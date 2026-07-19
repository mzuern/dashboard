import type { OpenCV } from '../vision/opencvLoader';
import type { BoardTemplate, RowRect } from '../../types/domain';
import { computeRowCount } from './BoardTemplate';

/**
 * Locates each project row within the corrected board image. The board
 * layout is fixed and known ahead of time (via BoardTemplate), so this
 * does not attempt general-purpose table detection - it looks for
 * horizontal separator lines near where the template expects them (a
 * row-wise gradient projection, peak-picked) and snaps to those when
 * confident, falling back to pure template math otherwise. This is
 * intentionally cheap: no OCR happens here, only row geometry.
 */
export class BoardDetector {
  private readonly cv: OpenCV;

  constructor(cv: OpenCV) {
    this.cv = cv;
  }

  detectRows(board: HTMLCanvasElement, template: BoardTemplate): RowRect[] {
    const rowCount = computeRowCount(template);
    if (rowCount === 0) return [];

    const lines = this.findHorizontalLines(board);
    const rows: RowRect[] = [];

    for (let i = 0; i < rowCount; i++) {
      const expectedTop = template.marginTopPx + i * template.rowHeightPx;
      const expectedBottom = expectedTop + template.rowHeightPx;
      const snappedTop = snapToLine(lines, expectedTop, template.rowHeightPx * 0.25);
      const snappedBottom = snapToLine(lines, expectedBottom, template.rowHeightPx * 0.25);
      const detected = snappedTop !== null || snappedBottom !== null;
      const top = snappedTop ?? expectedTop;
      const bottom = snappedBottom ?? expectedBottom;
      rows.push({
        index: i,
        rect: { x: 0, y: top, width: template.boardWidthPx, height: Math.max(4, bottom - top) },
        detected,
      });
    }
    return rows;
  }

  /** Row-wise gradient-energy projection; returns y-positions of strong horizontal edges. */
  private findHorizontalLines(board: HTMLCanvasElement): number[] {
    const cv = this.cv;
    const src = cv.imread(board);
    const gray = new cv.Mat();
    const sobel = new cv.Mat();
    try {
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
      cv.Sobel(gray, sobel, cv.CV_32F, 0, 1, 3);
      cv.convertScaleAbs(sobel, sobel);

      const rowEnergy: number[] = new Array(sobel.rows).fill(0);
      const data = sobel.data as Uint8Array;
      const cols = sobel.cols;
      for (let y = 0; y < sobel.rows; y++) {
        let sum = 0;
        const base = y * cols;
        for (let x = 0; x < cols; x++) sum += data[base + x];
        rowEnergy[y] = sum / cols;
      }

      const mean = rowEnergy.reduce((a, b) => a + b, 0) / rowEnergy.length;
      const threshold = mean * 2.2;
      const peaks: number[] = [];
      for (let y = 1; y < rowEnergy.length - 1; y++) {
        if (rowEnergy[y] > threshold && rowEnergy[y] >= rowEnergy[y - 1] && rowEnergy[y] >= rowEnergy[y + 1]) {
          peaks.push(y);
        }
      }
      return peaks;
    } finally {
      src.delete();
      gray.delete();
      sobel.delete();
    }
  }
}

function snapToLine(lines: number[], expected: number, tolerance: number): number | null {
  let best: number | null = null;
  let bestDist = tolerance;
  for (const y of lines) {
    const dist = Math.abs(y - expected);
    if (dist <= bestDist) {
      bestDist = dist;
      best = y;
    }
  }
  return best;
}
