import type { OpenCV } from './opencvLoader';

export function canvasFromMat(cv: OpenCV, mat: InstanceType<OpenCV['Mat']>): HTMLCanvasElement {
  const canvas = document.createElement('canvas');
  cv.imshow(canvas, mat);
  return canvas;
}

export function matFromCanvas(cv: OpenCV, canvas: HTMLCanvasElement): InstanceType<OpenCV['Mat']> {
  return cv.imread(canvas);
}

/** Crops a canvas region into a new canvas, clamping to source bounds. */
export function cropCanvas(source: HTMLCanvasElement, rect: { x: number; y: number; width: number; height: number }): HTMLCanvasElement {
  const x = Math.max(0, Math.round(rect.x));
  const y = Math.max(0, Math.round(rect.y));
  const width = Math.max(1, Math.min(Math.round(rect.width), source.width - x));
  const height = Math.max(1, Math.min(Math.round(rect.height), source.height - y));
  const out = document.createElement('canvas');
  out.width = width;
  out.height = height;
  const ctx = out.getContext('2d');
  ctx?.drawImage(source, x, y, width, height, 0, 0, width, height);
  return out;
}

export function scaleCanvas(source: HTMLCanvasElement, scale: number): HTMLCanvasElement {
  const out = document.createElement('canvas');
  out.width = Math.max(1, Math.round(source.width * scale));
  out.height = Math.max(1, Math.round(source.height * scale));
  const ctx = out.getContext('2d');
  ctx?.drawImage(source, 0, 0, out.width, out.height);
  return out;
}
