import { mkdir, copyFile, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const output = resolve(import.meta.dirname, '.pages-dist');
await mkdir(output, { recursive: true });
for (const file of ['index.html', 'app.js', 'style.css', 'STOCKFISH-GPL-3.0.txt', 'CHESS.JS-LICENSE.txt', 'favicon-dark.png', 'favicon-light.png'])
  await copyFile(resolve(import.meta.dirname, '../windowsHTML', file), resolve(output, file));
let index = await readFile(resolve(output, 'index.html'), 'utf8');
index = index.replace('<title>', '<meta name="yisi-engine-endpoint" content="https://141.148.168.171/chess-engine" /><title>')
  .replace(/    <script src="\.\/engine-data.js"><\/script>\n/, '')
  .replace(/    <script id="stockfish-engine" src="\.\/stockfish.js"><\/script>\n/, '')
  .replace('正在载入本地 Stockfish 引擎，请稍候……', '正在连接云端 Stockfish 18，请稍候……')
  .replace('Stockfish 本地离线分析', 'Stockfish 18 云端原生分析');
await writeFile(resolve(output, 'index.html'), index);
await writeFile(resolve(output, 'STOCKFISH-SOURCE.txt'), 'Cloud engine: official Stockfish 18 (sf_18), native ARM64 build with full NNUE.\nSource: https://github.com/official-stockfish/Stockfish/tree/sf_18\nLicense: STOCKFISH-GPL-3.0.txt\nAnalysis: Skill Level 20, UCI_LimitStrength false, Threads 1, Hash 256 MiB.\nPractice games can use optional Elo limits. Search budget: up to 8 seconds per request.\n');
