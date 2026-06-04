package com.example.livelink;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlaylistParser {
    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");

    private PlaylistParser() {
    }

    static List<Channel> parse(String body, String baseUrl) throws Exception {
        String text = stripBom(body).trim();
        if (text.isEmpty()) {
            return new ArrayList<>();
        }

        if (isJson(text)) {
            return parseJson(text, baseUrl);
        }

        if (isHlsMediaPlaylist(text)) {
            List<Channel> direct = new ArrayList<>();
            direct.add(new Channel("直播源", baseUrl, "直接播放", ""));
            return direct;
        }

        if (text.startsWith("#EXTM3U") || text.contains("#EXTINF")) {
            return parseM3u(text, baseUrl);
        }

        List<Channel> simple = parseSimpleText(text, baseUrl);
        if (!simple.isEmpty()) {
            return simple;
        }

        if (looksPlayableUrl(text)) {
            List<Channel> direct = new ArrayList<>();
            direct.add(new Channel("直播源", resolve(baseUrl, text), "直接播放", ""));
            return direct;
        }

        return simple;
    }

    static List<String> groupsFor(List<Channel> channels) {
        Set<String> groups = new LinkedHashSet<>();
        groups.add("全部");
        for (Channel channel : channels) {
            groups.add(channel.group);
        }
        return new ArrayList<>(groups);
    }

    private static List<Channel> parseM3u(String text, String baseUrl) {
        List<Channel> channels = new ArrayList<>();
        String pendingName = "";
        String pendingGroup = "";
        String pendingLogo = "";
        String pendingUserAgent = "";
        String pendingReferer = "";

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("#EXTINF")) {
                pendingName = firstNonEmpty(attribute(line, "tvg-name"), nameAfterComma(line));
                pendingGroup = attribute(line, "group-title");
                pendingLogo = attribute(line, "tvg-logo");
                pendingUserAgent = firstNonEmpty(attribute(line, "http-user-agent"), attribute(line, "user-agent"));
                pendingReferer = firstNonEmpty(attribute(line, "http-referrer"), attribute(line, "referer"));
                continue;
            }

            if (line.startsWith("#EXTVLCOPT:")) {
                String option = line.substring("#EXTVLCOPT:".length()).trim();
                int equals = option.indexOf('=');
                if (equals > 0) {
                    String key = option.substring(0, equals).trim();
                    String value = option.substring(equals + 1).trim();
                    if ("http-user-agent".equalsIgnoreCase(key)) {
                        pendingUserAgent = value;
                    } else if ("http-referrer".equalsIgnoreCase(key) || "http-referer".equalsIgnoreCase(key)) {
                        pendingReferer = value;
                    }
                }
                continue;
            }

            if (line.startsWith("#")) {
                continue;
            }

            if (looksPlayableUrl(line)) {
                String url = resolve(baseUrl, line);
                String name = firstNonEmpty(pendingName, deriveName(url));
                channels.add(new Channel(name, url, pendingGroup, pendingLogo, pendingUserAgent, pendingReferer));
                pendingName = "";
                pendingGroup = "";
                pendingLogo = "";
                pendingUserAgent = "";
                pendingReferer = "";
            }
        }
        return channels;
    }

    private static List<Channel> parseSimpleText(String text, String baseUrl) {
        List<Channel> channels = new ArrayList<>();
        String currentGroup = "";

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (!line.contains(",") && !looksPlayableUrl(line)) {
                currentGroup = line;
                continue;
            }

            int comma = line.indexOf(',');
            if (comma > 0) {
                String name = line.substring(0, comma).trim();
                String url = line.substring(comma + 1).trim();
                if (looksPlayableUrl(url)) {
                    channels.add(new Channel(name, resolve(baseUrl, url), currentGroup, ""));
                }
            } else if (looksPlayableUrl(line)) {
                String url = resolve(baseUrl, line);
                channels.add(new Channel(deriveName(url), url, currentGroup, ""));
            }
        }

        return channels;
    }

    private static List<Channel> parseJson(String text, String baseUrl) throws Exception {
        char first = text.charAt(0);
        if (first == '[') {
            return channelsFromArray(new JSONArray(text), baseUrl);
        }

        JSONObject object = new JSONObject(text);
        for (String key : new String[]{"channels", "data", "items", "list", "results"}) {
            if (object.has(key) && object.opt(key) instanceof JSONArray) {
                return channelsFromArray(object.getJSONArray(key), baseUrl);
            }
        }

        List<Channel> single = channelFromObject(object, baseUrl);
        return single;
    }

    private static List<Channel> channelsFromArray(JSONArray array, String baseUrl) throws Exception {
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (item instanceof JSONObject) {
                channels.addAll(channelFromObject((JSONObject) item, baseUrl));
            } else if (item instanceof String && looksPlayableUrl((String) item)) {
                String url = resolve(baseUrl, (String) item);
                channels.add(new Channel(deriveName(url), url, "", ""));
            }
        }
        return channels;
    }

    private static List<Channel> channelFromObject(JSONObject object, String baseUrl) throws Exception {
        List<Channel> channels = new ArrayList<>();
        String name = firstJsonValue(object, "name", "title", "tvg-name", "channel", "id");
        String group = firstJsonValue(object, "group", "group-title", "category", "type");
        String logo = firstJsonValue(object, "logo", "tvg-logo", "icon", "cover");
        String url = firstJsonValue(object, "url", "uri", "link", "stream", "stream_url", "playUrl", "播放地址");

        if (looksPlayableUrl(url)) {
            String resolved = resolve(baseUrl, url);
            channels.add(new Channel(firstNonEmpty(name, deriveName(resolved)), resolved, group, logo));
            return channels;
        }

        for (String key : new String[]{"urls", "streams", "sources", "playUrls"}) {
            if (object.has(key) && object.opt(key) instanceof JSONArray) {
                JSONArray array = object.getJSONArray(key);
                for (int i = 0; i < array.length(); i++) {
                    String item = array.optString(i, "");
                    if (looksPlayableUrl(item)) {
                        String resolved = resolve(baseUrl, item);
                        channels.add(new Channel(firstNonEmpty(name, deriveName(resolved)), resolved, group, logo));
                    }
                }
            }
        }

        return channels;
    }

    private static String firstJsonValue(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String attribute(String line, String key) {
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(line);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(key)) {
                return matcher.group(2).trim();
            }
        }
        return "";
    }

    private static String nameAfterComma(String line) {
        int comma = line.lastIndexOf(',');
        if (comma < 0 || comma + 1 >= line.length()) {
            return "";
        }
        return line.substring(comma + 1).trim();
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first.trim();
    }

    private static String deriveName(String url) {
        String clean = url == null ? "" : url;
        int query = clean.indexOf('?');
        if (query >= 0) {
            clean = clean.substring(0, query);
        }
        int slash = clean.lastIndexOf('/');
        String name = slash >= 0 ? clean.substring(slash + 1) : clean;
        return name.isEmpty() ? "直播源" : name;
    }

    private static String resolve(String baseUrl, String candidate) {
        try {
            return URI.create(baseUrl).resolve(candidate.trim()).toString();
        } catch (Exception ignored) {
            return candidate.trim();
        }
    }

    private static boolean isJson(String text) {
        return text.startsWith("{") || text.startsWith("[");
    }

    private static boolean isHlsMediaPlaylist(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        return upper.contains("#EXT-X-TARGETDURATION")
                || upper.contains("#EXT-X-MEDIA-SEQUENCE")
                || upper.contains("#EXT-X-PLAYLIST-TYPE")
                || upper.contains("#EXT-X-STREAM-INF");
    }

    private static boolean looksPlayableUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("rtsp://")
                || lower.endsWith(".m3u8")
                || lower.endsWith(".mp4")
                || lower.endsWith(".mpd");
    }

    private static String stripBom(String text) {
        if (text == null) {
            return "";
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
