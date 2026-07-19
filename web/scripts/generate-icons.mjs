#!/usr/bin/env node
/**
 * Generates the PWA's PNG app icons (maskable + apple-touch-icon) without
 * any image-processing dependency: a raw RGBA pixel buffer is built by
 * hand (accent-colored square + a white "scan viewfinder" corner-bracket
 * glyph) and encoded straight into PNG chunks via zlib.
 */
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = join(__dirname, '..', 'public', 'icons');
mkdirSync(outDir, { recursive: true });

const BG = [0x64, 0x6c, 0xff]; // accent purple
const FG = [0xff, 0xff, 0xff];

function buildPixels(size) {
  const px = new Uint8Array(size * size * 4);
  const bracket = Math.round(size * 0.09);
  const inset = Math.round(size * 0.18);
  const thickness = Math.max(2, Math.round(size * 0.055));

  const setPixel = (x, y, color) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    const i = (y * size + x) * 4;
    px[i] = color[0];
    px[i + 1] = color[1];
    px[i + 2] = color[2];
    px[i + 3] = 255;
  };

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) setPixel(x, y, BG);
  }

  const corners = [
    { x: inset, y: inset, dx: 1, dy: 1 },
    { x: size - inset, y: inset, dx: -1, dy: 1 },
    { x: inset, y: size - inset, dx: 1, dy: -1 },
    { x: size - inset, y: size - inset, dx: -1, dy: -1 },
  ];

  for (const c of corners) {
    for (let t = 0; t < thickness; t++) {
      for (let i = 0; i < bracket; i++) {
        setPixel(c.x + c.dx * i, c.y + c.dy * t, FG);
        setPixel(c.x + c.dx * t, c.y + c.dy * i, FG);
      }
    }
  }

  return px;
}

function crc32(buf) {
  let c;
  const table = [];
  for (let n = 0; n < 256; n++) {
    c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const lenBuf = Buffer.alloc(4);
  lenBuf.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function encodePng(size) {
  const pixels = buildPixels(size);
  const stride = size * 4;
  const raw = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y++) {
    raw[y * (stride + 1)] = 0; // filter type: none
    Buffer.from(pixels.buffer, y * stride, stride).copy(raw, y * (stride + 1) + 1);
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const idat = deflateSync(raw);
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

  return Buffer.concat([signature, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

const sizes = [
  { size: 192, name: 'icon-192.png' },
  { size: 512, name: 'icon-512.png' },
  { size: 180, name: 'apple-touch-icon.png' },
];

for (const { size, name } of sizes) {
  writeFileSync(join(outDir, name), encodePng(size));
}

console.log(`[generate-icons] wrote ${sizes.length} icons to public/icons`);
