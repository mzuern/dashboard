import { CameraManager } from '../core/camera/CameraManager';
import { MotionTracker, SAMPLE_WIDTH, type MotionSample } from '../core/vision/MotionTracker';
import { CoverageTracker, type CoverageSnapshot } from '../core/vision/CoverageTracker';
import { FrameCollector } from '../core/vision/FrameCollector';
import { ImageStitcher, type StitchDebugInfo } from '../core/vision/ImageStitcher';
import { PerspectiveCorrector, defaultQuad, type Quad } from '../core/vision/PerspectiveCorrector';
import { BoardDetector } from '../core/board/BoardDetector';
import { RegionExtractor } from '../core/board/RegionExtractor';
import { TesseractOCRProvider } from '../core/ocr/TesseractOCRProvider';
import { FIELD_WHITELISTS, buildCustomerField, buildDaysRemainingField, buildProjectNumberField, isFieldOk } from '../core/ocr/validation';
import { loadOpenCV, type OpenCV } from '../core/vision/opencvLoader';
import type { AppSettings, FieldKey, ReviewRow, RowRect, ScanIssue, ScanPhase } from '../types/domain';

const MIN_SHARPNESS = 25;
const MIN_TRACKING_CONFIDENCE = 0.25;
const SPEED_FAST_THRESHOLD = SAMPLE_WIDTH * 0.28; // per-tick shift that's "too fast"
const LOW_LIGHT_THRESHOLD = 55; // mean brightness 0-255
const GLARE_THRESHOLD = 0.12; // fraction of saturated pixels
const LOST_THRESHOLD = 0.12;
const MIN_FRAMES_BEFORE_AUTOSTOP = 8;
const MIN_SCAN_MS_BEFORE_AUTOSTOP = 4000;
const TICK_MS = 160;

export interface ScanState {
  phase: ScanPhase;
  engineReady: boolean;
  coverageFraction: number;
  coverageSnapshot: CoverageSnapshot | null;
  guidance: string | null;
  activeIssue: ScanIssue | null;
  issueLog: ScanIssue[];
  frameCount: number;
  elapsedMs: number;
  processingProgress: number;
  processingLabel: string;
  mosaicCanvas?: HTMLCanvasElement;
  quad?: Quad;
  boardCanvas?: HTMLCanvasElement;
  rows?: RowRect[];
  reviewRows?: ReviewRow[];
  stitchDebug?: StitchDebugInfo[];
  quadAreaRatio?: number;
  error?: string;
}

type Listener = (state: ScanState) => void;

/**
 * Orchestrates the full pipeline: live camera + motion/coverage tracking
 * during scanning, then (once the user or auto-stop ends the scan)
 * stitching, perspective correction, row detection, region extraction,
 * and OCR - handing back review-ready rows. This is the only module that
 * knows the *order* of the other modules; each of them stays independently
 * testable/replaceable.
 */
export class ScanPipeline {
  private cv: OpenCV | null = null;
  private camera = new CameraManager();
  private motion: MotionTracker | null = null;
  private coverage = new CoverageTracker();
  private frames: FrameCollector | null = null;
  private ocr = new TesseractOCRProvider();
  private timer: ReturnType<typeof setInterval> | null = null;
  private canvas = document.createElement('canvas');
  private listeners = new Set<Listener>();
  private scanStartedAt = 0;
  private issueLog: ScanIssue[] = [];
  private disposed = false;

  private state: ScanState = {
    phase: 'idle',
    engineReady: false,
    coverageFraction: 0,
    coverageSnapshot: null,
    guidance: null,
    activeIssue: null,
    issueLog: [],
    frameCount: 0,
    elapsedMs: 0,
    processingProgress: 0,
    processingLabel: '',
  };

  private settings: AppSettings;

  constructor(settings: AppSettings) {
    this.settings = settings;
  }

  updateSettings(settings: AppSettings): void {
    this.settings = settings;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => this.listeners.delete(listener);
  }

  private emit(patch: Partial<ScanState>): void {
    this.state = { ...this.state, ...patch };
    for (const listener of this.listeners) listener(this.state);
  }

  get video(): HTMLVideoElement {
    return this.camera.video;
  }

  /** Kicks off the (slow, ~10MB) OpenCV.js load early, e.g. when the scan screen mounts. */
  async preload(): Promise<void> {
    if (this.cv) return;
    this.cv = await loadOpenCV();
    if (this.disposed) return;
    this.emit({ engineReady: true });
  }

