package ua.wristgpt.bridge

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

private const val CHATGPT = "com.openai.chatgpt"
private val BG = Color.rgb(8, 17, 13)
private val GREEN = Color.rgb(153, 239, 184)
private val WHITE = Color.rgb(243, 250, 246)
private val GREY = Color.rgb(176, 195, 181)
private val CARD = Color.rgb(24, 46, 34)

class MainActivity : android.app.Activity() {
    private lateinit var statusView: TextView
    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.navigationBarColor = BG
        buildUi()
    }
    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) refresh()
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 22): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(message: String, size: Float = 13f, color: Int = WHITE): TextView =
        TextView(this).apply {
            text = message
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(6), dp(3), dp(6))
        }
    private fun action(title: String, primary: Boolean = false, tap: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 12f
            setTextColor(if (primary) BG else GREEN)
            background = shape(if (primary) GREEN else CARD, 40)
            setOnClickListener { tap() }
        }

    private fun buildUi() {
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(BG)
            isFillViewport = true
            setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_SCROLL) {
                    smoothScrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(48)).toInt())
                    true
                } else false
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(22), dp(28), dp(24))
        }
        scroll.addView(column)
        setContentView(scroll)

        column.addView(label("✳", 32f, GREEN))
        column.addView(label("WristGPT", 20f).apply { setTypeface(null, Typeface.BOLD) })
        column.addView(label("⌨ Без API • Wear OS", 10f, GREY))

        statusView = label("", 11f, GREY)
        column.addView(statusView)
        column.addView(action("⚙ Доступність") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        column.addView(action("↗ Відкрити ChatGPT / Google") { openOriginal() })

        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(6))
        }
        column.addView(chatContainer, LinearLayout.LayoutParams(-1, -2))

        input = EditText(this).apply {
            hint = "Натисни, щоб надрукувати..."
            setHintTextColor(GREY)
            setTextColor(WHITE)
            textSize = 13f
            isSingleLine = false
            minLines = 2
            maxLines = 5
            background = shape(CARD, 15)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        column.addView(input, LinearLayout.LayoutParams(-1, -2))
        column.addView(action("➤ Надіслати", true) { send() })
        column.addView(action("Очистити історію") { Bridge.clear(); refresh() })
        refresh()
    }

    private fun refresh() {
        statusView.text = Bridge.status
        chatContainer.removeAllViews()
        if (Bridge.messages.isEmpty()) {
            chatContainer.addView(label("Питання вводиться системною клавіатурою годинника.", 11f, GREY))
        }
        Bridge.messages.takeLast(16).forEach { msg ->
            val cell = label(
                (if (msg.role == "user") "ТИ" else "CHATGPT") + "\n" + msg.text,
                12f, WHITE
            ).apply {
                gravity = Gravity.START
                background = shape(if (msg.role == "user") Color.rgb(25,65,44) else CARD, 15)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, dp(3), 0, dp(4))
            chatContainer.addView(cell, lp)
        }
    }
    private fun send() {
        val value = input.text.toString().trim()
        if (value.isEmpty() || Bridge.waiting) return
        if (!Bridge.enabled(this)) {
            Bridge.status = "Спочатку увімкни службу доступності WristGPT."
            refresh(); return
        }
        if (!Bridge.enqueue(value)) return
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        refresh()
        openOriginal()
    }
    private fun openOriginal() {
        val intent = packageManager.getLaunchIntentForPackage(CHATGPT)
        if (intent == null) {
            Bridge.status = "Потрібна оригінальна програма ChatGPT на годиннику."
            refresh()
            return
        }
        try { startActivity(intent) }
        catch (e: Exception) {
            Bridge.status = "Не вдалося відкрити ChatGPT: " + e.javaClass.simpleName
            refresh()
        }
    }
}

/** Local-only state. Never stores credentials and never calls an API. */
object Bridge {
    data class Message(val role: String, val text: String)
    val messages = mutableListOf<Message>()
    var status: String = "Встанови ChatGPT і дозволь Accessibility"
    var waiting = false
    var inserted = false
    var sent = false
    var prompt: String? = null
    var sentAt = 0L
    var latestAnswer = ""
    var lastChange = 0L
    var before = emptySet<String>()
    private var app: Context? = null

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        runCatching {
            val arr = JSONArray(app!!.getSharedPreferences("wristgpt", 0).getString("messages", "[]"))
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                messages.add(Message(obj.optString("role"), obj.optString("text")))
            }
        }
    }
    fun enabled(context: Context): Boolean {
        val found = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return found.contains(context.packageName + "/.ChatGptAccessibilityService", true) ||
            found.contains(context.packageName + "/" + ChatGptAccessibilityService::class.java.name, true)
    }
    fun enqueue(text: String): Boolean {
        if (waiting) return false
        prompt = text.take(2800)
        inserted = false
        sent = false
        waiting = true
        latestAnswer = ""
        lastChange = System.currentTimeMillis()
        before = emptySet()
        status = "Перемикаюся в ChatGPT..."
        messages.add(Message("user", prompt!!))
        persist()
        return true
    }
    fun markSent() {
        sent = true
        sentAt = System.currentTimeMillis()
        lastChange = sentAt
        status = "Очікую відповідь..."
    }
    fun finish(text: String) {
        waiting = false
        prompt = null
        status = if (text.isBlank()) "Не вдалося зчитати. Перевір ChatGPT." else "Відповідь отримано"
        if (text.isNotBlank()) {
            messages.add(Message("assistant", text.take(10000)))
            persist()
        }
    }
    fun fail(text: String) { waiting = false; prompt = null; status = text }
    fun clear() { messages.clear(); persist(); status = "Локальну історію очищено" }
    private fun persist() {
        val arr = JSONArray()
        for (m in messages.takeLast(40)) {
            arr.put(JSONObject().put("role", m.role).put("text", m.text))
        }
        app?.getSharedPreferences("wristgpt", 0)?.edit()
            ?.putString("messages", arr.toString())?.apply()
    }
}

