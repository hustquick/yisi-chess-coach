# Chess 云端原生分析

生产网页：https://yisi-chess-pwa.pages.dev/

云主机网页：https://141.148.168.171/chess/

使用官方 Stockfish 18 `sf_18` 源码（主项目 `shared/Stockfish`），Linux ARM64 NEON 优化构建。默认大小 NNUE 网络均经源码 Makefile 校验；不是 Stockfish.js Lite。云端执行分析会上传当前 FEN 和分析参数，不上传本机存档列表。分析模式使用 Skill Level 20、UCI_LimitStrength=false；人机练习的 Elo 限制仅影响该次练习请求。

凤凰城只有 2 OCPU，当前服务每次搜索 1 线程、256 MiB Hash、最多 8 秒；目标深度是停止条件，不保证每次达到界面所选深度，界面候选显示实际完成深度。这是完整引擎、有限算力，不等同于无限深度或专用多核服务器。每个请求独立进程，最多同时运行一个，最多等待 8 个；每公网来源 IP 每分钟最多 30 次。超时、繁忙或断开会明确返回错误，不静默改用弱引擎或伪造结果。

引擎接口仅监听 127.0.0.1:8788，经 nginx `/chess-engine/` 提供 HTTPS 和指定来源访问。内存上限 1200 MiB、CPU 上限一个核；未开放新公网端口。服务路径 `/home/ubuntu/projects/yisi-chess`，systemd `yisi-chess-engine`；开机自动启动。

发布：先在 `windowsHTML` 执行 `npm ci && npm run build`，然后 `node cloud/build-pages.mjs`，最后 `wrangler pages deploy cloud/.pages-dist --project-name yisi-chess-pwa --branch main`。只发布明确列出的公开网页资源，不发布配置、服务源码、数据库。Cloudflare 提供友好入口，云主机承载完整原生计算和网页副本。离线 HTML 仍保留浏览器引擎，网页版不保证离线分析。

测试：在 cloud 目录 `npm ci && npm test && npm run test:web`。覆盖真实引擎的合法变化、两个并发请求隔离、来源和输入校验、限定着法、网页不加载 Lite、手机布局和人机落子。尚未进行大规模并发压力或长期负载验收。
