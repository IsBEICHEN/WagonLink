# WagonLink

WagonLink 是一个 Android 直播 / IPTV 播放器，用来加载用户自己提供且有权访问的订阅链接。

项目不内置频道，不采集内容源，也不绕过访问限制。请只使用你有权访问的直播源或订阅源。

## 下载

当前版本：`3.7`

- GitHub 仓库内下载：[WagonLink-3.7-debug.apk](https://github.com/IsBEICHEN/WagonLink/blob/main/releases/WagonLink-3.7-debug.apk)
- 直接下载链接：[WagonLink-3.7-debug.apk](https://raw.githubusercontent.com/IsBEICHEN/WagonLink/main/releases/WagonLink-3.7-debug.apk)
- 历史版本记录：[CHANGELOG.md](CHANGELOG.md)

说明：

- 当前 APK 是 debug 包，适合个人安装测试。
- 当前版本要求 Android 12 或以上。
- 如果手机提示安装来源风险，需要在系统设置里允许当前文件管理器或浏览器安装未知来源应用。

## 功能

- 支持 HTTP / HTTPS 订阅链接，包括短链接跳转。
- 支持 M3U / M3U8 频道列表。
- 支持简单文本格式的频道列表。
- 支持常见 JSON 频道列表。
- 支持频道分组筛选和频道搜索。
- 支持保存多个订阅，并在频道页快速切换。
- 支持点按订阅直接加载，长按订阅进行加载、编辑或删除。
- 支持自动加载上次选择的订阅。
- 支持解析成功后的频道缓存，减少重复加载订阅。
- 支持识别 M3U 里的 `http-user-agent`、`http-referrer` 等播放请求头。
- 支持 HLS、DASH、HTTP progressive、RTSP 等播放类型。
- 对没有 `.m3u8` 后缀但实际是 HLS 的部分直播网关地址做了兼容处理。
- 使用毛玻璃风格界面、圆角底部菜单和页面切换动画。
- 支持全屏播放时隐藏控件、点击唤出控件、锁定屏幕防误触，并保持屏幕常亮。

## 构建

1. 使用 Android Studio 打开本项目目录。
2. 安装提示所需的 Android SDK 和 Gradle 组件。
3. 选择 `app` 配置，连接 Android 12 或以上设备运行。

也可以在命令行构建：

```powershell
.\gradlew.bat assembleDebug
```

构建产物默认位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 说明

- 已启用 `android:usesCleartextTraffic="true"`，因为部分自用测试直播源仍然使用 HTTP。
- 如果订阅链接直接指向媒体播放列表，而不是频道列表，应用会把它作为单个直播源处理。
- 受 token、地区、源站限流、服务端鉴权影响的频道，可能仍然无法播放。这类情况不一定是播放器问题。
