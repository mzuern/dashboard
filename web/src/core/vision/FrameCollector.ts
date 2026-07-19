import type { MotionSample } from './MotionTracker';

export interface KeyFrame {
  canvas: HTMLCanvasElement;
  /** Accumulated camera-center position at capture time, in MotionTracker's downsampled-px units. */
  camX: number;
  camY: number;
  capturedAt: number;
}

export interface FrameCollectorOptions {
  /** MotionTracker's downsample width - keyframe spacing is expressed relative to it. */
  sampleWidth: number;
  minSharpness: number;
  minTrackingConfidence: number;
  maxFrames: number;
  /** Max working width for stored keyframes (kept modest for stitching perf). */
  workingWidth: number;
}

const DEFAULTS: Omit<FrameCollectorOptions, 'sampleWidth'> = {
  minSharpness: 25,
  minTrackingConfidence: 0.25,
  maxFrames: 140,
  workingWidth: 1280,
};

/**
 * Decides which live frames are worth keeping for stitching. Keyframes are
 * spaced so consecutive frames overlap roughly 40-70% - enough for reliable
 * feature matching without collecting (and having to stitch) far more
 * frames than necessary.
 */
export class FrameCollector {
  private readonly opts: FrameCollectorOptions;
  private frames: KeyFrame[] = [];
  private lastKeyframePos: { x: number; y: number } | null = null;
  private camX = 0;
  private camY = 0;
  private distanceSinceKeyframe = 0;

  constructor(opts: Partial<FrameCollectorOptions> & { sampleWidth: number }) {
    this.opts = { ...DEFAULTS, ...opts };
  }

  reset(): void {
    this.frames = [];
    this.lastKeyframePos = null;
    this.camX = 0;
    this.camY = 0;
    this.distanceSinceKeyframe = 0;
  }

  get count(): number {
    return this.frames.length;
  }

  get isFull(): boolean {
    return this.frames.length >= this.opts.maxFrames;
  }

  get keyframes(): readonly KeyFrame[] {
    return this.frames;
  }

  /**
   * Considers the current live frame for capture. Returns 'captured',
   * 'skipped-quality' (blurry/tracking lost - not counted toward overlap),
   * or 'skipped-spacing' (too close to the previous keyframe).
   */
  consider(sourceCanvas: HTMLCanvasElement, sample: MotionSample): 'captured' | 'skipped-quality' | 'skipped-spacing' | 'full' {
    if (this.isFull) return 'full';

    if (sample.hasPrevious) {
      this.camX += sample.dx;
      this.camY += sample.dy;
    }

    const qualityOk = sample.sharpness >= this.opts.minSharpness && sample.trackingConfidence >= this.opts.minTrackingConfidence;

    if (this.frames.length === 0) {
      if (!qualityOk) return 'skipped-quality';
      this.capture(sourceCanvas);
      return 'captured';
    }

    const dxSinceKey = this.camX - (this.lastKeyframePos?.x ?? 0);
    const dySinceKey = this.camY - (this.lastKeyframePos?.y ?? 0);
    this.distanceSinceKeyframe = Math.hypot(dxSinceKey, dySinceKey);

    const minSpacing = this.opts.sampleWidth * 0.3;
    if (this.distanceSinceKeyframe < minSpacing) return 'skipped-spacing';
    if (!qualityOk) return 'skipped-quality';

    this.capture(sourceCanvas);
    return 'captured';
  }

  /** How far (in sampleWidth units) we've drifted past ideal spacing without a good capture. */
  get overdueRatio(): number {
    const maxSpacing = this.opts.sampleWidth * 0.75;
    return this.distanceSinceKeyframe / maxSpacing;
  }

  private capture(sourceCanvas: HTMLCanvasElement): void {
    const scale = Math.min(1, this.opts.workingWidth / sourceCanvas.width);
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(sourceCanvas.width * scale);
    canvas.height = Math.round(sourceCanvas.height * scale);
    const ctx = canvas.getContext('2d');
    ctx?.drawImage(sourceCanvas, 0, 0, canvas.width, canvas.height);
    this.frames.push({ canvas, camX: this.camX, camY: this.camY, capturedAt: Date.now() });
    this.lastKeyframePos = { x: this.camX, y: this.camY };
  }
}
