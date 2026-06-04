package com.example.livelink;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SubscriptionStore {
    private static final String PREFS = "livelink_subscriptions";
    private static final String KEY_SUBSCRIPTIONS = "subscriptions";

    private SubscriptionStore() {
    }

    static List<Subscription> load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_SUBSCRIPTIONS, "[]");
        List<Subscription> subscriptions = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String url = object.optString("url", "").trim();
                if (url.isEmpty()) {
                    continue;
                }
                subscriptions.add(new Subscription(
                        object.optString("id", ""),
                        object.optString("name", ""),
                        url));
            }
        } catch (Exception ignored) {
        }
        return subscriptions;
    }

    static Subscription upsert(Context context, List<Subscription> current, String name, String url) {
        return upsert(context, current, "", name, url);
    }

    static Subscription upsert(Context context, List<Subscription> current, String editingId, String name, String url) {
        String trimmedUrl = url == null ? "" : url.trim();
        String trimmedId = editingId == null ? "" : editingId.trim();
        Subscription next = null;
        List<Subscription> updated = new ArrayList<>();
        for (Subscription subscription : current) {
            boolean sameSubscription = !trimmedId.isEmpty()
                    ? subscription.id.equals(trimmedId)
                    : subscription.url.equals(trimmedUrl);
            if (sameSubscription) {
                next = new Subscription(subscription.id, name, trimmedUrl);
                updated.add(next);
            } else {
                updated.add(subscription);
            }
        }
        if (next == null) {
            next = new Subscription(String.valueOf(System.currentTimeMillis()), name, trimmedUrl);
            updated.add(next);
        }
        save(context, updated);
        return next;
    }

    static void delete(Context context, List<Subscription> current, String id) {
        List<Subscription> updated = new ArrayList<>();
        for (Subscription subscription : current) {
            if (!subscription.id.equals(id)) {
                updated.add(subscription);
            }
        }
        save(context, updated);
    }

    private static void save(Context context, List<Subscription> subscriptions) {
        JSONArray array = new JSONArray();
        for (Subscription subscription : subscriptions) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", subscription.id);
                object.put("name", subscription.name);
                object.put("url", subscription.url);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SUBSCRIPTIONS, array.toString())
                .apply();
    }
}
