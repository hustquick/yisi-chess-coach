# 弈思国际象棋教练

弈思国际象棋教练是一套行棋优先的本地国际象棋学习工具，同时提供 iOS、Android 和 HTML 三个版本。分析引擎为 [Stockfish 18](https://github.com/official-stockfish/Stockfish/releases/tag/sf_18)，源码固定于官方 `sf_18` 标签（提交 `cb3d4ee9b47d0c5aae855b12379378ea1439675c`）。

界面的首要原则是：棋盘优先、行棋优先。用户落子时会立即中断旧局面的搜索，先更新棋盘，再在后台分析新局面。

## 主要功能

- 标准国际象棋规则，包含王车易位、吃过路兵、兵升变和王安全检查。
- Stockfish 18 + NNUE 本地多候选着法分析。
- 双人对弈、人机对战、悔棋、重开和棋盘翻转。
- 人机对战提供“业余一级”到“专业九级”十档参考强度，并通过 Stockfish 的 `UCI_LimitStrength` 与 `UCI_Elo` 实际限制电脑应着。
- 候选着法、评分、搜索深度、主变化和对局记录。
- 教练分析、局势图、棋谱和设置模块可折叠。
- iPhone/iPad 自适应 SwiftUI 布局；Android 手机/平板自适应竖屏和横屏；HTML 响应式布局。
- 移动端的 Stockfish 直接编译进 App/APK，运行时无需网络。

各端使用同一组 1320–3100 参考 Elo，默认“业余九级（2100）”。这些等级用于形成直观的学习阶梯，并不是任何对弈平台的官方段级换算。限强只作用于电脑实际走棋，教练候选分析仍保持完整强度。

## 仓库结构

```text
国际象棋/
├── shared/Stockfish/    Stockfish 18 官方完整源码与 GPL 文件
├── iOS/                 SwiftUI + Objective-C++ + Stockfish
├── android/             Android View/Canvas + JNI + Stockfish
├── html/                React + chess.js + 本地 UCI 引擎服务
└── README.md
```

## 快速构建

以下命令在 `国际象棋/` 目录执行。

首次克隆后，先下载 Stockfish 18 官方 NNUE 网络（网络文件超过 GitHub 普通文件大小限制，不直接存入仓库）：

```bash
./tools/setup-engine.sh
```

### HTML

```bash
npm --prefix html install
npm --prefix html run engine:setup   # 首次：下载并编译官方 Stockfish 18
npm --prefix html run local          # Stockfish UCI 服务 + HTML 开发服务器
```

打开 <http://localhost:3000/>。网页会连接 `127.0.0.1:8788`。只验证页面构建时可运行：

```bash
npm --prefix html test
```

### iOS / iPadOS

```bash
open iOS/YisiChessCoach.xcodeproj
```

在 Xcode 选择 `YisiChessCoach` scheme、开发团队和设备后运行。无签名模拟器构建验证：

```bash
xcodebuild -project iOS/YisiChessCoach.xcodeproj \
  -scheme YisiChessCoach -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iOS/build/simulator CODE_SIGNING_ALLOWED=NO build
```

### Android

```bash
android/gradlew -p android assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17、Android SDK 35、NDK 和 CMake 3.22.1。当前 APK 目标为 `arm64-v8a`，最低 Android 8.0。

## 与 Arena、Cute Chess、Scid 配合

Arena、Cute Chess 和 Scid 都可以加载标准 UCI 引擎。先编译仓库中的 Stockfish 18：

```bash
make -C shared/Stockfish/src net
make -C shared/Stockfish/src -j4 build ARCH=apple-silicon  # Apple Silicon
# Intel/AMD 电脑可换成 ARCH=x86-64
```

然后在 GUI 的“添加 UCI 引擎”功能中选择 `shared/Stockfish/src/stockfish`。弈思移动版和 HTML 版的内置教练界面不依赖这些桌面 GUI；它们与这些 GUI 一样使用 UCI/Stockfish 分析模型。

## 开源与隐私

Stockfish 按 GPL-3.0 发布。完整对应源码、作者信息和许可证位于 [`shared/Stockfish/`](shared/Stockfish/)，iOS 与 Android 安装包也会附带 GPL 文件。引擎分析在本机完成，不上传对局。
