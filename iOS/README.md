# 弈思国际象棋教练 iOS / iPadOS

原生 SwiftUI 应用，支持 iPhone 和 iPad。Stockfish 18 C++ 源码通过 Objective-C++ 桥接编译进 App，两个官方 NNUE 网络与引擎一起内嵌，可离线分析。

## 运行

```bash
./tools/setup-engine.sh  # 仓库根目录首次执行
open iOS/YisiChessCoach.xcodeproj
```

在 Xcode 中选择 `YisiChessCoach`、签名 Team 和 iPhone/iPad 后点击 Run。最低系统为 iOS/iPadOS 17。

在人机对战的“对弈与分析设置”中可选择十档电脑等级。实际应着使用 `UCI_LimitStrength`/`UCI_Elo`，教练候选分析不受限强影响。

## 构建验证

```bash
xcodebuild -project iOS/YisiChessCoach.xcodeproj \
  -scheme YisiChessCoach -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iOS/build/simulator CODE_SIGNING_ALLOWED=NO build
```

如果增删 Swift/C++ 源文件，先在项目根目录运行 `ruby iOS/generate_project.rb`。棋子移动在主线程按本地规则立即提交；Stockfish 在 utility 队列上单独搜索，落子会通过线程安全的 `stop()` 立即中断旧任务。
