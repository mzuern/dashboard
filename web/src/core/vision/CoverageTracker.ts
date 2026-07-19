import type { MotionSample } from './MotionTracker';

export interface CoverageCell {
  gx: number;
  gy: number;
  observations: number;
  covered: boolean;
}

export interface CoverageSnapshot {
  cells: CoverageCell[];
  /** Grid bounds, in cell-index space. */
  minGx: number;
  maxGx: number;
  minGy: number;
  maxGy: number;
  /** Current camera-center cell, for cursor rendering. */
  cursor: { gx: number; gy: number };
  coverageFraction: number;
}

const CELL_SIZE = 80; // downsampled-frame px per grid cell (see MotionTracker's SAMPLE_WIDTH)
const FOOTPRINT_RADIUS_CELLS = 2; // how much of the frame around the center counts as "seen"
const REQUIRED_OBSERVATIONS = 2; // overlapping frames needed before a cell turns green

/**
 * Builds a sparse coverage grid from accumulated camera motion. There's no
 * true camera pose/SLAM here - frame-to-frame shifts from MotionTracker are
 * integrated into a running camera-center trajectory, and a footprint
 * around that center is marked "observed" on every good-quality tick. This
 * is the same trick modern phone document scanners use for their coverage
 * heatmap: approximate, but good enough to guide a human sweep.
 */
export class CoverageTracker {
  private cells = new Map<string, CoverageCell>();
  private camX = 0;
  private camY = 0;
  private minGx = 0;
  private maxGx = 0;
  private minGy = 0;
  private maxGy = 0;
  private touched = false;

  update(sample: MotionSample, qualityOk: boolean): void {
    if (sample.hasPrevious) {
      this.camX += sample.dx;
      this.camY += sample.dy;
    }
    const gx = Math.round(this.camX / CELL_SIZE);
    const gy = Math.round(this.camY / CELL_SIZE);

    if (!this.touched) {
      this.minGx = this.maxGx = gx;
      this.minGy = this.maxGy = gy;
      this.touched = true;
    } else {
      this.minGx = Math.min(this.minGx, gx);
      this.maxGx = Math.max(this.maxGx, gx);
      this.minGy = Math.min(this.minGy, gy);
      this.maxGy = Math.max(this.maxGy, gy);
    }

    if (qualityOk) {
      for (let dx = -FOOTPRINT_RADIUS_CELLS; dx <= FOOTPRINT_RADIUS_CELLS; dx++) {
        for (let dy = -FOOTPRINT_RADIUS_CELLS; dy <= FOOTPRINT_RADIUS_CELLS; dy++) {
          const key = `${gx + dx},${gy + dy}`;
          const cell = this.cells.get(key) ?? { gx: gx + dx, gy: gy + dy, observations: 0, covered: false };
          cell.observations += 1;
          cell.covered = cell.observations >= REQUIRED_OBSERVATIONS;
          this.cells.set(key, cell);
        }
      }
    }
  }

  get cursorCell(): { gx: number; gy: number } {
    return { gx: Math.round(this.camX / CELL_SIZE), gy: Math.round(this.camY / CELL_SIZE) };
  }

  snapshot(): CoverageSnapshot {
    const cells = Array.from(this.cells.values());
    const totalCells = Math.max(1, (this.maxGx - this.minGx + 1) * (this.maxGy - this.minGy + 1));
    const coveredCount = cells.filter((c) => c.covered).length;
    return {
      cells,
      minGx: this.minGx,
      maxGx: this.maxGx,
      minGy: this.minGy,
      maxGy: this.maxGy,
      cursor: this.cursorCell,
      coverageFraction: this.touched ? Math.min(1, coveredCount / totalCells) : 0,
    };
  }

  reset(): void {
    this.cells.clear();
    this.camX = 0;
    this.camY = 0;
    this.touched = false;
  }
}
