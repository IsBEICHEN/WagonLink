package com.example.livelink;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ChannelCache {
    private static final String PREFS = "wagonlink_channel_cache";
    private static final String KEY_PREFIX = "subscription_";
    private static final int SCHEMA_VERSION = 2;

    private ChannelCache() {
    }

    static List<Channel> load(Context context, String subscriptionId, String subscriptionUrl) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return null;
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_PREFIX + subscriptionId, "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(raw);
            if (root.optInt("schemaVersion", 1) < SCHEMA_VERSION) {
                return null;
            }
            String cachedUrl = root.optString("url", "");
            if (!cachedUrl.equals(subscriptionUrl)) {
                return null;
            }
            JSONArray array = root.optJSONArray("channels");
            if (array == null) {
                return null;
            }
            List<Channel> channels = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String url = object.optString("url", "").trim();
                if (url.isEmpty()) {
                    continue;
                }
                channels.add(new Channel(
                        object.optString("name", ""),
                        url,
                        object.optString("group", ""),
                        object.optString("logo", ""),
                        object.optString("userAgent", ""),
                        object.optString("referer", "")));
            }
            return channels;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void save(Context context, String subscriptionId, String subscriptionUrl, List<Channel> channels) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return;
        }
        JSONArray array = new JSONArray();
        for (Channel channel : channels) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", channel.name);
                object.put("url", channel.url);
                object.put("group", channel.group);
                object.put("logo", channel.logo);
                object.put("userAgent", channel.userAgent);
                object.put("referer", channel.referer);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        JSONObject root = new JSONObject();
        try {
            root.put("url", subscriptionUrl);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("savedAt", System.currentTimeMillis());
            root.put("channels", array);
        } catch (Exception ignored) {
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PREFIX + subscriptionId, root.toString())
                .apply();
    }

    static void delete(Context context, String subscriptionId) {
        if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PREFIX + subscriptionId)
                .apply();
    }
}
