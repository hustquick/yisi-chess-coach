"use client";

import { Chess, type Color, type PieceSymbol, type Square } from "chess.js";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

type EngineLine = { depth: number; multipv: number; score: string; pv: string };
type Mode = "local" | "computer" | "setup";
type MoveEntry = { uci: string; san: string; fen: string };
type SetupPiece = { color: Color; type: PieceSymbol };
type SavedGame = { id: string; title: string; savedAt: string; initialFen: string; moves: string[] };
type EvalPoint = { ply: number; value: number };
type AnalyzeKind = "global" | "selection" | "computer";
type GameOutcome = { title: string; detail: string };

const startFen = new Chess().fen();
const storageKey = "yisi.chess.saved";
const symbols: Record<string, string> = { wk: "♔", wq: "♕", wr: "♖", wb: "♗", wn: "♘", wp: "♙", bk: "♚", bq: "♛", br: "♜", bb: "♝", bn: "♞", bp: "♟" };
const arrowColors = ["#1f9d55", "#2878d0", "#df8318", "#8750bd"];
const pieceTypes: PieceSymbol[] = ["k", "q", "r", "b", "n", "p"];

function Module({ title, open = false, children }: { title: string; open?: boolean; children: ReactNode }) {
  return <details className="module" open={open}><summary>{title}</summary><div className="module-body">{children}</div></details>;
}

function scorePawns(value: string) {
  const [, kind, amount] = value.match(/(cp|mate) (-?\d+)/) ?? [];
  if (kind === "mate") return Number(amount) >= 0 ? 100 : -100;
  return Number(amount || 0) / 100;
}

function scoreText(value: string) {
  const [, kind, amount] = value.match(/(cp|mate) (-?\d+)/) ?? [];
  return kind === "mate" ? `将杀 ${amount}` : `${scorePawns(value) >= 0 ? "+" : ""}${scorePawns(value).toFixed(2)}`;
}

function uciMove(from: Square, to: Square, promotion?: string) {
  return `${from}${to}${promotion ?? ""}`;
}

function setupFromGame(game: Chess) {
  const result: Partial<Record<Square, SetupPiece>> = {};
  for (const row of game.board()) for (const piece of row) if (piece) result[piece.square] = { color: piece.color, type: piece.type };
  return result;
}

function setupFen(position: Partial<Record<Square, SetupPiece>>, turn: Color) {
  const rows: string[] = [];
  for (let rank = 8; rank >= 1; rank--) {
    let row = "", empty = 0;
    for (const file of "abcdefgh") {
      const piece = position[`${file}${rank}` as Square];
      if (!piece) { empty++; continue; }
      if (empty) { row += empty; empty = 0; }
      row += piece.color === "w" ? piece.type.toUpperCase() : piece.type;
    }
    if (empty) row += empty;
    rows.push(row);
  }
  return `${rows.join("/")} ${turn} - - 0 1`;
}

function gameOutcome(game: Chess): GameOutcome | null {
  if (game.isCheckmate()) {
    const winner = game.turn() === "w" ? "黑方" : "白方";
    return { title: `${winner}获胜`, detail: `将死。${winner}赢得本局。` };
  }
  if (game.isStalemate()) return { title: "和棋", detail: "逼和：行棋方没有合法着法，但王未被将军。" };
  if (game.isThreefoldRepetition()) return { title: "和棋", detail: "同一局面已第三次出现。" };
  if (game.isDrawByFiftyMoves()) return { title: "和棋", detail: "五十回合内没有吃子或兵的移动。" };
  if (game.isInsufficientMaterial()) return { title: "和棋", detail: "双方子力不足以将死对方。" };
  return null;
}

