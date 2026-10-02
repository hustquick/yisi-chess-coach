# 弈思国际象棋教练 Android

原生 Android View/Canvas 界面，通过 JNI 调用 Stockfish 18。支持手机与平板：竖屏保证棋盘优先，横屏使用棋盘和折叠分析区左右布局。

Android 1.2.0：

- 紧凑工具栏与可缩放完整棋盘；固定 Canvas 棋子造型，不依赖系统棋子字体。
- 「优」切换真实候选箭头；选中己方棋子切换至该棋子的候选分析。
- 候选提供明确的演示、落子和停止演示操作；兵升变可选择后、车、象、马。
- 双人、人机和摆棋模式；执白/执黑及十档参考 Elo。摆棋通过合法 FEN 校验后才替换当前棋局。
- 本机命名存档、载入、删除与自动恢复；FEN 导入/复制；点击着法导航，悔棋后仍可前进。
- 原生库加载错误显示提示，不再因未捕获的 LinkageError 直接退出。

电脑应着由 Stockfish 的真实 UCI 限强搜索选出。保存与自动恢复保留完整着法时间线和当前浏览位置。卸载应用会删除本机存档，升级请覆盖安装。

```bash
./tools/setup-engine.sh  # 仓库根目录首次执行
android/gradlew -p android assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

环境：JDK 17、Android SDK 35、NDK 28.0.13004108、CMake 3.22.1；最低 Android 8.0，当前 ABI 为 `arm64-v8a`。原生库和安装包均为 16 KB 对齐。Stockfish 与 NNUE 在 APK 中，运行时无需网络。选子后的合法着法会后台获取；落子通过引擎检查并提交新局面后更新棋盘。

回归测试：

```sh
android/gradlew -p android testDebugUnitTest
```

FenRulesTest 检查合法/非法 FEN。ChessUiTest 用 Robolectric 与小型引擎替身验证布局、箭头像素、升变、本机存档和棋谱导航；这不等同于真实 ARM64 Stockfish 或用户 Pixel 模拟器的实装测试。渲染预览位于 android/app/build/ui-previews。

本次检查后的界面预览：[竖屏](docs/chess-portrait.png)、[横屏](docs/chess-landscape.png)。
