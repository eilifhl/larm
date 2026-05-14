import { cp, mkdir, rm, stat } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const distDir = path.join(root, "dist");
const webDir = path.join(root, "web");
const wasmFile = path.join(
  root,
  "rust",
  "target",
  "wasm32-unknown-unknown",
  "release",
  "larm.wasm",
);

await stat(wasmFile).catch(() => {
  throw new Error(
    "Missing rust/target/wasm32-unknown-unknown/release/larm.wasm. Run `npm run build:wasm` first.",
  );
});

await rm(distDir, { recursive: true, force: true });
await mkdir(distDir, { recursive: true });
await cp(webDir, distDir, { recursive: true });
await cp(wasmFile, path.join(distDir, "larm.wasm"));