function EvaluationChart({ points }: { points: EvalPoint[] }) {
  const width = 640, height = 150, padding = 18;
  const coordinates = points.map((point, index) => {
    const x = points.length < 2 ? width / 2 : padding + index * (width - padding * 2) / (points.length - 1);
    const y = height / 2 - Math.max(-8, Math.min(8, point.value)) / 8 * (height / 2 - padding);
    return `${x},${y}`;
  }).join(" ");
  return <div className="chart-wrap">
    <svg className="chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="白方视角局势评分曲线">
      <line x1="0" y1={height / 2} x2={width} y2={height / 2} className="chart-zero" />
      {coordinates && <polyline points={coordinates} className="chart-line" />}
      {points.length === 1 && <circle cx={width / 2} cy={height / 2 - Math.max(-8, Math.min(8, points[0].value)) / 8 * (height / 2 - padding)} r="5" className="chart-dot" />}
    </svg>
    <span className="chart-white">白方 +</span><span className="chart-black">黑方 +</span>
  </div>;
}

export default function Page() {
  const [chess, setChess] = useState(() => new Chess());
  const [initialFen, setInitialFen] = useState(startFen);
  const [moveHistory, setMoveHistory] = useState<MoveEntry[]>([]);
  const [selected, setSelected] = useState<Square | null>(null);
  const [targets, setTargets] = useState<Set<string>>(new Set());
  const [flipped, setFlipped] = useState(false);
  const [mode, setModeState] = useState<Mode>("local");
  const [human, setHuman] = useState<Color>("w");
  const [depth, setDepth] = useState(14);
  const [multiPV, setMultiPV] = useState(5);
  const [computerElo, setComputerElo] = useState(2100);
  const [lines, setLines] = useState<EngineLine[]>([]);
  const [selectedLines, setSelectedLines] = useState<EngineLine[]>([]);
  const [status, setStatus] = useState("Stockfish 准备中");
  const [showArrows, setShowArrows] = useState(false);
  const [evaluations, setEvaluations] = useState<EvalPoint[]>([]);
  const [lastMoveGrade, setLastMoveGrade] = useState("—");
  const [lastMoveReview, setLastMoveReview] = useState("等待落子，Stockfish 将评价着法质量。");
  const [savedGames, setSavedGames] = useState<SavedGame[]>(() => {
    if (typeof window === "undefined") return [];
    try { return JSON.parse(localStorage.getItem(storageKey) ?? "[]") as SavedGame[]; } catch { return []; }
  });
  const [recordOpen, setRecordOpen] = useState(false);
  const [fenText, setFenText] = useState(startFen);
  const [notice, setNotice] = useState<string | null>(null);
  const [outcomeOpen, setOutcomeOpen] = useState(false);
  const [setupPosition, setSetupPosition] = useState<Partial<Record<Square, SetupPiece>>>({});
  const [setupTurn, setSetupTurn] = useState<Color>("w");
  const [setupTool, setSetupTool] = useState("move");
  const generation = useRef(0);
  const controller = useRef<AbortController | null>(null);
  const pendingBestScore = useRef<number | null>(null);
  const moveCount = useRef(0);

  const cancelLocalRequest = useCallback(() => {
    generation.current++;
    controller.current?.abort();
    controller.current = null;
  }, []);

  const stop = useCallback(() => {
    cancelLocalRequest();
    fetch("http://127.0.0.1:8788/stop", { method: "POST" }).catch(() => {});
  }, [cancelLocalRequest]);

  async function analyze(game: Chess, kind: AnalyzeKind = "global", searchMoves: string[] = []) {
    // /analyze atomically supersedes and stops the previous engine search.
    // Sending a separate, fire-and-forget /stop here can arrive out of order
    // and accidentally cancel the new computer search instead of the old one.
    cancelLocalRequest();
    const outcome = gameOutcome(game);
    if (outcome) { setLines([]); setSelectedLines([]); setStatus(outcome.title); return; }
    const requestedGeneration = generation.current;
    const abort = new AbortController();
    controller.current = abort;
    setStatus(kind === "selection" ? "Stockfish · 正在分析选中棋子" : kind === "computer" ? "Stockfish 正在思考" : "Stockfish 计算中");
    try {
      const response = await fetch("http://127.0.0.1:8788/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ fen: game.fen(), depth, multiPV: kind === "computer" ? 1 : Math.min(multiPV, searchMoves.length || multiPV), searchMoves, elo: kind === "computer" ? computerElo : 0 }),
        signal: abort.signal,
      });
      if (!response.ok) throw new Error(`Stockfish HTTP ${response.status}`);
      const result = await response.json() as EngineLine[];
      if (requestedGeneration !== generation.current) return;
      if (kind === "selection") {
        setSelectedLines(result);
        setStatus("Stockfish · 已分析这枚棋子");
        return;
      }
      if (kind === "computer") {
        const move = result[0]?.pv.split(" ")[0];
        if (!move) return;
        const next = new Chess(game.fen());
        const made = next.move({ from: move.slice(0, 2) as Square, to: move.slice(2, 4) as Square, promotion: (move[4] || "q") as PieceSymbol });
        setChess(next);
        if (gameOutcome(next)) setOutcomeOpen(true);
        moveCount.current++;
        setMoveHistory(previous => [...previous, { uci: move, san: made.san, fen: next.fen() }]);
        setSelected(null); setTargets(new Set()); setSelectedLines([]);
        queueMicrotask(() => analyze(next, "global"));
        return;
      }
      setLines(result);
      setStatus(`Stockfish · 深度 ${result[0]?.depth ?? depth}`);
      if (result[0]) {
        const whiteValue = game.turn() === "w" ? scorePawns(result[0].score) : -scorePawns(result[0].score);
        const ply = moveCount.current;
        setEvaluations(previous => [...previous.filter(point => point.ply !== ply), { ply, value: whiteValue }].sort((a, b) => a.ply - b.ply));
        if (pendingBestScore.current !== null) {
          const loss = Math.max(0, pendingBestScore.current + scorePawns(result[0].score));
          if (loss < .15) { setLastMoveGrade("最佳"); setLastMoveReview("这步基本保持了 Stockfish 的最优评估。"); }
          else if (loss < .45) { setLastMoveGrade("优秀"); setLastMoveReview("这步很稳健，与首选只有很小差距。"); }
          else if (loss < 1) { setLastMoveGrade("可行"); setLastMoveReview("这步可下，但候选首选能保留更多优势。"); }
          else if (loss < 2.5) { setLastMoveGrade("失误"); setLastMoveReview("这步导致明显掉分，建议回看落子前的候选着法。"); }
          else { setLastMoveGrade("严重失误"); setLastMoveReview("局面评估大幅下降，请重点检查将杀、捉子和未受保护的棋子。"); }
          pendingBestScore.current = null;
        }
      }
    } catch (error) {
      if ((error as Error).name !== "AbortError" && requestedGeneration === generation.current) setStatus("未连接本地 Stockfish，棋盘仍可正常行棋");
    }
  }

  useEffect(() => {
    queueMicrotask(() => analyze(new Chess(), "global"));
    return stop;
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const outcome = gameOutcome(chess);
  const canHumanMove = !outcome && (mode !== "computer" || chess.turn() === human);
  const files = flipped ? [..."hgfedcba"] : [..."abcdefgh"];
  const ranks = flipped ? [1, 2, 3, 4, 5, 6, 7, 8] : [8, 7, 6, 5, 4, 3, 2, 1];
  const visibleLines = selected ? selectedLines : lines;
  const lastMove = moveHistory.at(-1)?.uci;
  const rows = useMemo(() => moveHistory.reduce<MoveEntry[][]>((result, move, index) => {
    if (index % 2 === 0) result.push([move]); else result[result.length - 1].push(move);
    return result;
  }, []), [moveHistory]);

  const commitMove = (move: string) => {
    cancelLocalRequest();
    const next = new Chess(chess.fen());
    let made;
    try { made = next.move({ from: move.slice(0, 2) as Square, to: move.slice(2, 4) as Square, promotion: (move[4] || "q") as PieceSymbol }); } catch { return; }
    pendingBestScore.current = lines[0] ? scorePawns(lines[0].score) : null;
    setChess(next);
    if (gameOutcome(next)) setOutcomeOpen(true);
    moveCount.current++;
    setMoveHistory(previous => [...previous, { uci: move, san: made.san, fen: next.fen() }]);
    setSelected(null); setTargets(new Set()); setSelectedLines([]);
    if (gameOutcome(next)) return;
    if (mode === "computer" && next.turn() !== human) queueMicrotask(() => analyze(next, "computer"));
    else queueMicrotask(() => analyze(next, "global"));
  };

  const editSetup = (square: Square) => {
    if (setupTool === "erase") {
      setSetupPosition(previous => { const next = { ...previous }; delete next[square]; return next; });
      setSelected(null); return;
    }
    if (setupTool.length === 2) {
      setSetupPosition(previous => ({ ...previous, [square]: { color: setupTool[0] as Color, type: setupTool[1] as PieceSymbol } }));
      setSelected(null); return;
    }
    if (selected && setupPosition[selected]) {
      setSetupPosition(previous => { const next = { ...previous, [square]: previous[selected]! }; delete next[selected]; return next; });
      setSelected(null);
    } else if (setupPosition[square]) setSelected(square);
  };

  const clickSquare = (square: Square) => {
    if (mode === "setup") { editSetup(square); return; }
    if (!canHumanMove) return;
    if (selected && targets.has(square)) {
      const legal = chess.moves({ square: selected, verbose: true }).find(move => move.to === square);
      if (legal) commitMove(uciMove(legal.from, legal.to, legal.promotion));
      return;
    }
    const piece = chess.get(square);
    if (piece && piece.color === chess.turn()) {
      const moves = chess.moves({ square, verbose: true });
      setSelected(square); setTargets(new Set(moves.map(move => move.to))); setSelectedLines([]);
      analyze(chess, "selection", moves.map(move => uciMove(move.from, move.to, move.promotion)));
    } else {
      setSelected(null); setTargets(new Set()); setSelectedLines([]); analyze(chess, "global");
    }
  };

  const finishSetup = (nextMode: Exclude<Mode, "setup"> = "local") => {
    const whiteKings = Object.values(setupPosition).filter(piece => piece?.color === "w" && piece.type === "k").length;
    const blackKings = Object.values(setupPosition).filter(piece => piece?.color === "b" && piece.type === "k").length;
    if (whiteKings !== 1 || blackKings !== 1) { setNotice("摆盘必须恰好包含一个白王和一个黑王。"); return; }
    try {
      const next = new Chess(setupFen(setupPosition, setupTurn));
      cancelLocalRequest(); moveCount.current = 0; setChess(next); setInitialFen(next.fen()); setMoveHistory([]); setEvaluations([]);
      setSelected(null); setTargets(new Set()); setModeState(nextMode); setLastMoveGrade("—");
      queueMicrotask(() => analyze(next, nextMode === "computer" && next.turn() !== human ? "computer" : "global"));
    } catch (error) { setNotice(`无法完成摆盘：${(error as Error).message}`); }
  };

  const changeMode = (nextMode: Mode) => {
    if (nextMode === mode) return;
    if (mode === "setup" && nextMode !== "setup") { finishSetup(nextMode); return; }
    cancelLocalRequest(); setSelected(null); setTargets(new Set()); setSelectedLines([]);
    if (nextMode === "setup") {
      setModeState("setup"); setSetupPosition(setupFromGame(chess)); setSetupTurn(chess.turn()); setLines([]); setStatus("摆盘模式 · 已暂停分析");
      fetch("http://127.0.0.1:8788/stop", { method: "POST" }).catch(() => {});
    } else {
      setModeState(nextMode);
      queueMicrotask(() => analyze(chess, nextMode === "computer" && chess.turn() !== human ? "computer" : "global"));
    }
  };

  const goToPly = (ply: number) => {
    cancelLocalRequest();
    const safe = Math.max(0, Math.min(ply, moveHistory.length));
    const nextHistory = moveHistory.slice(0, safe);
    const next = new Chess(safe === 0 ? initialFen : nextHistory[safe - 1].fen);
    moveCount.current = safe;
    setChess(next); setMoveHistory(nextHistory); setSelected(null); setTargets(new Set()); setSelectedLines([]);
    setEvaluations(previous => previous.filter(point => point.ply <= safe));
    queueMicrotask(() => analyze(next, "global"));
  };

  const reset = () => {
    cancelLocalRequest(); setOutcomeOpen(false); moveCount.current = 0; const next = new Chess(); setChess(next); setInitialFen(startFen); setMoveHistory([]); setEvaluations([]);
    setSelected(null); setTargets(new Set()); setSelectedLines([]); setLastMoveGrade("—"); setLastMoveReview("等待落子，Stockfish 将评价着法质量。");
    setModeState(mode === "setup" ? "local" : mode); queueMicrotask(() => analyze(next, "global"));
  };

  const saveGame = () => {
    const game: SavedGame = { id: crypto.randomUUID(), title: `国际象棋对局 · ${new Date().toLocaleString()}`, savedAt: new Date().toISOString(), initialFen, moves: moveHistory.map(move => move.uci) };
    const next = [game, ...savedGames]; setSavedGames(next); localStorage.setItem(storageKey, JSON.stringify(next)); setNotice("棋局已保存到浏览器本机。");
  };

  const loadGame = (game: SavedGame) => {
    try {
      const next = new Chess(game.initialFen); const entries: MoveEntry[] = [];
      for (const uci of game.moves) { const made = next.move({ from: uci.slice(0, 2) as Square, to: uci.slice(2, 4) as Square, promotion: (uci[4] || "q") as PieceSymbol }); entries.push({ uci, san: made.san, fen: next.fen() }); }
      cancelLocalRequest(); moveCount.current = entries.length; setChess(next); setInitialFen(game.initialFen); setMoveHistory(entries); setEvaluations([]); setRecordOpen(false); setSelected(null); setTargets(new Set());
      queueMicrotask(() => analyze(next, "global"));
    } catch (error) { setNotice(`棋谱无法载入：${(error as Error).message}`); }
  };

  const importFen = () => {
    try {
      const next = new Chess(fenText.trim()); cancelLocalRequest(); moveCount.current = 0; setChess(next); setInitialFen(next.fen()); setMoveHistory([]); setEvaluations([]); setRecordOpen(false);
      setSelected(null); setTargets(new Set()); setModeState("local"); queueMicrotask(() => analyze(next, "global"));
    } catch (error) { setNotice(`FEN 无效：${(error as Error).message}`); }
  };

  const arrowGeometry = (move: string) => {
    const decode = (square: string) => ({ file: square.charCodeAt(0) - 97, rank: 8 - Number(square[1]) });
    const from = decode(move.slice(0, 2)), to = decode(move.slice(2, 4));
    const ff = flipped ? 7 - from.file : from.file, fr = flipped ? 7 - from.rank : from.rank;
    const tf = flipped ? 7 - to.file : to.file, tr = flipped ? 7 - to.rank : to.rank;
    const x1 = (ff + .5) * 12.5, y1 = (fr + .5) * 12.5, x2 = (tf + .5) * 12.5, y2 = (tr + .5) * 12.5;
    return { x1, y1, x2, y2, labelX: x1 + (x2 - x1) * .34, labelY: y1 + (y2 - y1) * .34 };
  };

  return <main>
    <header><picture><source srcSet="/favicon-dark.png" media="(prefers-color-scheme: dark)" /><img className="logo" src="/favicon-light.png" alt="兵升变应用图标" /></picture><div className="brand"><h1>弈思</h1><p>国际象棋教练</p></div><span className="status">{status}</span></header>
    <div className="layout"><section className="play">
      <div className="mode"><button className={mode === "local" ? "active" : ""} onClick={() => changeMode("local")}>双人对弈</button><button className={mode === "computer" ? "active" : ""} onClick={() => changeMode("computer")}>人机对战</button><button className={mode === "setup" ? "active" : ""} onClick={() => changeMode("setup")}>摆盘</button></div>
      <div className="toolbar"><strong>{mode === "setup" ? "摆盘模式" : `${chess.turn() === "w" ? "白方" : "黑方"}走棋`}</strong><span /><button className={showArrows ? "round active" : "round"} onClick={() => setShowArrows(value => !value)} aria-label={showArrows ? "隐藏候选箭头" : "显示候选箭头"}>优</button><button onClick={() => setFlipped(value => !value)}>⇅ 翻转</button><button disabled={!moveHistory.length || mode === "setup"} onClick={() => goToPly(moveHistory.length - 1)}>↩ 悔棋</button><button onClick={reset}>↻ 重开</button></div>
      {mode === "setup" && <div className="setup-tools"><div><button className={setupTool === "move" ? "active" : ""} onClick={() => setSetupTool("move")}>移动</button><button className={setupTool === "erase" ? "active" : ""} onClick={() => setSetupTool("erase")}>删除</button><label>走棋方 <select value={setupTurn} onChange={event => setSetupTurn(event.target.value as Color)}><option value="w">白方</option><option value="b">黑方</option></select></label><button className="finish" onClick={() => finishSetup()}>完成摆盘</button></div>{(["w", "b"] as Color[]).map(color => <div className="setup-pieces" key={color}><small>{color === "w" ? "白方" : "黑方"}</small>{pieceTypes.map(type => <button key={type} className={setupTool === `${color}${type}` ? "active piece-tool" : "piece-tool"} onClick={() => setSetupTool(`${color}${type}`)}>{symbols[`${color}${type}`]}</button>)}</div>)}</div>}
      <div className="board-shell"><div className="board">{ranks.flatMap((rank, ri) => files.map((file, fi) => {
        const square = `${file}${rank}` as Square;
        const piece = mode === "setup" ? setupPosition[square] : chess.get(square);
        const tone = (ri + fi) % 2 ? "dark" : "light";
        const isLast = lastMove && (lastMove.slice(0, 2) === square || lastMove.slice(2, 4) === square);
        return <button key={square} className={`square ${tone} ${selected === square ? "selected" : ""} ${targets.has(square) ? "target" : ""} ${isLast ? "last" : ""}`} onClick={() => clickSquare(square)} aria-label={square}>{piece && <span className={`piece ${piece.color}`}>{symbols[piece.color + piece.type]}</span>}{fi === 0 && <small className="rank-label">{rank}</small>}{ri === 7 && <small className="file-label">{file}</small>}</button>;
      }))}</div>{showArrows && lines.length > 0 && <svg className="candidate-arrows" viewBox="0 0 100 100" aria-hidden="true"><defs>{arrowColors.map((color, index) => <marker key={color} id={`head-${index}`} markerWidth="4" markerHeight="4" refX="3.2" refY="2" orient="auto"><path d="M0,0 L4,2 L0,4 Z" fill={color} /></marker>)}</defs>{lines.slice(0, 4).map((line, index) => { const move = line.pv.split(" ")[0], point = arrowGeometry(move), color = arrowColors[index]; return <g key={`${move}-${index}`}><line x1={point.x1} y1={point.y1} x2={point.x2} y2={point.y2} stroke={color} strokeWidth="1.2" strokeLinecap="round" markerEnd={`url(#head-${index})`} opacity=".82" /><circle cx={point.labelX} cy={point.labelY} r="2.7" fill={color} stroke="white" strokeWidth=".45" /><text x={point.labelX} y={point.labelY} className="arrow-number">{index + 1}</text></g>; })}</svg>}</div>
    </section><aside>
      <Module title="教练分析" open><div className="review"><b>{moveHistory.length ? `上一步：${lastMoveGrade}` : "开局建议"}</b><span>{moveHistory.length ? lastMoveReview : "先看全局候选，再选择计划。"}</span></div><div className="candidate-heading"><b>{selected ? `${selected.toUpperCase()} 棋子的候选着法` : "全局候选着法"}</b>{selected && <button onClick={() => { setSelected(null); setTargets(new Set()); setSelectedLines([]); analyze(chess, "global"); }}>返回全局</button>}</div><div className="candidates">{visibleLines.length ? visibleLines.map((line, index) => { const move = line.pv.split(" ")[0]; return <button className="candidate" key={`${line.multipv}-${line.pv}`} disabled={!canHumanMove || mode === "setup"} onClick={() => commitMove(move)}><b>{index + 1}</b><span><code>{move.length > 4 ? `${move.slice(0, 4)}=${move[4].toUpperCase()}` : move}</code><small>{line.pv.split(" ").slice(0, 5).join("  ")}</small></span><em>{index === 0 ? "最佳" : "候选"}</em><strong>{scoreText(line.score)}</strong><small>d{line.depth}</small></button>; }) : <p>{status.includes("计算") || status.includes("分析") ? "Stockfish 正在计算…" : "暂无候选着法"}</p>}</div></Module>
      <Module title="局势图"><EvaluationChart points={evaluations} /><p>评分以白方视角显示，曲线上方代表白方占优。</p></Module>
      <Module title="棋谱与存档"><div className="record-head"><span><b>{moveHistory.length ? "当前棋谱" : "新对局"}</b><small>{moveHistory.length} 半回合</small></span><button disabled={!moveHistory.length} onClick={() => goToPly(0)}>开局</button><button disabled={!moveHistory.length} onClick={() => goToPly(moveHistory.length - 1)}>上一步</button><button onClick={() => { setFenText(chess.fen()); setRecordOpen(true); }}>载入</button><button className="primary" onClick={saveGame}>保存</button></div><div className="moves">{rows.length ? rows.map((row, index) => <p key={index}><b>{index + 1}.</b>{row.map((move, side) => <button key={move.uci} onClick={() => goToPly(index * 2 + side + 1)}>{move.san}</button>)}</p>) : <p>还没有着法。可以载入 FEN 局面或浏览器本机存档。</p>}</div></Module>
      <Module title="对弈与分析设置"><label>分析深度 {depth}<input type="range" min="8" max="22" value={depth} onChange={event => setDepth(Number(event.target.value))} /></label><label>候选着法 <input type="number" min="1" max="8" value={multiPV} onChange={event => setMultiPV(Math.max(1, Math.min(8, Number(event.target.value))))} /></label>{mode === "computer" && <><label>执棋 <select value={human} onChange={event => { const color = event.target.value as Color; setHuman(color); if (chess.turn() !== color) queueMicrotask(() => analyze(chess, "computer")); }}><option value="w">白方</option><option value="b">黑方</option></select></label><label>电脑等级 <select value={computerElo} onChange={event => setComputerElo(Number(event.target.value))}>{[["业余一级",1320],["业余三级",1500],["业余五级",1700],["业余七级",1900],["业余九级",2100],["专业一级",2300],["专业三级",2500],["专业五级",2700],["专业七级",2900],["专业九级",3100]].map(([name,elo]) => <option key={elo} value={elo}>{name} · Elo {elo}</option>)}</select></label></>}<button onClick={() => analyze(chess, "global")}>应用并重新分析</button><p>电脑等级使用 UCI_LimitStrength 与参考 Elo，并非平台官方段级换算。分析时仍可行棋；局面变化后会立即中断旧任务，并分析新局面。</p></Module>
      <Module title="GUI 与 UCI"><p>内置教练界面直接使用 Stockfish。仓库同时保留标准 UCI 可执行文件，可添加到 Arena、Cute Chess 或 Scid。</p></Module>
    </aside></div><footer>Stockfish · GPL-3.0 · 国际象棋规则由 chess.js 即时执行</footer>
    {recordOpen && <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setRecordOpen(false); }}><section className="record-modal" role="dialog" aria-modal="true" aria-labelledby="record-title"><header><div><h2 id="record-title">载入棋谱或局面</h2><p>粘贴 FEN，或选择浏览器本机存档。</p></div><button onClick={() => setRecordOpen(false)}>完成</button></header><label>FEN 局面<textarea value={fenText} onChange={event => setFenText(event.target.value)} /></label><button className="primary wide" disabled={!fenText.trim()} onClick={importFen}>载入此局面</button><h3>本机存档</h3><div className="saved-games">{savedGames.length ? savedGames.map(game => <button key={game.id} onClick={() => loadGame(game)}><b>{game.title}</b><span>{game.moves.length} 半回合 · {new Date(game.savedAt).toLocaleString()}</span></button>) : <p>还没有保存的棋局。</p>}</div></section></div>}
    {outcome && outcomeOpen && <div className="modal-backdrop" role="presentation"><section className="result-modal" role="alertdialog" aria-modal="true" aria-labelledby="result-title"><div className="result-mark">✓</div><h2 id="result-title">{outcome.title}</h2><p>{outcome.detail}</p><div><button className="primary" onClick={reset}>再来一局</button><button onClick={() => setOutcomeOpen(false)}>查看棋局</button></div></section></div>}
    {notice && <div className="notice" role="alert"><span>{notice}</span><button onClick={() => setNotice(null)}>好</button></div>}
  </main>;
}
