package com.example.livelink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SubscriptionClient {
    private static final int MAX_REDIRECTS = 8;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private SubscriptionClient() {
    }

    static Result load(String inputUrl) throws Exception {
        String normalizedUrl = normalizeUrl(inputUrl);
        FetchResult fetch = fetch(normalizedUrl, 0);
        List<Channel> channels = PlaylistParser.parse(fetch.body, fetch.finalUrl);
        if (channels.isEmpty() && looksPlayable(fetch.finalUrl)) {
            channels = new ArrayList<>();
            channels.add(new Channel("直播源", fetch.finalUrl, "直接播放", ""));
        }
        return new Result(fetch.finalUrl, channels);
    }

    private static String normalizeUrl(String inputUrl) {
        String trimmed = inputUrl == null ? "" : inputUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("请输入订阅链接");
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("只支持 http:// 或 https:// 链接");
        }
        return trimmed;
    }

    private static FetchResult fetch(String url, int redirectCount) throws Exception {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("重定向次数过多");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "LiveLink/1.0 Android");
        connection.setRequestProperty("Accept", "application/x-mpegURL,audio/mpegurl,text/plain,application/json,*/*");

        int status = connection.getResponseCode();
        if (isRedirect(status)) {
            String location = connection.getHeaderField("Location");
            if (location == null || location.trim().isEmpty()) {
                throw new IOException("重定向响应缺少 Location");
            }
            String nextUrl = URI.create(url).resolve(location.trim()).toString();
            connection.disconnect();
            return fetch(nextUrl, redirectCount + 1);
        }

        if (status < 200 || status >= 300) {
            String message = connection.getResponseMessage();
            connection.disconnect();
            throw new IOException("请求失败: HTTP " + status + (message == null ? "" : " " + message));
        }

        Charset charset = charsetFromContentType(connection.getContentType());
        byte[] bytes = readLimited(connection.getInputStream());
        String body = new String(bytes, charset);
        String finalUrl = connection.getURL().toString();
        connection.disconnect();
        return new FetchResult(finalUrl, body);
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private static byte[] readLimited(InputStream inputStream) throws IOException {
        try (InputStream input = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IOException("订阅内容超过 8MB，已停止读取");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        int charsetIndex = lower.indexOf("charset=");
        if (charsetIndex < 0) {
            return StandardCharsets.UTF_8;
        }
        String charset = contentType.substring(charsetIndex + "charset=".length()).trim();
        int semicolon = charset.indexOf(';');
        if (semicolon >= 0) {
            charset = charset.substring(0, semicolon);
        }
        try {
            return Charset.forName(charset.replace("\"", ""));
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private static boolean looksPlayable(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.endsWith(".m3u8")
                || lower.endsWith(".mp4")
                || lower.endsWith(".mpd")
                || lower.startsWith("rtsp://");
    }

    static final class Result {
        final String finalUrl;
        final List<Channel> channels;

        Result(String finalUrl, List<Channel> channels) {
            this.finalUrl = finalUrl;
            this.channels = channels;
        }
    }

    private static final class FetchResult {
        final String finalUrl;
        final String body;

        FetchResult(String finalUrl, String body) {
            this.finalUrl = finalUrl;
            this.body = body;
        }
    }
}
