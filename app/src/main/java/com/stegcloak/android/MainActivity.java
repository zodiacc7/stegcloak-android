package com.stegcloak.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * StegCloak for Android — hides encrypted secrets inside ordinary-looking text
 * using zero-width characters. 100% compatible with the original StegCloak
 * (CLI / web app), so texts can be exchanged across platforms.
 *
 * Bilingual (English / Persian) with persistent locale and fixed eye-toggle.
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF0C0F16;
    private static final int CARD = 0xFF151B26;
    private static final int FIELD = 0xFF0F141E;
    private static final int STROKE = 0xFF28324A;
    private static final int ACCENT = 0xFF6C8CFF;
    private static final int ACCENT_DARK = 0xFF28315E;
    private static final int TEXT = 0xFFE7EBF3;
    private static final int SUB = 0xFF8E99B0;
    private static final int GOOD = 0xFF3FB68B;
    private static final int BAD = 0xFFFF7A7A;

    private String[] sampleCovers;
    private int coverIndex = 0;

    private LinearLayout hidePanel, revealPanel;
    private TextView tabHide, tabReveal;
    private EditText secretInput, coverInput, hidePassword;
    private CheckBox encryptBox, hmacBox;
    private TextView hideError;
    private LinearLayout hideResultCard;
    private TextView hideResultText, hideResultInfo;

    private EditText concealedInput, revealPassword;
    private TextView revealError;
    private LinearLayout revealResultCard;
    private TextView revealResultText, revealResultTitle;

    // Language toggle
    private String currentLang;

    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = LocaleHelper.getPersistedLanguage(newBase);
        super.attachBaseContext(LocaleHelper.updateBaseContextLocale(newBase, lang));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLang = LocaleHelper.getPersistedLanguage(this);
        sampleCovers = getResources().getStringArray(R.array.sample_covers);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        buildUi();
        handleSharedIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedIntent(intent);
    }

    private void handleSharedIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.hasExtra(Intent.EXTRA_TEXT)) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.trim().isEmpty() && concealedInput != null) {
                concealedInput.setText(shared);
                // switch to reveal tab if text contains hidden data, otherwise stay but show it
                selectTab(false);
                if (!StegCloak.containsHiddenData(shared)) {
                    // still show it; user can edit
                    revealError.setVisibility(View.GONE);
                }
                toast(getString(R.string.toast_copied_short));
            }
        }
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(30));
        scroll.addView(root);

        // ---- language switcher row (top)
        LinearLayout langRow = new LinearLayout(this);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        langRow.setGravity(Gravity.END);
        Button langBtn = ghostButton(getString(R.string.lang_switch_label));
        langBtn.setTextSize(12.5f);
        langBtn.setPadding(dp(10), 0, dp(10), 0);
        langBtn.setMinHeight(dp(32));
        langBtn.setMinimumHeight(dp(32));
        langBtn.setOnClickListener(v -> toggleLanguage());
        langRow.addView(langBtn, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(-1, -2);
        langLp.bottomMargin = dp(6);
        root.addView(langRow, langLp);

        // ---- header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(header);

        TextView logo = new TextView(this);
        logo.setText("🕶️");
        logo.setTextSize(40);
        header.addView(logo);

        TextView title = new TextView(this);
        title.setText(getString(R.string.header_title));
        title.setTextColor(TEXT);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.header_subtitle));
        subtitle.setTextColor(SUB);
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(2), 0, dp(12));
        subtitle.setLineSpacing(0, 1.15f);
        header.addView(subtitle);

        // ---- tabs
        FrameLayout tabsWrap = new FrameLayout(this);
        GradientDrawable tabsBg = new GradientDrawable();
        tabsBg.setColor(CARD);
        tabsBg.setCornerRadius(dp(14));
        tabsBg.setStroke(dp(1), STROKE);
        tabsWrap.setBackground(tabsBg);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabsWrap.addView(tabs);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(-1, -2);
        tabsLp.bottomMargin = dp(14);
        root.addView(tabsWrap, tabsLp);

        tabHide = tab(getString(R.string.tab_hide));
        tabReveal = tab(getString(R.string.tab_reveal));
        tabs.addView(tabHide, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(tabReveal, new LinearLayout.LayoutParams(0, -2, 1));
        tabHide.setOnClickListener(v -> selectTab(true));
        tabReveal.setOnClickListener(v -> selectTab(false));

        // ---- panels
        hidePanel = buildHidePanel();
        revealPanel = buildRevealPanel();
        root.addView(hidePanel);
        root.addView(revealPanel);

        // ---- footer
        TextView footer = new TextView(this);
        footer.setText(getString(R.string.footer_text));
        footer.setTextColor(SUB);
        footer.setTextSize(11.5f);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        footer.setLineSpacing(0, 1.25f);
        root.addView(footer);

        // ---- package info (small)
        TextView pkgInfo = new TextView(this);
        pkgInfo.setText("com.stegcloak.android • v1.0 • offline • zero permissions");
        pkgInfo.setTextColor(0xFF6A7488);
        pkgInfo.setTextSize(10.5f);
        pkgInfo.setGravity(Gravity.CENTER);
        pkgInfo.setPadding(0, dp(10), 0, 0);
        root.addView(pkgInfo);

        selectTab(true);
        setContentView(scroll);
    }

    private void toggleLanguage() {
        String newLang = "fa".equals(currentLang) ? "en" : "fa";
        LocaleHelper.persist(this, newLang);
        // Recreate activity with new locale
        recreate();
    }

    private TextView tab(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14.5f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(11), 0, dp(11));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void selectTab(boolean hide) {
        hidePanel.setVisibility(hide ? View.VISIBLE : View.GONE);
        revealPanel.setVisibility(hide ? View.GONE : View.VISIBLE);
        styleTab(tabHide, hide);
        styleTab(tabReveal, !hide);
    }

    private void styleTab(TextView t, boolean active) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(11));
        if (active) {
            g.setColor(ACCENT_DARK);
            t.setTextColor(Color.WHITE);
        } else {
            g.setColor(Color.TRANSPARENT);
            t.setTextColor(SUB);
        }
        t.setBackground(g);
    }

    private LinearLayout buildHidePanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c1 = card();
        c1.addView(label(getString(R.string.label_secret)));
        secretInput = input(getString(R.string.hint_secret), 3);
        c1.addView(secretInput);

        c1.addView(label(getString(R.string.label_cover)));
        coverInput = input(getString(R.string.hint_cover), 3);
        if (sampleCovers != null && sampleCovers.length > 0) {
            coverInput.setText(sampleCovers[0]);
        }
        c1.addView(coverInput);
        Button dice = ghostButton(getString(R.string.btn_random_cover));
        dice.setOnClickListener(v -> {
            if (sampleCovers == null || sampleCovers.length == 0) return;
            coverIndex = (coverIndex + 1) % sampleCovers.length;
            coverInput.setText(sampleCovers[coverIndex]);
            coverInput.setSelection(coverInput.getText().length());
        });
        LinearLayout diceWrap = new LinearLayout(this);
        diceWrap.setGravity(Gravity.START);
        diceWrap.addView(dice);
        LinearLayout.LayoutParams dl = lp();
        dl.topMargin = dp(6);
        c1.addView(diceWrap, dl);

        // Clear button for hide panel
        Button clearHide = ghostButton("✕");
        clearHide.setTextSize(13f);
        clearHide.setOnClickListener(v -> {
            secretInput.setText("");
            hideError.setVisibility(View.GONE);
            hideResultCard.setVisibility(View.GONE);
        });
        LinearLayout clearWrap = new LinearLayout(this);
        clearWrap.setGravity(Gravity.END);
        clearWrap.addView(clearHide, new LinearLayout.LayoutParams(-2, -2));
        c1.addView(clearWrap);

        LinearLayout c2 = card();
        c2.addView(label(getString(R.string.label_security)));
        // password row with fixed eye toggle
        hidePassword = passwordInput(getString(R.string.hint_password_hide));
        View pwRow = passwordRow(hidePassword);
        c2.addView(pwRow);

        encryptBox = check(getString(R.string.check_encrypt), true);
        encryptBox.setOnCheckedChangeListener((b, on) -> {
            hmacBox.setEnabled(on);
            // visual feedback for disabled
            hmacBox.setAlpha(on ? 1.0f : 0.45f);
        });
        c2.addView(encryptBox);
        hmacBox = check(getString(R.string.check_hmac), true);
        c2.addView(hmacBox);

        TextView note = new TextView(this);
        note.setText(getString(R.string.note_empty_password));
        note.setTextColor(SUB);
        note.setTextSize(11.5f);
        note.setPadding(dp(2), dp(4), dp(2), 0);
        note.setLineSpacing(0, 1.2f);
        c2.addView(note);

        hideError = errorText();
        p.addView(c1);
        p.addView(c2);
        p.addView(hideError);

        Button go = primaryButton(getString(R.string.btn_hide));
        go.setOnClickListener(v -> doHide());
        LinearLayout.LayoutParams gl = lp();
        gl.topMargin = dp(2);
        p.addView(go, gl);

        hideResultCard = card();
        hideResultCard.setVisibility(View.GONE);
        TextView rt = label(getString(R.string.label_result_hidden));
        rt.setTextColor(GOOD);
        hideResultCard.addView(rt);
        hideResultText = resultView();
        hideResultCard.addView(hideResultText);
        hideResultCard.addView(buttonRow(
                ghostButton(getString(R.string.btn_copy), () -> copyFrom(hideResultText)),
                ghostButton(getString(R.string.btn_share), () -> share(hideResultText))));
        hideResultInfo = infoText();
        hideResultCard.addView(hideResultInfo);
        p.addView(hideResultCard);
        return p;
    }

    private LinearLayout buildRevealPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);

        LinearLayout c1 = card();
        c1.addView(label(getString(R.string.label_concealed)));
        concealedInput = input(getString(R.string.hint_concealed), 4);
        c1.addView(concealedInput);
        Button paste = ghostButton(getString(R.string.btn_paste_clipboard));
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                if (t != null) {
                    concealedInput.setText(t);
                    concealedInput.setSelection(concealedInput.getText().length());
                    // auto hint: if hidden data detected, optionally auto-clear error
                    revealError.setVisibility(View.GONE);
                }
            } else {
                toast(getString(R.string.error_concealed_empty));
            }
        });
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.START);
        wrap.addView(paste);
        // clear concealed
        Button clearConcealed = ghostButton("✕");
        clearConcealed.setOnClickListener(v -> {
            concealedInput.setText("");
            revealError.setVisibility(View.GONE);
            revealResultCard.setVisibility(View.GONE);
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = dp(8);
        wrap.addView(clearConcealed, cl);

        LinearLayout.LayoutParams pl = lp();
        pl.topMargin = dp(6);
        c1.addView(wrap, pl);

        LinearLayout c2 = card();
        c2.addView(label(getString(R.string.label_password)));
        revealPassword = passwordInput(getString(R.string.hint_password_reveal));
        c2.addView(passwordRow(revealPassword));

        revealError = errorText();
        p.addView(c1);
        p.addView(c2);
        p.addView(revealError);

        Button go = primaryButton(getString(R.string.btn_reveal));
        go.setOnClickListener(v -> doReveal());
        p.addView(go, lp());

        revealResultCard = card();
        revealResultCard.setVisibility(View.GONE);
        revealResultTitle = label(getString(R.string.label_revealed_success));
        revealResultTitle.setTextColor(GOOD);
        revealResultCard.addView(revealResultTitle);
        revealResultText = resultView();
        revealResultCard.addView(revealResultText);
        revealResultCard.addView(buttonRow(
                ghostButton(getString(R.string.btn_copy), () -> copyFrom(revealResultText)),
                ghostButton(getString(R.string.btn_share), () -> share(revealResultText))));
        p.addView(revealResultCard);
        return p;
    }

    // ------------------------------------------------------------------ actions

    private void doHide() {
        hideError.setVisibility(View.GONE);
        String secret = secretInput.getText().toString();
        String cover = coverInput.getText().toString();
        String pw = hidePassword.getText().toString();
        if (secret.trim().isEmpty()) {
            showError(hideError, getString(R.string.error_secret_empty));
            return;
        }
        if (cover.trim().isEmpty()) {
            showError(hideError, getString(R.string.error_cover_empty));
            return;
        }
        boolean enc = encryptBox.isChecked();
        try {
            String out = StegCloak.hide(secret, pw, cover, enc, hmacBox.isChecked() && enc);
            hideResultText.setText(out);
            int zwc = StegCloak.zeroWidthCount(out);
            String method;
            if (enc) {
                method = hmacBox.isChecked()
                        ? getString(R.string.method_enc_hmac)
                        : getString(R.string.method_enc);
            } else {
                method = getString(R.string.method_plain);
            }
            hideResultInfo.setText(getString(R.string.info_embedded, zwc, method));
            hideResultCard.setVisibility(View.VISIBLE);
            copy(out);
            toast(getString(R.string.toast_copied_clipboard));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("two words")) {
                showError(hideError, getString(R.string.error_cover_two_words));
            } else {
                String msg = e.getMessage() != null ? e.getMessage() : "unknown";
                showError(hideError, getString(R.string.error_generic, msg));
            }
        }
    }

    private void doReveal() {
        revealError.setVisibility(View.GONE);
        revealResultCard.setVisibility(View.GONE);
        String text = concealedInput.getText().toString();
        String pw = revealPassword.getText().toString();
        if (text.trim().isEmpty()) {
            showError(revealError, getString(R.string.error_concealed_empty));
            return;
        }
        if (!StegCloak.containsHiddenData(text)) {
            showError(revealError, getString(R.string.error_no_hidden));
            return;
        }
        if (StegCloak.isEncrypted(text) && pw.isEmpty()) {
            showError(revealError, getString(R.string.error_password_required));
            return;
        }
        try {
            String msg = StegCloak.reveal(text, pw);
            revealResultText.setText(msg);
            revealResultTitle.setText(getString(R.string.label_revealed_success));
            revealResultTitle.setTextColor(GOOD);
            revealResultCard.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("HMAC")) {
                showError(revealError, getString(R.string.error_wrong_password_hmac));
            } else if (m.contains("not detected")) {
                showError(revealError, getString(R.string.error_not_detected));
            } else {
                showError(revealError, getString(R.string.error_reveal_generic));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("stegcloak", s));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void showError(TextView target, String msg) {
        target.setText(msg);
        target.setVisibility(View.VISIBLE);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = dp(12);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), STROKE);
        c.setBackground(g);
        LinearLayout.LayoutParams l = lp();
        l.topMargin = 0;
        l.bottomMargin = dp(14);
        c.setLayoutParams(l);
        return c;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(SUB);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(2), 0, dp(2), dp(7));
        return t;
    }

    private EditText input(String hint, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF5A6478);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        e.setBackground(fieldBg());
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.bottomMargin = dp(4);
        e.setLayoutParams(l);
        return e;
    }

    private EditText passwordInput(String hint) {
        EditText e = input(hint, 1);
        e.setMinLines(1);
        // Base state is hidden password; disable suggestions
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setTypeface(Typeface.DEFAULT);
        return e;
    }

    /**
     * Fixed eye-toggle: uses View tag Boolean to track visibility state instead of
     * fragile InputType bitmask checks. Previously the mask check always returned
     * hidden, so the button never switched to visible.
     */
    private View passwordRow(EditText field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(field, new LinearLayout.LayoutParams(0, -2, 1));

        Button eye = ghostButton("👁");
        eye.setContentDescription(getString(R.string.cd_show_password));
        eye.setTag(Boolean.FALSE); // FALSE = hidden, TRUE = visible
        eye.setMinWidth(dp(48));
        eye.setMinimumWidth(dp(48));

        eye.setOnClickListener(v -> {
            boolean isVisible = Boolean.TRUE.equals(eye.getTag());
            // Preserve cursor position
            int selStart = field.getSelectionStart();
            int selEnd = field.getSelectionEnd();

            if (isVisible) {
                // currently visible -> hide it
                field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eye.setText("👁");
                eye.setContentDescription(getString(R.string.cd_show_password));
                eye.setTag(Boolean.FALSE);
            } else {
                // currently hidden -> show it
                field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                eye.setText("🙈");
                eye.setContentDescription(getString(R.string.cd_hide_password));
                eye.setTag(Boolean.TRUE);
            }
            // InputType change resets typeface on many devices; restore it
            field.setTypeface(Typeface.DEFAULT);
            // restore cursor; if invalid, move to end
            try {
                if (selStart >= 0 && selEnd >= 0 && selStart <= field.getText().length() && selEnd <= field.getText().length()) {
                    field.setSelection(selStart, selEnd);
                } else {
                    field.setSelection(field.getText().length());
                }
            } catch (Exception ignored) {
                field.setSelection(field.getText().length());
            }
        });

        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-2, -2);
        el.leftMargin = dp(8);
        el.gravity = Gravity.CENTER_VERTICAL;
        row.addView(eye, el);
        return row;
    }

    private CheckBox check(String s, boolean on) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(TEXT);
        c.setTextSize(14);
        c.setChecked(on);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        c.setPadding(0, dp(6), 0, 0);
        return c;
    }

    private Button primaryButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(ACCENT);
        g.setCornerRadius(dp(13));
        b.setBackground(g);
        b.setPadding(0, dp(4), 0, dp(4));
        return b;
    }

    private Button ghostButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(ACCENT);
        b.setTextSize(13.5f);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x14000000);
        g.setCornerRadius(dp(11));
        g.setStroke(dp(1), STROKE);
        b.setBackground(g);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(38));
        b.setMinimumHeight(dp(38));
        return b;
    }

    private interface GhostAction {
        void run();
    }

    private Button ghostButton(String s, GhostAction a) {
        Button b = ghostButton(s);
        b.setOnClickListener(v -> a.run());
        return b;
    }

    private LinearLayout buttonRow(Button a, Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(-2, -2);
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(-2, -2);
        lb.leftMargin = dp(8);
        la.topMargin = lb.topMargin = dp(10);
        row.addView(a, la);
        row.addView(b, lb);
        return row;
    }

    private void copyFrom(TextView tv) {
        copy(tv.getText().toString());
        toast(getString(R.string.toast_copied_short));
    }

    private void share(TextView tv) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, tv.getText().toString());
        startActivity(Intent.createChooser(i, getString(R.string.share_chooser)));
    }

    private GradientDrawable fieldBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(FIELD);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), STROKE);
        return g;
    }

    private TextView resultView() {
        TextView t = new TextView(this);
        t.setTextColor(TEXT);
        t.setTextSize(15);
        t.setTextIsSelectable(true);
        t.setLineSpacing(0, 1.3f);
        t.setBackground(fieldBg());
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        return t;
    }

    private TextView infoText() {
        TextView t = new TextView(this);
        t.setTextColor(SUB);
        t.setTextSize(11.5f);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(dp(2), dp(10), dp(2), 0);
        return t;
    }

    private TextView errorText() {
        TextView t = new TextView(this);
        t.setTextColor(BAD);
        t.setTextSize(13.5f);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(dp(4), 0, dp(4), dp(10));
        t.setVisibility(View.GONE);
        return t;
    }
}