  async startScanning(): Promise<void> {
    await this.preload();
    if (!this.cv) throw new Error('OpenCV failed to load.');

    try {
      await this.camera.start(this.settings.cameraResolution);
    } catch (err) {
      this.emit({ error: err instanceof Error ? err.message : 'Could not access the camera.' });
      throw err;
    }

    this.motion = new MotionTracker(this.cv, this.camera.width / this.camera.height);
    this.coverage.reset();
    this.frames = new FrameCollector({ sampleWidth: SAMPLE_WIDTH });
    this.issueLog = [];
    this.scanStartedAt = Date.now();

    this.emit({
      phase: 'scanning',
      coverageFraction: 0,
      coverageSnapshot: null,
      guidance: 'Slowly sweep across the board',
      activeIssue: null,
      issueLog: [],
      frameCount: 0,
      elapsedMs: 0,
      error: undefined,
    });

    this.timer = setInterval(() => this.tick(), TICK_MS);
  }

  private tick(): void {
    if (!this.motion || !this.frames) return;
    const ctx = this.camera.grabFrame(this.canvas);
    if (!ctx) return;

    const sample = this.motion.analyze(this.canvas);
    const qualityOk = sample.sharpness >= MIN_SHARPNESS && sample.trackingConfidence >= MIN_TRACKING_CONFIDENCE;
    this.coverage.update(sample, qualityOk);
    this.frames.consider(this.canvas, sample);

    const issue = this.classifyIssue(sample);
    if (issue) this.issueLog = [...this.issueLog.slice(-19), issue];

    const snapshot = this.coverage.snapshot();
    const elapsedMs = Date.now() - this.scanStartedAt;

    this.emit({
      coverageFraction: snapshot.coverageFraction,
      coverageSnapshot: snapshot,
      guidance: issue?.message ?? 'Board Coverage',
      activeIssue: issue,
      issueLog: this.issueLog,
      frameCount: this.frames.count,
      elapsedMs,
    });

    const readyToAutostop =
      snapshot.coverageFraction >= this.settings.coverageThreshold &&
      this.frames.count >= MIN_FRAMES_BEFORE_AUTOSTOP &&
      elapsedMs >= MIN_SCAN_MS_BEFORE_AUTOSTOP;

    if (readyToAutostop || this.frames.isFull) {
      void this.requestStop();
    }
  }

  private classifyIssue(sample: MotionSample): ScanIssue | null {
    const now = Date.now();
    if (sample.hasPrevious && sample.trackingConfidence < LOST_THRESHOLD) {
      return { code: 'tracking_lost', message: 'Move back to the highlighted area', recoverable: true, timestamp: now };
    }
    if (sample.speed > SPEED_FAST_THRESHOLD) {
      return { code: 'motion_too_fast', message: 'Move slower', recoverable: true, timestamp: now };
    }
    if (sample.sharpness < MIN_SHARPNESS) {
      return { code: 'blur', message: 'Hold steady', recoverable: true, timestamp: now };
    }
    if (sample.brightness < LOW_LIGHT_THRESHOLD) {
      return { code: 'low_light', message: 'Poor lighting - move to a brighter area', recoverable: true, timestamp: now };
    }
    if (sample.glareRatio > GLARE_THRESHOLD) {
      return { code: 'glare', message: 'Glare detected - adjust angle', recoverable: true, timestamp: now };
    }
    if (this.frames && this.frames.count > 0 && this.frames.overdueRatio > 1.4) {
      return { code: 'insufficient_overlap', message: 'Slow down to re-cover the last section', recoverable: true, timestamp: now };
    }
    return null;
  }

  /** Manual or auto stop. If coverage is short of threshold, pauses for user confirmation. */
  async requestStop(): Promise<void> {
    if (this.state.phase !== 'scanning') return;
    const snapshot = this.coverage.snapshot();
    if (snapshot.coverageFraction < this.settings.coverageThreshold) {
      this.pauseLoop();
      this.emit({
        phase: 'confirmIncomplete',
        activeIssue: {
          code: 'incomplete_scan',
          message: `Only ${Math.round(snapshot.coverageFraction * 100)}% of the board looks covered.`,
          recoverable: true,
          timestamp: Date.now(),
        },
      });
      return;
    }
    await this.beginProcessing();
  }

  resumeScanning(): void {
    if (this.state.phase !== 'confirmIncomplete') return;
    this.emit({ phase: 'scanning', activeIssue: null });
    this.timer = setInterval(() => this.tick(), TICK_MS);
  }

  async forceProcessAnyway(): Promise<void> {
    if (this.state.phase !== 'confirmIncomplete') return;
    await this.beginProcessing();
  }

