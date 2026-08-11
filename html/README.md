# 弈思国际象棋教练 HTML

React 响应式界面，使用 `chess.js` 在浏览器主线程立即执行合法行棋，使用本地多线程 Stockfish 18 UCI 进程进行后台分析。

```bash
npm install
npm run engine:setup   # 首次需联网，编译 Stockfish 18
npm run local
```

打开 <http://localhost:3000/>。默认引擎端口为 `127.0.0.1:8788`，默认使用最多 4 个 CPU 线程，并为界面保留处理能力。可通过环境变量调整：

```bash
STOCKFISH_THREADS=6 STOCKFISH_PORT=8788 npm run local
```

其他命令：

```bash
npm run build
npm test
npm run lint
```

只运行 `npm run dev` 时不会启动引擎，棋盘可以行棋，但候选着法区会提示未连接本地 Stockfish。
