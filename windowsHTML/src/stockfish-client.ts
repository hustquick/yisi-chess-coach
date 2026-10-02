export type BrowserEngineLine = { depth: number; multipv: number; score: string; pv: string };

type SearchOptions = {
  fen: string;
  depth: number;
  multiPV: number;
  searchMoves?: string[];
  elo?: number;
};

type SearchRequest = {
  id: number;
  options: SearchOptions;
  lines: Map<number, BrowserEngineLine>;
  resolve: (lines: BrowserEngineLine[]) => void;
  reject: (reason?: unknown) => void;
  signal?: AbortSignal;
  onAbort?: () => void;
};

declare global {
  interface Window {
    __YISI_STOCKFISH_WASM_BASE64__?: string;
  }
}

function abortError() {
  return new DOMException("Stockfish search was superseded", "AbortError");
}

function createOfflineStockfishWorker() {
  const wasmBase64 = window.__YISI_STOCKFISH_WASM_BASE64__;
  const engineScript = document.getElementById("stockfish-engine") as (HTMLScriptElement & { _exports?: (options?: Record<string, unknown>) => Promise<unknown> }) | null;
  const factory = engineScript?._exports;
  if (typeof factory !== "function" || !wasmBase64) throw new Error("找不到随软件提供的 Stockfish 离线引擎数据");

  const source = String.raw`
var je;
const Stockfish = ${factory.toString()};
const encodedWasm = ${JSON.stringify(wasmBase64)};
const binary = atob(encodedWasm);
const wasmBinary = new Uint8Array(binary.length);
for (let index = 0; index < binary.length; index += 1) wasmBinary[index] = binary.charCodeAt(index);

let engine = null;
const waiting = [];
const commandQueue = [];

function execute(command) {
  engine.ccall("command", null, ["string"], [command], { async: /^go\b/.test(command) });
  if (command === "quit") self.close();
}

function drain() {
  while (commandQueue.length && (!engine._isSearching || !engine._isSearching())) execute(commandQueue.shift());
}

function send(command) {
  command = String(command).trim();
  if (!command) return;
  if (!engine) { waiting.push(command); return; }
  if (/^(go\b|setoption\b)/.test(command)) commandQueue.push(command);
  else execute(command);
  drain();
}

self.onmessage = event => send(event.data);

Stockfish({
  wasmBinary,
  listener(line) { self.postMessage(String(line)); },
  print(line) { self.postMessage(String(line)); },
  printErr(line) { self.postMessage(String(line)); },
}).then(module => {
  engine = module;
  engine.onDoneSearching = () => setTimeout(drain, 1);
  while (waiting.length) send(waiting.shift());
}).catch(error => {
  setTimeout(() => { throw error; });
});
`;
  const workerUrl = URL.createObjectURL(new Blob([source], { type: "text/javascript" }));
  const worker = new Worker(workerUrl);
  worker.addEventListener("error", () => {
    URL.revokeObjectURL(workerUrl);
  }, { once: true });
  return worker;
}

class StockfishBrowserEngine {
  private worker = createOfflineStockfishWorker();
  private initialized = false;
  private active: SearchRequest | null = null;
  private pending: SearchRequest | null = null;
  private nextId = 0;
  private uciTimer: number;

  constructor() {
    this.worker.addEventListener("message", event => this.onMessage(String(event.data)));
    this.worker.addEventListener("error", event => {
      const error = new Error(event.message || "Stockfish Web Worker failed");
      this.finish(this.active, error);
      this.finish(this.pending, error);
      this.active = null;
      this.pending = null;
    });
    const requestUci = () => this.worker.postMessage("uci");
    requestUci();
    this.uciTimer = window.setInterval(requestUci, 500);
  }

  analyze(options: SearchOptions, signal?: AbortSignal) {
    return new Promise<BrowserEngineLine[]>((resolve, reject) => {
      const request: SearchRequest = { id: ++this.nextId, options, lines: new Map(), resolve, reject, signal };
      if (signal?.aborted) { reject(abortError()); return; }
      request.onAbort = () => this.cancel(request);
      signal?.addEventListener("abort", request.onAbort, { once: true });

      if (this.pending) this.finish(this.pending, abortError());
      this.pending = request;
      if (this.active) {
        this.finish(this.active, abortError());
        this.worker.postMessage("stop");
      } else if (this.initialized) {
        this.startPending();
      }
    });
  }

