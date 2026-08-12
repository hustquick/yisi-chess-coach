import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("international chess UI keeps board-first and cancellation behavior", async()=>{
  const [page,css,server]=await Promise.all([
    readFile(new URL("../app/page.tsx",import.meta.url),"utf8"),
    readFile(new URL("../app/globals.css",import.meta.url),"utf8"),
    readFile(new URL("../tools/stockfish-server.mjs",import.meta.url),"utf8")]);
  assert.match(page,/Stockfish/);assert.match(page,/new Chess/);assert.match(page,/cancelLocalRequest\(\)/);
  assert.match(page,/分析时仍可行棋/);assert.ok(page.indexOf('className="board"')<page.indexOf('教练分析'));
  assert.match(css,/grid-template-columns:\s*repeat\(8,\s*1fr\)/);assert.match(server,/req\.url === "\/stop"/);
  assert.match(server,/queue = queue\.catch/);assert.match(server,/error: "superseded"/);
  assert.match(server,/STOCKFISH_THREADS/);
});

test("HTML coach keeps feature parity with the native coach", async()=>{
  const page=await readFile(new URL("../app/page.tsx",import.meta.url),"utf8");
  assert.match(page,/候选箭头/);
  assert.match(page,/candidate-arrows/);
  assert.match(page,/searchMoves/);
  assert.match(page,/局势图/);
  assert.match(page,/棋谱与存档/);
  assert.match(page,/localStorage/);
  assert.match(page,/FEN 局面/);
  assert.match(page,/摆盘模式/);
  assert.match(page,/完成摆盘/);
  assert.match(page,/上一步：/);
});

test("board and play controls stay ahead of low-frequency mode settings", async()=>{
  const page=await readFile(new URL("../app/page.tsx",import.meta.url),"utf8");
  const settings=page.indexOf('<Module title="对弈与分析设置">');
  assert.ok(page.indexOf('className="toolbar"')<settings);
  assert.ok(page.indexOf('className="board-shell"')<settings);
  assert.equal(page.includes('<Module title="对弈模式">'),false);
  assert.equal(page.includes('GUI 与 UCI'),false);
  assert.ok(page.indexOf('<Module title="教练分析"')<page.indexOf('<Module title="局势图"'));
  assert.ok(page.indexOf('<Module title="局势图"')<settings);
  assert.ok(settings<page.indexOf('<Module title="棋谱与存档">'));
});
