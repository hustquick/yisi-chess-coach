import http from 'node:http';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { Chess } from 'chess.js';
const binary = process.env.STOCKFISH_PATH;
const origins = new Set(['https://yisi-chess-pwa.pages.dev', 'https://141.148.168.171', 'http://localhost:8080']);
const queue = [], limits = new Map(); let running = 0;
const json = (res, status, body) => { if (!res.destroyed && !res.writableEnded) { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(body)); } };
function next() { if (running || !queue.length) return; const job = queue.shift(); if (job.res.destroyed) { next(); return; } running++; run(job).finally(() => { running--; next(); }); }
async function run({ res, options }) {
  await new Promise(resolve => {
    const child = spawn(binary, [], { stdio: ['pipe', 'pipe', 'ignore'] });
    const lines = new Map(); let settled = false, started = false;
    const finish = (status, body) => { if (settled) return; settled = true; clearTimeout(timer); child.kill(); json(res, status, body); resolve(); };
    const timer = setTimeout(() => finish(504, { error: '引擎计算超时，请稍后重试' }), 14000);
    res.once('close', () => { if (!res.writableEnded) finish(499, { error: 'cancelled' }); });
    child.on('error', () => finish(503, { error: '引擎暂不可用' }));
    child.stdin.on('error', () => finish(503, { error: '引擎连接中断' }));
    child.on('exit', () => { if (!settled) finish(503, { error: '引擎退出，请重试' }); });
    const command = text => { if (!settled && child.stdin.writable) child.stdin.write(text + '\n'); };
    createInterface({ input: child.stdout }).on('line', line => {
      if (line === 'uciok') {
        command('setoption name Threads value 1'); command('setoption name Hash value 256');
        command('setoption name Skill Level value 20');
        command(`setoption name UCI_LimitStrength value ${options.elo ? 'true' : 'false'}`);
        if (options.elo) command(`setoption name UCI_Elo value ${options.elo}`);
        command(`setoption name MultiPV value ${options.multiPV}`); command('isready');
      } else if (line === 'readyok' && !started) {
        started = true; command(`position fen ${options.fen}`);
        command(`go depth ${options.depth} movetime 8000${options.searchMoves.length ? ' searchmoves ' + options.searchMoves.join(' ') : ''}`);
      } else if (line.startsWith('info ') && /\bpv /.test(line)) {
        const depth = Number(line.match(/\bdepth (\d+)/)?.[1]), multipv = Number(line.match(/\bmultipv (\d+)/)?.[1] ?? 1);
        const score = line.match(/\bscore ((?:cp|mate) -?\d+)/)?.[1], pv = line.match(/\bpv (.+)/)?.[1];
        if (Number.isFinite(depth) && score && pv) lines.set(multipv, { depth, multipv, score, pv });
      } else if (line.startsWith('bestmove ')) {
        const best = line.split(/\s+/)[1], result = [...lines.values()].sort((a,b) => a.multipv-b.multipv);
        if (options.elo && best && best !== '(none)' && result[0]?.pv.split(' ')[0] !== best) result.unshift({ depth: result[0]?.depth ?? 0, multipv: 0, score: '', pv: best });
        finish(200, result);
      }
    });
    command('uci');
  });
}
http.createServer(async (req, res) => {
  try {
    const origin = req.headers.origin;
    if (origin && !origins.has(origin)) { json(res, 403, { error: '不允许的来源' }); return; }
    if (origin) { res.setHeader('Access-Control-Allow-Origin', origin); res.setHeader('Vary', 'Origin'); }
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type'); res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
    if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }
    if (req.url === '/health') { json(res, 200, { engine: 'Stockfish 18', mode: 'native', nnue: 'full', threads: 1, hashMB: 256, maxSearchMs: 8000 }); return; }
    if (req.url !== '/analyze' || req.method !== 'POST') { json(res, 404, {}); return; }
    const ip = req.headers['x-real-ip'] ?? req.socket.remoteAddress, now = Date.now();
    const limit = limits.get(ip);
    if (limit?.until > now && limit.count >= 30) { json(res, 429, { error: '分析过于频繁，请稍后重试' }); return; }
    limits.set(ip, limit?.until > now ? { ...limit, count: limit.count + 1 } : { until: now + 60000, count: 1 });
    for (const [key, entry] of limits) if (entry.until <= now) limits.delete(key);
    let size = 0, chunks = [];
    for await (const chunk of req) { size += chunk.length; if (size > 8192) { json(res, 413, { error: '请求过大' }); return; } chunks.push(chunk); }
    const input = JSON.parse(Buffer.concat(chunks));
    if (typeof input.fen !== 'string' || input.fen.length > 150 || /[\r\n]/.test(input.fen)) throw Error();
    const chess = new Chess(input.fen);
    const legal = new Set(chess.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion ?? '')));
    if (!legal.size) { json(res, 200, []); return; }
    const searchMoves = input.searchMoves ?? [];
    if (!Array.isArray(searchMoves) || searchMoves.length > 64 || searchMoves.some(m => !legal.has(m))) throw Error();
    if (queue.length >= 8) { json(res, 503, { error: '云端分析繁忙，请稍后重试' }); return; }
    queue.push({ res, options: { fen: chess.fen(), searchMoves, depth: Math.max(8, Math.min(30, Number(input.depth) || 18)), multiPV: Math.max(1, Math.min(8, Number(input.multiPV) || 3)), elo: input.elo ? Math.max(1320, Math.min(3190, Number(input.elo) || 1320)) : 0 } }); next();
  } catch { json(res, 400, { error: '局面或分析参数无效' }); }
}).listen(8788, '127.0.0.1');