  stop() {
    if (this.pending) this.finish(this.pending, abortError());
    this.pending = null;
    if (this.active) {
      this.finish(this.active, abortError());
      this.worker.postMessage("stop");
    }
  }

  private cancel(request: SearchRequest) {
    if (this.pending === request) {
      this.pending = null;
      this.finish(request, abortError());
    }
    if (this.active === request) {
      this.finish(request, abortError());
      this.worker.postMessage("stop");
    }
  }

  private startPending() {
    if (this.active || !this.pending) return;
    this.active = this.pending;
    this.pending = null;
    const { multiPV, elo } = this.active.options;
    this.worker.postMessage("setoption name Hash value 32");
    this.worker.postMessage(`setoption name MultiPV value ${Math.max(1, multiPV)}`);
    this.worker.postMessage(`setoption name UCI_LimitStrength value ${elo ? "true" : "false"}`);
    if (elo) this.worker.postMessage(`setoption name UCI_Elo value ${elo}`);
    this.worker.postMessage("isready");
  }

  private onMessage(message: string) {
    if (message === "uciok") {
      window.clearInterval(this.uciTimer);
      this.initialized = true;
      this.startPending();
      return;
    }
    if (message === "readyok" && this.active) {
      const { fen, depth, searchMoves = [] } = this.active.options;
      this.worker.postMessage(`position fen ${fen}`);
      const limitedMoves = searchMoves.length ? ` searchmoves ${searchMoves.join(" ")}` : "";
      this.worker.postMessage(`go depth ${Math.max(1, depth)}${limitedMoves}`);
      return;
    }
    if (message.startsWith("info ") && this.active) {
      const depth = Number(message.match(/\bdepth (\d+)/)?.[1]);
      const multipv = Number(message.match(/\bmultipv (\d+)/)?.[1] ?? 1);
      const score = message.match(/\bscore (cp|mate) (-?\d+)/);
      const pv = message.match(/\bpv (.+)$/)?.[1];
      if (Number.isFinite(depth) && score && pv) {
        const previous = this.active.lines.get(multipv);
        if (!previous || depth >= previous.depth) this.active.lines.set(multipv, { depth, multipv, score: `${score[1]} ${score[2]}`, pv });
      }
      return;
    }
    if (message.startsWith("bestmove") && this.active) {
      const completed = this.active;
      this.active = null;
      if (completed.signal?.aborted) this.finish(completed, abortError());
      else this.finish(completed, null, [...completed.lines.values()].sort((a, b) => a.multipv - b.multipv));
      this.startPending();
    }
  }

  private finish(request: SearchRequest | null, error?: unknown, lines?: BrowserEngineLine[]) {
    if (!request) return;
    request.signal?.removeEventListener("abort", request.onAbort!);
    if (error) request.reject(error); else request.resolve(lines ?? []);
  }
}

let engine: StockfishBrowserEngine | null = null;

function browserEngine() {
  engine ??= new StockfishBrowserEngine();
  return engine;
}

export function analyzeWithBrowserStockfish(options: SearchOptions, signal?: AbortSignal) {
  const endpoint = document.querySelector<HTMLMetaElement>('meta[name="yisi-engine-endpoint"]')?.content;
  if (endpoint) {
    const url = new URL(endpoint);
    if (url.protocol !== 'https:' || url.origin !== 'https://141.148.168.171' || url.pathname !== '/chess-engine') throw new Error('云端引擎配置异常');
    return fetch(endpoint + '/analyze', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(options), signal })
      .then(async response => { const value = await response.json(); if (!response.ok) throw new Error(value.error ?? '云端分析失败'); return value as BrowserEngineLine[]; });
  }
  return browserEngine().analyze(options, signal);
}

export function stopBrowserStockfish() {
  engine?.stop();
}
