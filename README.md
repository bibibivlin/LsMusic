# L's Music

一个专注于音乐的 Android DLNA 控制器。手机不传输音频，而是负责浏览局域网音乐库、管理播放列表，并让 DLNA 播放设备直接从媒体服务器播放音乐。

## 功能

- 发现局域网中的 DLNA / UPnP 媒体服务器与播放器
- 浏览文件夹、专辑和播放列表，仅展示可播放的音频内容
- 自适应专辑封面网格与歌曲列表，支持手机、平板和折叠屏
- 手机侧播放列表：播放全部、加入队列、排序、切歌与进度跳转
- 可选“本机”作为播放设备，使用 Media3 播放远程音乐
- 可选将正在播放及满足自定义阈值的播放记录上报到 ListenBrainz，保存令牌前会同时校验鉴权与网络连接
- 远程播放支持 Android 系统媒体卡片、锁屏与蓝牙媒体控制
- Material 3 Expressive 界面、动态配色和设备选择记忆
- 针对 Astell&Kern / IRIVER AK Connect（如 SR35）的 SOAP 兼容控制

## 使用方式

1. 将手机、DLNA 媒体服务器和播放设备接入同一 Wi‑Fi。
2. 安装应用并允许局域网、通知等所需权限。
3. 在“设置”中扫描并选择媒体库和播放设备。
4. 浏览音乐库，选择歌曲、专辑或播放列表开始播放。

### ListenBrainz

1. 在 ListenBrainz 的账户设置中复制用户令牌。
2. 打开应用的“设置”页面，在最底部“网络”分类中填写令牌并选择“校验并保存”。
3. 校验成功后启用“ListenBrainz 播放记录”，再按需要调整最小播放时长和最小播放百分比。

应用会在曲目开始播放时发送 `playing_now`；曲目结束、停止或切换后，实际播放时长达到任一阈值才会永久记录。暂停时间不计入播放时长，拖动进度也不会虚增记录进度。默认规则遵循 ListenBrainz 建议：播放 4 分钟或曲目时长的 50%，先满足者生效。

如果 DLNA 媒体服务器在 DIDL-Lite 元数据中提供 Picard/MusicBrainz 标签，应用会优先提交 recording、release 和 artist MBID；无法取得 MBID 时则提交曲名、艺术家和专辑文本。令牌保存在单独的本机偏好文件中，并排除在 Android 云备份与设备迁移之外。接口格式参见 [ListenBrainz API 文档](https://listenbrainz.readthedocs.io/en/latest/users/api/)。

> 访客网络、AP 隔离和 VPN 可能阻断 SSDP 设备发现。DLNA 互通建议在真机上验证，模拟器通常无法可靠接收局域网组播通知。

## 开发

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

生成的调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术栈

Kotlin、Jetpack Compose、Material 3 Expressive、Media3、jUPnP / DLNA、Coil。

## 目录

- `app/src/main/java/com/linxyi/lsmusic/dlna/`：设备发现、媒体库浏览与播放控制
- `app/src/main/java/com/linxyi/lsmusic/ui/`：Compose 界面、状态管理和主题
- `app/src/main/java/com/linxyi/lsmusic/playback/`：本机与远程系统媒体会话
- `app/src/main/java/com/linxyi/lsmusic/listenbrainz/`：播放跟踪、MusicBrainz 元数据与 ListenBrainz 上报
