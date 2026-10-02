# 2026-10-02 发布包构建

Android 1.2.0（versionCode 4），发布标签 v2026.10.02.2。本次修订 Android 布局和功能，
离线 HTML 沿用上一版已验证完整包。功能和验证范围见 android/README.md。

本次提供 Android Debug 侧载 APK（Android 8.0+、arm64-v8a）与完整离线 HTML 包。
APK 使用原有 Android 调试证书签名；仅公开证书指纹，不提供私钥。
证书 SHA-256：4057fe454cd3e892cf2745422cd5efe06cba5469037376f26f869aeecd459ce9。

## Android

安装 JDK 17、Android SDK 35、Build Tools 35.0.0、NDK 28.0.13004108 和 CMake 3.22.1。
配置 JAVA_HOME 与 ANDROID_HOME 后，在仓库根目录执行：

```sh
android/gradlew -p android assembleDebug --max-workers=2
android/gradlew -p android testDebugUnitTest --max-workers=2
```

输出：android/app/build/outputs/apk/debug/app-debug.apk。
对应源码包保留引擎源码及所需资源；GitHub 默认 Source code 包不包含忽略的模型。
国际象棋从普通 Git 克隆构建前，执行 bash tools/setup-engine.sh 下载并校验官方 Stockfish 18 神经网络。

## 离线 HTML

```sh
npm --prefix windowsHTML install
npm --prefix windowsHTML run build
```

保留 windowsHTML 内的 JS、CSS、模型、图标和许可证，完整解压后打开 index.html。
浏览器引擎固定为 Stockfish.js v18.0.8；对应源码位于 windowsHTML/source/stockfish-js，来源 https://github.com/nmrugg/stockfish.js/tree/v18.0.8。

## 已验证

- Android 1.2.0 的 9 项回归测试通过（7 项 UI/存档状态测试、2 项 FEN 测试，含 16 个非法输入案例）。
- 竖屏 393×800 与横屏 800×393 的界面渲染检查通过；完整棋盘可见，候选箭头开关确实改变像素。
- UI 测试使用引擎替身，不宣称完成真实 Stockfish 或用户 Pixel 模拟器的现场启动测试。

本次 16 KB 兼容修订使用 NDK r28 编译引擎，静态链接 C++ 标准库，
显式设置 LOAD/RELRO 的 16 KB 链接对齐，并采用未压缩原生库打包。
版本号已递增，使用相同签名证书，可覆盖安装并保留应用数据。
发布前检查 ELF LOAD/RELRO 对齐及 APK 的 16 KB ZIP 对齐。
未连接用户的 Pixel 模拟器，不能据此宣称已验证用户设备上的启动或所有闪退原因。

- Android Gradle 构建成功；apksigner 验签通过。
- APK 检查包含本地引擎、模型与许可证。
- Chrome 在断网且 file:// 打开时，棋盘与引擎候选可用，无页面脚本异常。
- Release 附件提供 SHA256SUMS.txt。
