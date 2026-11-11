package com.openai.chatgpt.watch;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * EXPERIMENTAL: Replaces the visible main screen in the original ChatGPT process,
 * reusing its existing login/session and underlying UI. Does not make API calls.
 * Underlying Valdi widgets might not expose editable accessibility nodes.
 */
public final class WatchUi {
    private static final int BLACK = Color.rgb(7, 15, 12);
    private static final int PANEL = Color.rgb(25, 38, 33);
    private static final int GREEN = Color.rgb(126, 223, 169);
    private static final int WHITE = Color.rgb(238, 246, 240);
    private static final int MUTED = Color.rgb(155, 174, 163);
    private static WeakReference<Activity> current = new WeakReference<>(null);
    private static WeakReference<FrameLayout> liveOverlay = new WeakReference<>(null);
    private static FrameLayout overlay;
    private static FrameLayout openButtonHost;
    private static FrameLayout parent;
    private static ScrollView threadScroll;
    private static LinearLayout thread;
    private static EditText composer;
    private static TextView status;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Set<String> beforeSend = new HashSet<>();
    private static boolean waiting = false;
    private static long lastAnswerChange;
    private static long sentAt;
    private static String candidate = "";
    private static String lastUserMessage = "";
    private static boolean active = true;
    private static int generation = 0;

    private WatchUi() {}

