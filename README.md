# L's Music

[![Android CI](https://github.com/bibibivlin/LsMusic/actions/workflows/android-ci.yml/badge.svg)](https://github.com/bibibivlin/LsMusic/actions/workflows/android-ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/bibibivlin/LsMusic)](https://github.com/bibibivlin/LsMusic/releases/latest)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)

一个专注于音乐的 Android DLNA 控制器。手机不传输音频，而是负责浏览局域网音乐库、管理播放列表，并让 DLNA 播放设备直接从媒体服务器播放音乐。应用需要 Android 12 或更高版本。

## 下载与安装

正式版本发布在 [GitHub Releases](https://github.com/bibibivlin/LsMusic/releases)。下载名称形如 `LsMusic-v1.0.0.apk` 的安装包，并在 Android 系统提示时允许当前应用安装未知来源应用。Release 同时提供 `SHA256SUMS.txt`，可用于核对下载文件是否完整。

```bash
sha256sum -c SHA256SUMS.txt
```

每个正式 APK 都由 GitHub Actions 从对应标签构建，并附带可验证的构建来源证明：

```bash
gh attestation verify LsMusic-v1.0.0.apk -R bibibivlin/LsMusic
```

后续版本必须使用与已安装版本兼容的签名证书才能直接升级。请只从本仓库的 Releases 页面下载安装包。

## 功能

- 发现局域网中的 DLNA / UPnP 媒体服务器与播放器
- 浏览文件夹、专辑和播放列表，仅展示可播放的音频内容
- 自适应专辑封面网格与歌曲列表，支持手机、平板和折叠屏
- 返回上级目录时直接恢复原有内容、搜索条件、布局模式和浏览位置
- 在媒体库中快速按服务器默认顺序、年份升降序、专辑艺术家或多语言标题排序专辑
- 流畅浏览大型专辑库，进入专辑时先显示列表中已有的封面，再自动升级到服务器大图或音频内嵌高清封面
- 手机侧播放列表：播放全部、加入队列、长按拖动手柄排序、切歌与进度跳转
- 可选“本机”作为播放设备，使用 Media3 播放远程音乐
- 可选在线滚动歌词，从网易云音乐和 QQ 音乐自动匹配，支持逐行/逐字同步与双语显示
- 可选将正在播放及满足自定义阈值的播放记录上报到 ListenBrainz，断网时会保留记录并在网络恢复后重试
- 远程播放支持 Android 系统媒体卡片、锁屏与蓝牙媒体控制
- Material 3 Expressive 界面，可使用壁纸动态配色或选择内置预设配色
- 远程播放器 SOAP 兼容控制；遇到本机序列化异常时会自动回退，暂停状态下无法直接换曲时会停止后重试

## 使用方式

1. 将手机、DLNA 媒体服务器和播放设备接入同一 Wi‑Fi。
2. 安装应用并允许局域网、通知等所需权限。
3. 在“设置”中扫描并选择媒体库和播放设备。
4. 浏览音乐库，选择歌曲、专辑或播放列表开始播放。

### 在线歌词

在线歌词默认关闭。前往“设置 > 歌词”打开“在线获取歌词”后，在正在播放页点击专辑封面即可打开歌词：空间不足或屏幕比例接近方形时会用歌词替换封面，只有可用宽高和屏幕比例都适合时才并排显示封面与歌词；轻点歌词区域可返回封面。曲目信息、播放进度和控制按钮始终保留。

应用会按设置中的顺序从网易云音乐和 QQ 音乐自动查找高置信度匹配。可以拖动来源手柄调整优先级，选择是否显示当前歌词来源，并选择仅原文、双语或仅中文显示；仅中文在没有译文时会回退到原文。当前行会保持在歌词区域中央。手动拖动时所有歌词会平滑变清晰，停止操作片刻后自动回到当前行并恢复特效。歌词特效可单独关闭，以便在图形性能较弱的设备上只保留基础滚动和整行高亮。无时间戳歌词仍可手动滚动查看；全部来源都没有匹配时显示“无歌词”，网络请求失败时可点击重试。

成功获取的歌词会在应用缓存目录中保留最多 30 天，未找到结果会短期缓存 24 小时，缓存总量限制为 25 MiB。可在“设置 > 歌词 > 歌词缓存”查看占用并一键清除。关闭在线歌词后，应用不会读取这些缓存或请求歌词服务。

当前版本只支持在线获取，不读取音频文件、媒体标签或用户本地歌词文件，也不提供手动选曲、歌词时间偏移或点击歌词跳转。网易云音乐和 QQ 音乐的非官方公开接口可能随服务端调整而暂时失效。

在“设置 > 界面”中关闭“动态配色”后，配色卡片会展开显示内置颜色。点选任一色球即可立即应用对应的 Material 3 Expressive 配色；浅色与深色模式会分别使用与该重点色协调的完整颜色方案，选择结果会保留到下次启动。

### 专辑排序

进入媒体服务器提供的专辑目录后，可直接在媒体库的专辑工具条中切换排序方式，无需前往设置页面：

- `服务器默认排序`：不向 ContentDirectory 指定 `SortCriteria`，沿用服务器返回的专辑顺序。
- `年份：从早到晚` / `年份：从新到旧`：使用 DIDL-Lite 的专辑日期，未提供年份的专辑固定排在末尾。
- `专辑艺术家`：优先使用专辑艺术家，缺失时回退到容器创建者；仍然缺失的专辑排在末尾。
- `标题`：依次排列数字和符号、英文、中文（按拼音）及其他语言。

工具条会显示当前结果中的专辑数量，并将路径导航独立成行，以适配窄屏手机。紧凑封面模式会根据卡片宽度调整标题字号并保持卡片信息区对齐。

排序入口依赖服务器将容器声明为标准 UPnP 专辑类型（如 `object.container.album.musicAlbum`）。年份和专辑艺术家的完整程度取决于服务器提供的 `dc:date`、`upnp:artist` 或 `dc:creator` 元数据；即使这些字段缺失，标题排序和服务器默认排序仍可正常使用。

### 媒体库浏览体验

从专辑或子目录返回时，应用会保留上级目录的搜索条件、网格/列表模式和浏览位置，直接回到离开前看到的内容，无需重新翻页查找。切换媒体库或重新启动应用后，内容会从当前服务器重新加载。

搜索框滚出屏幕后会在右上角变为圆形搜索按钮，点击即可返回页面顶部并聚焦搜索框。内容较多时，可拖动屏幕最右侧的滚动条，在列表或封面网格中快速定位。

浏览大型专辑库时，封面会随着浏览进度逐步加载。打开专辑后，详情页会先显示专辑列表中已经出现的封面，再尝试服务器提供的其他大图；如果服务器只提供缩略图，应用会从专辑前三首歌曲中寻找音频内嵌封面并在后台无缝替换。成功提取的封面会保存在有上限的临时缓存中。

读取远端音频内嵌封面需要媒体服务器支持 HTTP 分段请求；应用只读取标签和图片所在的有限区段，不会为获取封面下载完整音频。服务器不支持分段读取、文件没有内嵌图片或图片损坏时，详情页会继续使用服务器能够提供的最佳封面。首次加载速度仍取决于 DLNA 服务器和局域网连接。

### ListenBrainz

1. 在 ListenBrainz 的账户设置中复制用户令牌。
2. 打开应用的“设置”页面，在最底部“网络”分类中填写令牌并选择“校验并保存”。
3. 校验成功后启用“ListenBrainz 播放记录”，再按需要调整最小播放时长和最小播放百分比。

应用会在曲目开始播放时发送 `playing_now`；曲目结束、停止或切换后，实际播放时长达到任一阈值才会永久记录。暂停时间不计入播放时长，拖动进度也不会虚增记录进度。默认规则遵循 ListenBrainz 建议：播放 4 分钟或曲目时长的 50%，先满足者生效。`playing_now` 具有时效性，断网发送失败时不会弹出提示，也不会在恢复网络后补报。

正式播放记录会先保存在本机，只有 ListenBrainz 确认上传成功后才会移除。断网、服务不可用、设备重启或应用进程被系统结束都不会让尚未成功上传的记录消失；网络恢复后系统会自动重试，并始终保留曲目实际开始播放的时间。存在待上传记录时，“设置 > 网络”会显示管理入口，可查看失败原因、立即重试全部或单条记录，以及删除单条或清空列表。待上传列表不参与云备份和设备迁移，卸载应用会同时删除这些记录。

应用会优先使用音频文件内嵌的 Picard/MusicBrainz 标签，并在文件标签缺失时使用 DLNA 服务器提供的 DIDL-Lite 元数据。目前可从 MP3、FLAC、DSF 和 DFF 音频源读取标签；服务器不允许分段读取或文件没有相应标签时，则提交可取得的曲名、艺术家和专辑文本。令牌保存在单独的本机偏好文件中，并排除在 Android 云备份与设备迁移之外。接口格式参见 [ListenBrainz API 文档](https://listenbrainz.readthedocs.io/en/latest/users/api/)。

ListenBrainz 请求会跟随 Android 当前的默认网络，包括系统 VPN；从 Wi‑Fi 切换到移动网络后无需重启应用。VPN 的分应用规则仍需允许 L's Music 访问网络。

> 访客网络、AP 隔离和 VPN 可能阻断 SSDP 设备发现。DLNA 互通建议在真机上验证，模拟器通常无法可靠接收局域网组播通知。

## 开发

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

生成的调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。

未提供发布签名环境变量时，本地生成的 release APK 默认未签名；正式标签由 GitHub Actions 使用受保护的发布凭据签名。DLNA 设备发现和播放建议在真机及非隔离的局域网中验证。

## 技术栈

Kotlin、Jetpack Compose、Material 3 Expressive、Media3、jUPnP / DLNA、Coil。

在线来源协议实现参考 Apache-2.0 许可的 [jitwxs/163MusicLyrics](https://github.com/jitwxs/163MusicLyrics) 和 [cqjjjzr/MusicBee-NeteaseLyrics](https://github.com/cqjjjzr/MusicBee-NeteaseLyrics)，QQ QRC 协议实现也与 MIT 许可的 [jixunmoe-go/qrc](https://github.com/jixunmoe-go/qrc) 交叉验证。歌词动画设计参考 Apache-2.0 许可的 [dokar3/amlv](https://github.com/dokar3/amlv)。[applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics) 与 [BetterLyrics](https://github.com/jayfunc/BetterLyrics) 仅用于视觉调研，未复制其代码。详细归属与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 目录

- `app/src/main/java/com/linxyi/lsmusic/dlna/`：设备发现、媒体库浏览与播放控制
- `app/src/main/java/com/linxyi/lsmusic/artwork/`：高清封面选择、内嵌图片读取与缓存
- `app/src/main/java/com/linxyi/lsmusic/ui/`：Compose 界面、状态管理、专辑排序和主题
- `app/src/main/java/com/linxyi/lsmusic/playback/`：本机与远程系统媒体会话
- `app/src/main/java/com/linxyi/lsmusic/listenbrainz/`：播放跟踪、MusicBrainz 元数据与 ListenBrainz 上报
- `app/src/main/java/com/linxyi/lsmusic/lyrics/`：在线歌词来源、匹配、解析与缓存

## 项目信息

- [隐私说明](PRIVACY.md)
- [参与贡献](CONTRIBUTING.md)
- [行为准则](CODE_OF_CONDUCT.md)
- [安全问题报告](SECURITY.md)
- [版本记录](CHANGELOG.md)
- [第三方声明](THIRD_PARTY_NOTICES.md)
