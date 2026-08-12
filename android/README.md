# 弈思国际象棋教练 Android

原生 Android View/Canvas 界面，通过 JNI 调用 Stockfish 18。支持手机与平板：竖屏保证棋盘优先，横屏使用棋盘和折叠分析区左右布局。

“对弈与分析设置”可切换双人/人机、执白/执黑和十档参考 Elo；电脑应着由 Stockfish 的真实 UCI 限强搜索选出。

```bash
./tools/setup-engine.sh  # 仓库根目录首次执行
android/gradlew -p android assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

环境：JDK 17、Android SDK 35、NDK、CMake 3.22.1；最低 Android 8.0，当前 ABI 为 `arm64-v8a`。Stockfish 与 NNUE 在 APK 中，运行时无需网络。选子后的合法着法会后台获取；落子时棋盘先行显示移动，同时停止旧搜索并生成新局面。
