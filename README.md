# L's Music

[![Android CI](https://github.com/bibibivlin/LsMusic/actions/workflows/android-ci.yml/badge.svg)](https://github.com/bibibivlin/LsMusic/actions/workflows/android-ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/bibibivlin/LsMusic)](https://github.com/bibibivlin/LsMusic/releases/latest)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)

L's Music 是一个 Android DLNA / UPnP 音乐控制器。它可以浏览家庭网络中的音乐库，并让 DLNA 播放设备直接播放音乐；也可以选择使用手机本机播放。

## 主要功能

- 自动发现局域网中的媒体服务器和播放设备。
- 浏览、搜索和排序文件夹、专辑与播放列表。
- 返回媒体库时恢复之前的搜索、布局和浏览位置。
- 使用远程 DLNA 设备或手机本机播放音乐。
- 管理播放队列，支持随机、重复、切歌和进度跳转。
- 支持 Android 通知栏、锁屏和蓝牙媒体控制。
- 可选在线同步歌词，支持逐行、逐字和双语显示。
- 可选将播放记录提交到 ListenBrainz，断网记录会保留并在网络恢复后重试。
- 支持手机、平板和折叠屏，以及动态配色和预设主题。

## 下载与安装

1. 前往 [GitHub Releases](https://github.com/bibibivlin/LsMusic/releases)。
2. 下载名称形如 `LsMusic-v1.0.0.apk` 的安装包。
3. 按 Android 提示允许当前应用安装未知来源应用，然后完成安装。

应用需要 Android 12 或更高版本。Release 中的 `SHA256SUMS.txt` 可用于核对下载文件是否完整。

## 开始使用

1. 将手机、媒体服务器和 DLNA 播放设备连接到同一 Wi-Fi。
2. 打开 L's Music，并允许局域网、通知等必要权限。
3. 在“设置”中扫描并选择媒体库和播放设备。
4. 浏览音乐库，点击歌曲、专辑或播放列表开始播放。

如果没有远程播放设备，可以选择“本机”作为播放器。

设置首页保留完整的媒体库与播放设备选择区域；“界面”“歌词”和“网络”分别通过滑动与淡入过渡进入独立页面。“关于”中可查看软件版本、项目主页、隐私说明和开源声明。

## 退出应用

在设置首页最底部点击“退出”，会停止本机和当前受控 DLNA 设备的播放，并移除本应用的媒体通知与系统媒体控制连接。远程设备断连时，应用会提示无法确认设备已停止，然后退出。

启用 ListenBrainz 并保存有效令牌后，退出会清除本应用的正在播放状态；达到上传门槛的播放会按实际播放时长保存为正式记录。应用最多等待 5 秒尝试上传，失败的记录会保留并在之后自动重试。若本机存储写入失败，播放仍会停止，页面会保留供用户重试保存后退出。

## 在线歌词

在线歌词默认关闭，可在“设置 > 歌词”中启用。播放歌曲后，在正在播放页面点击专辑封面即可打开歌词。

应用会按照用户设置的顺序查找网易云音乐和 QQ 音乐歌词，并提供双语显示、来源标签、字号和视觉效果等选项。没有匹配结果或网络失败时，可以稍后重试。歌词服务依赖第三方公开接口，服务端变化可能造成暂时不可用。

## ListenBrainz

ListenBrainz 上报默认关闭。使用前需要：

1. 从 ListenBrainz 账户设置复制用户令牌。
2. 在 L's Music 的“设置 > 网络”中校验并保存令牌。
3. 启用播放记录，并按需要调整最小播放时长或百分比。

只有达到设置门槛的播放才会成为正式记录。断网时尚未上传的记录会保存在设备上，用户可以在“设置 > 网络 > 待上传记录”中重试或删除。令牌不会包含在项目代码、日志或备份中。

## 常见限制

- DLNA 发现依赖局域网组播；访客网络、AP 隔离和部分 VPN 可能阻止设备互相发现。
- 模拟器通常不能可靠发现 DLNA 设备，建议使用真机。
- 媒体信息、专辑分类和封面质量取决于媒体服务器提供的内容。
- 在线歌词不读取本地歌词文件或音频文件中的歌词。
- 第一次加载大型音乐库或高清封面时，速度取决于服务器和 Wi-Fi 状况。

## 隐私

L's Music 不包含广告、用户画像或遥测 SDK。在线歌词和 ListenBrainz 都需要用户主动启用。详细说明请阅读 [隐私说明](PRIVACY.md)。

## 开发

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。未提供发布签名环境变量时，本地 release APK 默认不签名。DLNA 功能和优化版 release 建议在真机及非隔离 Wi-Fi 中验证。

## 项目信息

- [MIT 许可证](LICENSE)
- [参与贡献](CONTRIBUTING.md)
- [安全问题报告](SECURITY.md)
- [版本记录](CHANGELOG.md)
- [第三方声明](THIRD_PARTY_NOTICES.md)
