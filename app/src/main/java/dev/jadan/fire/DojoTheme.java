package dev.jadan.fire;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Scoped arcade/dojo visual language. Shared focus semantics, independent
 * palette. Does not recolor the parent Fire Console or other applications.
 */
final class DojoTheme {
    static final int BACKGROUND = 0xFF111821;
    static final int PANEL = 0xFF1D2934;
    static final int INK = 0xFFF4EFE2;
    static final int MUTED = 0xFFB8C5C8;
    static final int GOLD = 0xFFF3C873;
    static final int RED = 0xFFE77969;
    private DojoTheme() {}

    static void page(Activity a, LinearLayout root) {
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(TvUi.dp(a, 30), TvUi.dp(a, 18),
            TvUi.dp(a, 30), TvUi.dp(a, 26));
        root.setBackgroundColor(BACKGROUND);
    }

    static TextView label(Activity a, String value, int size, int color) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, TvUi.dp(a, 7), 0, TvUi.dp(a, 7));
        return t;
    }

    static TextView headline(Activity a, String text) {
        TextView t = label(a, text, 27, INK);
        t.setTypeface(null, 1);
        return t;
    }

    static LinearLayout card(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(TvUi.dp(a, 18), TvUi.dp(a, 12),
            TvUi.dp(a, 18), TvUi.dp(a, 15));
        box.setBackground(shape(a, PANEL, 0xFF394754, 1));
        return box;
    }

    static GradientDrawable shape(Activity a, int fill, int outline, int width) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(TvUi.dp(a, 12));
        d.setStroke(TvUi.dp(a, width), outline);
        return d;
    }

    static Button button(Activity a, String title, Runnable run) {
        Button b = new Button(a);
        b.setText(title);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(INK);
        b.setMinHeight(TvUi.dp(a, 56));
        b.setBackground(shape(a, PANEL, 0xFF526170, 1));
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(shape(a, focused ? 0xFF354350 : PANEL,
                focused ? GOLD : 0xFF526170, focused ? 3 : 1));
            b.setTextColor(focused ? GOLD : INK);
            b.setElevation(TvUi.dp(a, focused ? 6 : 0));
        });
        b.setOnClickListener(v -> run.run());
        return b;
    }

    static Button action(Activity a, LinearLayout box, String title, Runnable run) {
        Button b = button(a, title, run);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = TvUi.dp(a, 8);
        box.addView(b, lp);
        return b;
    }

    static void addCard(Activity a, LinearLayout root, LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = TvUi.dp(a, 12);
        root.addView(card, lp);
    }

    static void input(Activity a, EditText edit) {
        edit.setTextColor(INK);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(17);
        edit.setPadding(TvUi.dp(a, 14), TvUi.dp(a, 12),
            TvUi.dp(a, 14), TvUi.dp(a, 12));
        edit.setBackground(shape(a, PANEL, 0xFF526170, 2));
        edit.setOnFocusChangeListener((v, focused) ->
            edit.setBackground(shape(a, PANEL, focused ? GOLD : 0xFF526170,
                focused ? 3 : 2)));
    }
}
