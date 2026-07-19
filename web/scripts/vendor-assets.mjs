#!/usr/bin/env node
/**
 * Copies OCR runtime assets (Tesseract.js worker, WASM core, and English
 * language data) from node_modules into public/vendor so they are served as
 * same-origin static files. This keeps OCR fully local/offline: no CDN is
 * ever contacted at runtime, and the service worker precaches these files
 * for offline use after the first load.
 */
import { copyFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '..');
const nodeModules = join(root, 'node_modules');
const publicDir = join(root, 'public');

const copies = [
  // Tesseract.js worker script (spawned as a Worker at runtime).
  ['tesseract.js/dist/worker.min.js', 'vendor/tesseract/worker.min.js'],
  // WASM core: both SIMD and non-SIMD LSTM-only builds, so the worker can
  // pick the right one for the device via feature detection.
  ['tesseract.js-core/tesseract-core-simd-lstm.wasm.js', 'vendor/tesseract/tesseract-core-simd-lstm.wasm.js'],
  ['tesseract.js-core/tesseract-core-simd-lstm.wasm', 'vendor/tesseract/tesseract-core-simd-lstm.wasm'],
  ['tesseract.js-core/tesseract-core-lstm.wasm.js', 'vendor/tesseract/tesseract-core-lstm.wasm.js'],
  ['tesseract.js-core/tesseract-core-lstm.wasm', 'vendor/tesseract/tesseract-core-lstm.wasm'],
  // English trained data (LSTM "best_int" variant, matches lstmOnly core).
  ['@tesseract.js-data/eng/4.0.0_best_int/eng.traineddata.gz', 'vendor/tessdata/eng.traineddata.gz'],
];

let copied = 0;
for (const [src, dest] of copies) {
  const srcPath = join(nodeModules, src);
  const destPath = join(publicDir, dest);
  if (!existsSync(srcPath)) {
    console.error(`[vendor-assets] missing source: ${src} (did npm install run?)`);
    process.exitCode = 1;
    continue;
  }
  mkdirSync(dirname(destPath), { recursive: true });
  copyFileSync(srcPath, destPath);
  copied += 1;
}

console.log(`[vendor-assets] copied ${copied}/${copies.length} files into public/vendor`);