  private pauseLoop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private async beginProcessing(): Promise<void> {
    this.pauseLoop();
    this.camera.stop();
    if (!this.cv || !this.frames) return;
    const cv = this.cv;

    if (this.frames.count === 0) {
      this.emit({ phase: 'idle', error: 'No usable frames were captured. Try scanning again, moving more slowly.' });
      return;
    }

    try {
      this.emit({ phase: 'stitching', processingProgress: 0, processingLabel: 'Stitching frames...' });
      const workingWidth = this.frames.keyframes[0].canvas.width;
      const stitcher = new ImageStitcher(cv, workingWidth / SAMPLE_WIDTH);
      const { canvas: mosaicCanvas, debug: stitchDebug } = await stitcher.stitch(this.frames.keyframes, (done, total) => {
        this.emit({ processingProgress: done / total });
      });

      const corrector = new PerspectiveCorrector(cv);
      const detection = corrector.detectBoardQuad(mosaicCanvas);
      const quad = detection.quad ?? defaultQuad(mosaicCanvas.width, mosaicCanvas.height);

      this.emit({
        phase: 'adjustCorners',
        mosaicCanvas,
        quad,
        quadAreaRatio: detection.areaRatio,
        stitchDebug,
        processingProgress: 1,
      });
    } catch (err) {
      this.emit({ phase: 'idle', error: err instanceof Error ? err.message : 'Stitching failed.' });
    }
  }

  /** Called while the user drags a corner handle, to keep the overlay in sync. */
  updateQuad(quad: Quad): void {
    if (this.state.phase !== 'adjustCorners') return;
    this.emit({ quad });
  }

  /** Called by the UI once the user confirms (or adjusts) the detected board corners. */
  async confirmCorners(quad: Quad): Promise<void> {
    if (!this.cv || this.state.phase !== 'adjustCorners' || !this.state.mosaicCanvas) return;
    const cv = this.cv;
    const template = this.settings.boardTemplate;

    this.emit({ phase: 'correcting', processingProgress: 0, processingLabel: 'Correcting perspective...' });
    const corrector = new PerspectiveCorrector(cv);
    const boardCanvas = corrector.warpToRectangle(this.state.mosaicCanvas, quad, template.boardWidthPx, template.boardHeightPx);

    this.emit({ phase: 'detecting', boardCanvas, processingProgress: 0, processingLabel: 'Detecting rows...' });
    const detector = new BoardDetector(cv);
    const rows = detector.detectRows(boardCanvas, template);

    this.emit({ rows, phase: 'ocr', processingProgress: 0, processingLabel: 'Reading project rows...' });
    const reviewRows = await this.runOcr(boardCanvas, rows);

    this.emit({ phase: 'review', reviewRows, processingProgress: 1 });
  }

  private async runOcr(boardCanvas: HTMLCanvasElement, rows: RowRect[]): Promise<ReviewRow[]> {
    await this.ocr.initialize();
    const extractor = new RegionExtractor(this.settings.boardTemplate);
    const results: ReviewRow[] = [];
    const total = Math.max(1, rows.length);

    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const regions = extractor.extractRow(boardCanvas, row);
      const byField = new Map<FieldKey, { text: string; confidence: number }>();
      const debugCrops: Partial<Record<FieldKey, string>> = {};

      for (const region of regions) {
        const result = await this.ocr.recognize(region.canvas, { whitelist: FIELD_WHITELISTS[region.field] });
        byField.set(region.field, result);
        if (this.settings.debugMode) debugCrops[region.field] = region.canvas.toDataURL('image/png');
      }

      const projectNumber = buildProjectNumberField(byField.get('projectNumber')!.text, byField.get('projectNumber')!.confidence);
      const customer = buildCustomerField(byField.get('customer')!.text, byField.get('customer')!.confidence);
      const daysRemaining = buildDaysRemainingField(byField.get('daysRemaining')!.text, byField.get('daysRemaining')!.confidence);
      const threshold = this.settings.ocrConfidenceThreshold;
      const needsReview = !isFieldOk(projectNumber, threshold) || !isFieldOk(customer, threshold) || !isFieldOk(daysRemaining, threshold);

      results.push({
        id: `row-${row.index}-${Date.now()}`,
        rowIndex: row.index,
        projectNumber,
        customer,
        daysRemaining,
        verified: !needsReview,
        needsReview,
        debugCrops: this.settings.debugMode ? debugCrops : undefined,
      });

      this.emit({ processingProgress: (i + 1) / total });
    }

    await this.ocr.terminate();
    return results.filter((r) => r.projectNumber.value.length > 0 || r.customer.value.length > 0);
  }

  cancelScan(): void {
    this.pauseLoop();
    this.camera.stop();
    this.motion?.destroy();
    this.motion = null;
    this.emit({
      phase: 'idle',
      coverageFraction: 0,
      coverageSnapshot: null,
      guidance: null,
      activeIssue: null,
    });
  }

  dispose(): void {
    this.disposed = true;
    this.pauseLoop();
    this.camera.stop();
    this.motion?.destroy();
    void this.ocr.terminate();
    this.listeners.clear();
  }

  getState(): ScanState {
    return this.state;
  }
}
