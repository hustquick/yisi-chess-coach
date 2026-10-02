import assert from 'node:assert/strict';
import { Chess } from 'chess.js';
const base = 'https://141.148.168.171/chess-engine';
async function analyze(data, origin = 'https://yisi-chess-pwa.pages.dev') {
  const response = await fetch(base + '/analyze', { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: origin }, body: JSON.stringify(data) });
  return { status: response.status, body: await response.json() };
}
const chess = new Chess(), first = { fen: chess.fen(), depth: 8, multiPV: 3 };
const second = new Chess(); second.move('e4');
const results = await Promise.all([analyze(first), analyze({ ...first, fen: second.fen() })]);
for (const [index, result] of results.entries()) {
  assert.equal(result.status, 200); assert.ok(result.body.length >= 1);
  for (const line of result.body) {
    const board = new Chess(index ? second.fen() : chess.fen());
    for (const uci of line.pv.split(' ')) board.move({ from: uci.slice(0,2), to: uci.slice(2,4), promotion: uci[4] });
  }
}
assert.equal((await analyze({ ...first, fen: 'bad\ngo infinite' })).status, 400);
assert.equal((await analyze(first, 'https://evil.example')).status, 403);
assert.equal((await analyze({ ...first, searchMoves: ['e2e5'] })).status, 400);
const limited = await analyze({ ...first, searchMoves: ['e2e4'], multiPV: 1 });
assert.equal(limited.body[0].pv.split(' ')[0], 'e2e4');
console.log('PASS full native analysis + concurrent isolation + legal variations + input/origin validation');
