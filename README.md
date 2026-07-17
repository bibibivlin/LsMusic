# L's Music

一个专注于音乐的 Android DLNA 控制器。手机不传输音频，而是负责浏览局域网音乐库、管理播放列表，并让 DLNA 播放设备直接从媒体服务器播放音乐。

## 功能

- 发现局域网中的 DLNA / UPnP 媒体服务器与播放器
- 浏览文件夹、专辑和播放列表，仅展示可播放的音频内容
- 自适应专辑封面网格与歌曲列表，支持手机、平板和折叠屏
- 手机侧播放列表：播放全部、加入队列、排序、切歌与进度跳转
- 可选“本机”作为播放设备，使用 Media3 播放远程音乐
- 远程播放支持 Android 系统媒体卡片、锁屏与蓝牙媒体控制
- Material 3 Expressive 界面、动态配色和设备选择记忆
- 针对 Astell&Kern / IRIVER AK Connect（如 SR35）的 SOAP 兼容控制

## 使用方式

1. 将手机、DLNA 媒体服务器和播放设备接入同一 Wi‑Fi。
2. 安装应用并允许局域网、通知等所需权限。
3. 在“设置”中扫描并选择媒体库和播放设备。
4. 浏览音乐库，选择歌曲、专辑或播放列表开始播放。

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
