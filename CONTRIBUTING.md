# 参与贡献

感谢你帮助改进 L's Music。提交改动前，请先搜索现有 Issue，较大的功能或行为调整建议先创建 Issue 讨论范围和兼容性。

## 开发环境

- Android Studio 与 JDK 21
- Android SDK 37
- Android 12 或更高版本的真机（涉及 DLNA 时强烈建议）

从仓库根目录运行：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

请为确定性逻辑增加 JUnit4 单元测试。单元测试不得调用真实 ListenBrainz 或歌词服务，也不得包含用户令牌。

## DLNA 与网络验证

SSDP 依赖局域网组播，模拟器、访客网络、AP 隔离和部分 VPN 配置可能无法发现设备。涉及 DLNA、网络路由或 R8 的改动，应在非隔离 Wi-Fi 上使用真机验证；涉及 jUPnP 或 release 优化的改动还必须验证优化版媒体库浏览。

## Pull Request

Pull Request 应保持范围集中，并说明：

- 用户可见变化；
- 影响本机播放、远程播放或两者；
- 已运行的测试和真机验证；
- UI 改动的截图；
- 已知限制或未验证设备。

提交内容即表示你有权按项目许可证贡献该内容，并同意贡献内容按同一许可证分发。
