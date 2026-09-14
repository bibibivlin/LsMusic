# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/)。用户可见的主要变化记录在此文件中。

## [1.1.0] - 2026-09-14

### Added

- 支持中文和英文界面，并可在系统支持的情况下使用应用内语言设置。
- 新增播放设置页面，可分别配置加入队列、清空当前队列和迷你播放器行为。
- 新增睡眠定时器，支持预设时长、自定义时长和播放完当前歌曲后停止。
- 设置页面按界面、播放、歌词和网络功能拆分，退出应用时可独立停止本机及远程播放。

### Changed

- 优化歌词面板的翻译、来源、字号、手动滚动和视觉效果交互。
- 统一本机 Media3 与远程 DLNA 播放的队列、播放顺序和退出行为。
- 增加更完整的界面、播放、主题、本地化和退出流程测试覆盖。

## [1.0.0] - 2026-09-03

### Added

- 发现并选择局域网中的 DLNA / UPnP 媒体服务器与播放设备。
- 浏览文件夹、专辑和播放列表，支持大型媒体库搜索、排序与快速滚动。
- 支持远程 DLNA 播放和基于 Media3 的本机播放。
- 提供队列、播放顺序、重复、随机、进度控制和 Android 系统媒体控制。
- 提供网易云音乐与 QQ 音乐在线同步歌词、翻译、缓存和视觉效果设置。
- 可选 ListenBrainz `playing_now` 与播放记录上报，支持断网持久化重试。
- 支持从 MP3、FLAC、DSF 和 DFF 的有限远程区段读取封面与 MusicBrainz 标签。
- 提供 Material 3 Expressive 自适应界面、动态配色和预设主题。

[1.0.0]: https://github.com/bibibivlin/LsMusic/releases/tag/v1.0.0
[1.1.0]: https://github.com/bibibivlin/LsMusic/releases/tag/v1.1.0