/** Experimental accessibility bridge: operates only on the separately installed ChatGPT. */
class ChatGptAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false
    private var misses = 0
    private val sendWords = listOf("send", "submit", "arrow_up", "надісл", "отправ", "відправ")
    private val ignore = setOf(
        "ChatGPT", "Copy", "Send", "Share", "New chat", "Menu", "Search", "Voice",
        "Надіслати", "Копіювати", "Поділитися", "Новий чат", "Меню", "Пошук"
    )

    override fun onServiceConnected() {
        Bridge.init(this)
        Bridge.status = "Дозвіл є. Можна писати текст."
        schedule()
    }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != CHATGPT || !Bridge.waiting) return
        schedule()
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        handler.postDelayed({
            scheduled = false
            if (Bridge.waiting) {
                inspect()
                schedule()
            }
        }, 800)
    }
    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty() && out.size < 700) {
            val n = stack.removeLast()
            out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let(stack::addLast)
        }
        return out
    }
    private fun inspect() {
        val prompt = Bridge.prompt ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != CHATGPT) return
        val list = nodes(root)
        if (!Bridge.sent) {
            val field = list.lastOrNull {
                it.isVisibleToUser && it.isEnabled && it.isEditable
            }
            if (field == null) {
                if (++misses > 35) { Bridge.fail("Не знайшов поле: перевір Google-вхід."); returnHome() }
                return
            }
            if (!Bridge.inserted) {
                Bridge.before = list.mapNotNull { it.text?.toString()?.trim() }.toSet()
                val data = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, prompt)
                }
                if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, data)) {
                    Bridge.fail("ChatGPT не дозволяє автоматичне введення тексту.")
                    returnHome(); return
                }
                Bridge.inserted = true
                Bridge.status = "Текст вставлено. Пробую відправити..."
                return
            }
            // If a user pressed send manually, the input is no longer equal to our prompt.
            if (field.text?.toString()?.trim()?.isEmpty() == true) {
                Bridge.markSent()
                return
            }
            val send = list.lastOrNull { n ->
                val v = (n.contentDescription?.toString().orEmpty() + " " +
                    n.viewIdResourceName.orEmpty() + " " + n.text?.toString().orEmpty()).lowercase()
                n.isVisibleToUser && n.isEnabled && sendWords.any { v.contains(it) }
            }
            if (send != null && click(send)) {
                Bridge.markSent()
            } else {
                Bridge.status = "Текст введено. Натисни ↑ у ChatGPT вручну."
            }
            return
        }

        val all = list.filter { it.isVisibleToUser }.mapNotNull { it.text?.toString()?.trim() }
            .filter { it.isNotBlank() }
        val mark = prompt.take(35).lowercase()
        val index = if (mark.length > 2) all.indexOfLast { it.lowercase().contains(mark) } else -1
        val candidates = if (index >= 0) all.drop(index + 1)
                         else all.filterNot { it in Bridge.before }
        val answer = candidates.filter {
            it != prompt && it !in ignore && !it.equals("Stop generating", true)
        }.distinct().joinToString("\n").take(10000).trim()
        val now = System.currentTimeMillis()
        if (answer.isNotBlank() && answer != Bridge.latestAnswer) {
            Bridge.latestAnswer = answer
            Bridge.lastChange = now
            Bridge.status = "Читаю відповідь..."
        }
        if (Bridge.latestAnswer.length > 3 &&
            now - Bridge.sentAt > 5500L && now - Bridge.lastChange > 3000L) {
            Bridge.finish(Bridge.latestAnswer)
            returnHome()
        } else if (now - Bridge.sentAt > 65000L) {
            Bridge.finish(Bridge.latestAnswer)
            returnHome()
        }
    }
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(4) {
            if (n?.isClickable == true && n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                return true
            n = n?.parent
        }
        return false
    }
    private fun returnHome() {
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
    }
}
