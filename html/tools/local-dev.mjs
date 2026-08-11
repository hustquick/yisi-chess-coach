import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const binary = join(root, ".local", "stockfish", "stockfish");
if (!existsSync(binary)) {
  console.error("Stockfish is not installed. Run: npm run engine:setup");
  process.exit(1);
}
const children = [
  spawn(process.execPath, [join(root, "tools", "stockfish-server.mjs")], { cwd: root, stdio: "inherit" }),
  spawn("npm", ["run", "dev"], { cwd: root, stdio: "inherit" }),
];
function stop() { for (const child of children) child.kill("SIGTERM"); }
process.on("SIGINT", stop); process.on("SIGTERM", stop);
await Promise.all(children.map(child => new Promise(resolve => child.on("exit", resolve))));