    public static void attach(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            Activity old = current.get();
            FrameLayout oldOverlay = liveOverlay.get();
            if (old == activity && oldOverlay != null && oldOverlay.getParent() != null) return;
            try {
                current = new WeakReference<>(activity);
                View root = activity.getWindow().getDecorView();
                if (!(root instanceof FrameLayout)) return;
                parent = (FrameLayout) root;
                build(activity);
                activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            } catch (Throwable failure) {
                android.util.Log.e("WatchUi", "Round UI could not attach", failure);
            }
        });
    }

    private static int dp(Context c, float value) {
        return (int)(value * c.getResources().getDisplayMetrics().density + 0.5f);
    }
    private static GradientDrawable bg(int color, int radius, Context c) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(c, radius));
        return shape;
    }
    private static TextView label(Context c, String text, int size, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false);
        return v;
    }
    private static TextView control(Context c, String label, boolean primary, Runnable press) {
        TextView b = label(c, label, 15, primary ? BLACK : WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bg(primary ? GREEN : PANEL, 45, c));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v -> press.run());
        return b;
    }
    private static void build(final Activity a) {
        // Unlike scaling a rectangular app, this creates an edge-to-edge native layout.
        overlay = new FrameLayout(a);
        overlay.setBackgroundColor(BLACK);
        overlay.setClickable(true);

        LinearLayout screen = new LinearLayout(a);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(a, 5), dp(a, 16), dp(a, 5), dp(a, 8));
        overlay.addView(screen, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(a, 45), 0, dp(a, 45), dp(a, 0));
        screen.addView(header, new LinearLayout.LayoutParams(-1, dp(a, 38)));

        TextView old = control(a, "‹", false, WatchUi::openOriginal);
        header.addView(old, new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30)));
        TextView title = label(a, "ChatGPT", 18, WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView reset = control(a, "+", false, () -> {
            waiting = false;
            thread.removeAllViews();
            appendBubble("Новий чат. Для нової розмови в акаунті натисни + в оригінальному екрані.", false);
            refreshStatus("Для створення нового чату відкрий оригінал.");
        });
        header.addView(reset, new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30)));

        status = label(a, "Круглий інтерфейс · оригінальний ChatGPT", 10, MUTED);
        status.setGravity(Gravity.CENTER);
        screen.addView(status, new LinearLayout.LayoutParams(-1, dp(a, 15)));

        threadScroll = new ScrollView(a);
        threadScroll.setFillViewport(true);
        threadScroll.setVerticalScrollBarEnabled(false);
        threadScroll.setClipToPadding(false);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(-1, 0, 1);
        sc.leftMargin = dp(a, 6);
        sc.rightMargin = dp(a, 6);
        screen.addView(threadScroll, sc);
        thread = new LinearLayout(a);
        thread.setPadding(dp(a, 13), dp(a, 5), dp(a, 13), dp(a, 4));
        thread.setOrientation(LinearLayout.VERTICAL);
        threadScroll.addView(thread);

        appendBubble("Введи запит клавіатурою. Для входу через Google натисни ‹, увійди в оригінальному ChatGPT і повернись кнопкою ◉.", false);

        LinearLayout sendRow = new LinearLayout(a);
        sendRow.setGravity(Gravity.CENTER_VERTICAL);
        sendRow.setPadding(dp(a, 47), dp(a, 0), dp(a, 47), 0);
        screen.addView(sendRow, new LinearLayout.LayoutParams(-1, dp(a, 50)));

        composer = new EditText(a);
        composer.setSingleLine(false);
        composer.setMinLines(1);
        composer.setMaxLines(4);
        composer.setTextSize(14);
        composer.setTextColor(WHITE);
        composer.setHintTextColor(MUTED);
        composer.setHint("Повідомлення...");
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setPadding(dp(a, 8), dp(a, 6), dp(a, 6), dp(a, 6));
        composer.setBackground(bg(PANEL, 23, a));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.rightMargin = dp(a, 5);
        sendRow.addView(composer, cp);

        TextView submit = control(a, "↑", true, WatchUi::sendMessage);
        sendRow.addView(submit, new LinearLayout.LayoutParams(dp(a, 35), dp(a, 35)));

        parent.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        liveOverlay = new WeakReference<>(overlay);

        openButtonHost = new FrameLayout(a);
        TextView show = control(a, "◉", true, () -> {
            active = true;
            overlay.setVisibility(View.VISIBLE);
            openButtonHost.setVisibility(View.GONE);
            refreshStatus("ChatGPT · круглий режим");
        });
        FrameLayout.LayoutParams mini = new FrameLayout.LayoutParams(dp(a, 48), dp(a, 48), Gravity.CENTER);
        openButtonHost.addView(show, mini);
        FrameLayout.LayoutParams launcher = new FrameLayout.LayoutParams(dp(a, 56), dp(a, 56), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        launcher.topMargin = dp(a, 15);
        parent.addView(openButtonHost, launcher);
        openButtonHost.setVisibility(View.GONE);
        active = true;
    }
    private static void openOriginal() {
        active = false;
        waiting = false;
        if (overlay != null) overlay.setVisibility(View.GONE);
        if (openButtonHost != null) openButtonHost.setVisibility(View.VISIBLE);
    }
    private static void appendBubble(String message, boolean isUser) {
        Activity a = current.get();
        if (a == null || thread == null || message == null || message.trim().isEmpty()) return;
        TextView bubble = label(a, message, 13, WHITE);
        bubble.setPadding(dp(a, 13), dp(a, 11), dp(a, 13), dp(a, 11));
        bubble.setBackground(bg(isUser ? Color.rgb(24, 69, 48) : PANEL, 17, a));
        bubble.setGravity(Gravity.START);
        bubble.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = isUser ? Gravity.RIGHT : Gravity.LEFT;
        lp.leftMargin = dp(a, isUser ? 28 : 0);
        lp.rightMargin = dp(a, isUser ? 0 : 23);
        lp.bottomMargin = dp(a, 8);
        thread.addView(bubble, lp);
        threadScroll.post(() -> threadScroll.fullScroll(View.FOCUS_DOWN));
    }
    private static void refreshStatus(String message) {
        if (status != null) status.setText(message);
    }

    private static void sendMessage() {
        Activity a = current.get();
        if (a == null || composer == null) return;
        String text = composer.getText().toString().trim();
        if (text.isEmpty() || waiting) return;
        beforeSend.clear();
        collectStrings(beforeSend);
        lastUserMessage = text;
        candidate = "";
        appendBubble(text, true);
        composer.setText("");
        ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(composer.getWindowToken(), 0);

        if (!sendToUnderlying(text)) {
            appendBubble("Не вдалося знайти поле введення в оригінальному ChatGPT. Увійди в акаунт через ‹ та спробуй ще раз. Якщо поле залишиться недоступним, ця версія Valdi несумісна з таким керуванням.", false);
            refreshStatus("Тільки при доступному полі введення ChatGPT");
            return;
        }
        waiting = true;
        sentAt = android.os.SystemClock.uptimeMillis();
        lastAnswerChange = sentAt;
        refreshStatus("Очікування відповіді…");
        int ticket = ++generation;
        handler.postDelayed(() -> pollAnswer(ticket), 1200);
    }
    private static View findOriginalInput() {
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == overlay || child == openButtonHost) continue;
            View found = searchInputView(child);
            if (found != null) return found;
        }
        return null;
    }
    private static View searchInputView(View root) {
        if (root instanceof EditText && root.isEnabled()) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = searchInputView(group.getChildAt(i));
                if (candidate != null) return candidate;
            }
        }
        return null;
    }
    private static boolean sendToUnderlying(String text) {
        try {
            View v = findOriginalInput();
            if (v instanceof EditText) {
                ((EditText)v).setText(text);
                if (clickSendView()) return true;
            }
            // Valdi renders some UI as virtual accessibility nodes. No Accessibility
            // Service permission is needed when operating on our OWN Activity.
            for (AccessibilityNodeInfo n : snapshotNodes()) {
                if (n != null && n.isEditable() && n.isEnabled()) {
                    Bundle args = new Bundle();
                    args.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                        if (clickSendNode()) return true;
                    }
                }
            }
        } catch (Throwable e) {
            android.util.Log.w("WatchUi", "Cannot send via Valdi view hierarchy", e);
        }
        return false;
    }
    private static boolean clickSendView() {
        if (parent == null) return false;
        ArrayList<View> stack = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (v != overlay && v != openButtonHost) stack.add(v);
        }
        while (!stack.isEmpty()) {
            View v = stack.remove(stack.size() - 1);
            String desc = v.getContentDescription() == null ? "" : v.getContentDescription().toString();
            String text = v instanceof TextView ? ((TextView)v).getText().toString() : "";
            if (v.isEnabled() && v.isClickable() && isSend(desc + " " + text))
                return v.performClick();
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup)v;
                for(int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
            }
        }
        return false;
    }
    private static boolean clickSendNode() {
        for (AccessibilityNodeInfo n : snapshotNodes()) {
            String label = "" + n.getContentDescription() + " " + n.getText() + " " + n.getViewIdResourceName();
            if (n.isEnabled() && isSend(label) &&
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }
    private static boolean isSend(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("send") || t.contains("submit") || t.contains("відправ") ||
            t.contains("надісл") || t.contains("arrow_up") || t.contains("send_message");
    }
    private static List<AccessibilityNodeInfo> snapshotNodes() {
        ArrayList<AccessibilityNodeInfo> out = new ArrayList<>();
        if (parent == null) return out;
        ArrayList<View> stack = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (v != overlay && v != openButtonHost) stack.add(v);
        }
        while (!stack.isEmpty() && out.size() < 400) {
            View v = stack.remove(stack.size() - 1);
            try {
                AccessibilityNodeProvider p = v.getAccessibilityNodeProvider();
                AccessibilityNodeInfo node = p == null ? v.createAccessibilityNodeInfo() : p.createAccessibilityNodeInfo(-1);
                if (node != null) visitNode(node, out, 0);
            } catch (Throwable ignored) {}
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for(int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
            }
        }
        return out;
    }
    private static void visitNode(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int depth) {
        if (node == null || depth > 10 || out.size() >= 400) return;
        out.add(node);
        for (int i = 0; i < node.getChildCount() && out.size() < 400; i++) {
            try { visitNode(node.getChild(i), out, depth + 1); } catch (Throwable ignored) {}
        }
    }
    private static void collectStrings(Set<String> dest) {
        for (AccessibilityNodeInfo n : snapshotNodes()) {
            try {
                CharSequence s = n.getText();
                if (s != null && s.length() > 3 && s.length() < 8000) dest.add(s.toString().trim());
            } catch (Throwable ignored) {}
        }
    }
    private static void pollAnswer(int ticket) {
        if (!waiting || ticket != generation) return;
        try {
            Set<String> now = new HashSet<>();
            collectStrings(now);
            String best = "";
            for (String t : now) {
                if (!beforeSend.contains(t) && !t.equals(lastUserMessage) &&
                   !t.equalsIgnoreCase("ChatGPT") && t.length() > best.length())
                    best = t;
            }
            long uptime = android.os.SystemClock.uptimeMillis();
            if (best.length() > 3 && !best.equals(candidate)) {
                candidate = best;
                lastAnswerChange = uptime;
            }
            if (candidate.length() > 3 && uptime - sentAt > 4000 &&
                uptime - lastAnswerChange > 2800) {
                waiting = false;
                appendBubble(candidate, false);
                refreshStatus("ChatGPT");
                return;
            }
            if (uptime - sentAt > 45000) {
                waiting = false;
                if (candidate.length() > 3) appendBubble(candidate, false);
                else appendBubble("Не вдалося прочитати відповідь. Натисни ‹, щоб переглянути її в оригінальному чаті.", false);
                refreshStatus("Відповідь не розпізнана");
                return;
            }
        } catch (Throwable e) {
            android.util.Log.w("WatchUi", "Could not read response", e);
        }
        handler.postDelayed(() -> pollAnswer(ticket), 1200);
    }
}