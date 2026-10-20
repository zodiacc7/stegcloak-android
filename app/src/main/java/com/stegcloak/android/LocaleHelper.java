package com.stegcloak.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * Simple locale manager. Persists the chosen language in SharedPreferences
 * and updates the base Context configuration so every Activity restart
 * immediately renders the correct language.
 *
 * Supported languages: "en" (English, LTR) and "fa" (Persian/Farsi, RTL).
 */
public final class LocaleHelper {

    private static final String PREFS_NAME = "stegcloak_prefs";
    private static final String KEY_LANG = "app_lang";

    private LocaleHelper() {}

    public static String getPersistedLanguage(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, null);
        if (lang != null) return lang;
        // first launch: use device language if Persian, otherwise English
        String device = Locale.getDefault().getLanguage();
        if ("fa".equals(device) || "ar".equals(device) || "ur".equals(device)) {
            return "fa";
        }
        return "en";
    }

    public static void persist(Context ctx, String lang) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANG, lang)
                .apply();
    }

    public static Context updateBaseContextLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            // also set layout direction for RTL support
            config.setLayoutDirection(locale);
            return context.createConfigurationContext(config);
        } else {
            //noinspection deprecation
            config.locale = locale;
            //noinspection deprecation
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }

    public static boolean isRtl(String lang) {
        return "fa".equals(lang);
    }
}
