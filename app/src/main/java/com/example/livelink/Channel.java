package com.example.livelink;

final class Channel {
    final String name;
    final String url;
    final String group;
    final String logo;
    final String userAgent;
    final String referer;

    Channel(String name, String url, String group, String logo) {
        this(name, url, group, logo, "", "");
    }

    Channel(String name, String url, String group, String logo, String userAgent, String referer) {
        this.name = emptyToDefault(name, "未命名频道");
        this.url = url == null ? "" : url.trim();
        this.group = emptyToDefault(group, "未分组");
        this.logo = logo == null ? "" : logo.trim();
        this.userAgent = userAgent == null ? "" : userAgent.trim();
        this.referer = referer == null ? "" : referer.trim();
    }

    private static String emptyToDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
