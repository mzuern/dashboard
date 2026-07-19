import type { OpenCV } from './opencvLoader';
import { canvasFromMat } from './imageUtils';

export interface Corner {
  x: number;
  y: number;
}

export type Quad = [Corner, Corner, Corner, Corner]; // top-left, top-right, bottom-right, bottom-left

export interface DetectionResult {
  quad: Quad | null;
  /** Fraction of the source image area the detected quad covers - a rough confidence signal. */
  areaRatio: number;
}

/**
 * Finds the whiteboard's boundary as a quadrilateral, so the stitched
 * mosaic (which still has camera-sweep skew and any residual perspective)
 * can be warped into a flat, document-scan-style rectangle. Detection
 * uses the standard "scanner app" recipe: edges, dilate to close gaps,
 * find the largest 4-point contour. When it isn't confident, the caller
 * should fall back to asking the user to drag the corners manually -
 * see `warpToRectangle`, which accepts either an auto-detected or a
 * user-adjusted quad.
 */
export class PerspectiveCorrector {
  private readonly cv: OpenCV;

  constructor(cv: OpenCV) {
    this.cv = cv;
  }

  detectBoardQuad(source: HTMLCanvasElement): DetectionResult {
    const cv = this.cv;
    const src = cv.imread(source);
    const gray = new cv.Mat();
    const blurred = new cv.Mat();
    const edges = new cv.Mat();
    const dilated = new cv.Mat();
    const contours = new cv.MatVector();
    const hierarchy = new cv.Mat();

    try {
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
      cv.GaussianBlur(gray, blurred, new cv.Size(5, 5), 0);
      cv.Canny(blurred, edges, 50, 150);
      const kernel = cv.Mat.ones(5, 5, cv.CV_8U);
      cv.dilate(edges, dilated, kernel);
      kernel.delete();

      cv.findContours(dilated, contours, hierarchy, cv.RETR_LIST, cv.CHAIN_APPROX_SIMPLE);

      const imageArea = src.cols * src.rows;
      let best: Quad | null = null;
      let bestArea = 0;

      for (let i = 0; i < contours.size(); i++) {
        const contour = contours.get(i);
        const area = cv.contourArea(contour);
        if (area < imageArea * 0.15 || area <= bestArea) {
          contour.delete();
          continue;
        }
        const peri = cv.arcLength(contour, true);
        const approx = new cv.Mat();
        cv.approxPolyDP(contour, approx, 0.02 * peri, true);
        if (approx.rows === 4 && cv.isContourConvex(approx)) {
          const pts: Corner[] = [];
          for (let r = 0; r < 4; r++) {
            pts.push({ x: approx.data32S[r * 2], y: approx.data32S[r * 2 + 1] });
          }
          best = orderCorners(pts);
          bestArea = area;
        }
        approx.delete();
        contour.delete();
      }

      return { quad: best, areaRatio: bestArea / imageArea };
    } finally {
      src.delete();
      gray.delete();
      blurred.delete();
      edges.delete();
      dilated.delete();
      contours.delete();
      hierarchy.delete();
    }
  }

  warpToRectangle(source: HTMLCanvasElement, quad: Quad, outWidth: number, outHeight: number): HTMLCanvasElement {
    const cv = this.cv;
    const src = cv.imread(source);
    const srcPts = cv.matFromArray(
      4,
      1,
      cv.CV_32FC2,
      quad.flatMap((c) => [c.x, c.y]),
    );
    const dstPts = cv.matFromArray(4, 1, cv.CV_32FC2, [0, 0, outWidth, 0, outWidth, outHeight, 0, outHeight]);
    const M = cv.getPerspectiveTransform(srcPts, dstPts);
    const dst = new cv.Mat();
    try {
      cv.warpPerspective(src, dst, M, new cv.Size(outWidth, outHeight));
      return canvasFromMat(cv, dst);
    } finally {
      src.delete();
      srcPts.delete();
      dstPts.delete();
      M.delete();
      dst.delete();
    }
  }
}

function orderCorners(pts: Corner[]): Quad {
  const sum = pts.map((p) => p.x + p.y);
  const diff = pts.map((p) => p.x - p.y);
  const tl = pts[sum.indexOf(Math.min(...sum))];
  const br = pts[sum.indexOf(Math.max(...sum))];
  const tr = pts[diff.indexOf(Math.max(...diff))];
  const bl = pts[diff.indexOf(Math.min(...diff))];
  return [tl, tr, br, bl];
}

export function defaultQuad(width: number, height: number, insetPct = 0.03): Quad {
  const ix = width * insetPct;
  const iy = height * insetPct;
  return [
    { x: ix, y: iy },
    { x: width - ix, y: iy },
    { x: width - ix, y: height - iy },
    { x: ix, y: height - iy },
  ];
}
