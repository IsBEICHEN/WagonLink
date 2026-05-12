package com.example.livelink;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPreferences {
    static final String THEME_SYSTEM = "system";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";

    private static final String PREFS = "wagonlink_preferences";
    private static final String KEY_THEME = "theme";
    private static final String KEY_LAST_SUBSCRIPTION_ID = "last_subscription_id";
    private static final String KEY_GLASS_EFFECT = "glass_effect";

    private AppPreferences() {
    }

    static String loadTheme(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_SYSTEM);
    }

    static void saveTheme(Context context, String theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, theme)
                .apply();
    }

    static String loadLastSubscriptionId(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_SUBSCRIPTION_ID, "");
    }

    static void saveLastSubscriptionId(Context context, String subscriptionId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SUBSCRIPTION_ID, subscriptionId == null ? "" : subscriptionId)
                .apply();
    }

    static boolean loadGlassEffect(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_GLASS_EFFECT, false);
    }

    static void saveGlassEffect(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_GLASS_EFFECT, enabled)
                .apply();
    }
}
