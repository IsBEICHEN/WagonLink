# WagonLink

WagonLink 是一个最小可用的安卓直播/IPTV 播放器，用来加载用户自己提供且有权访问的订阅链接。

支持能力：

- HTTP/HTTPS 订阅链接，包括 bit.ly 这类会跳转的短链接。
- 带 `#EXTINF` 元数据的 M3U/M3U8 频道列表。
- 简单的 `频道名,播放地址` 文本列表。
- 常见 JSON 频道列表，例如数组对象，或包含 `channels` / `data` / `items` 的对象。
- 分组筛选、频道搜索，以及通过 AndroidX Media3 播放 HLS/DASH/HTTP/RTSP 流。

这个项目不内置频道、不采集内容源，也不绕过访问限制。请只粘贴你有权使用的直播源或订阅源。

## 构建

1. 用 Android Studio 打开本文件夹。
2. 按提示安装 Android SDK 和 Gradle 组件。
3. 选择 `app` 配置，在模拟器或安卓手机上运行。

## 说明

- 已启用 `android:usesCleartextTraffic="true"`，因为一些自用测试直播源仍然使用 HTTP。
- 如果订阅链接直接指向媒体播放列表，而不是频道列表，应用会把它当成单个直播源播放。
