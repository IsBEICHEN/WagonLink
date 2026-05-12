package com.example.livelink;

import java.net.URI;

final class Subscription {
    final String id;
    final String name;
    final String url;

    Subscription(String id, String name, String url) {
        this.id = id == null ? "" : id;
        this.url = url == null ? "" : url.trim();
        this.name = normalizedName(name, this.url);
    }

    static String normalizedName(String name, String url) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && !host.trim().isEmpty()) {
                return host.replace("www.", "");
            }
        } catch (Exception ignored) {
        }
        return "未命名订阅";
    }
}
