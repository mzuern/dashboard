/** Shared domain types used across scanning, board, OCR, and review modules. */

/** A rectangle expressed as fractions (0-1) of its parent's width/height. */
export interface FractionalRect {
  xPct: number;
  yPct: number;
  wPct: number;
  hPct: number;
}

/** A pixel-space rectangle in some specific image's coordinate system. */
export interface PixelRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** The three fields extracted from every project row, plus row geometry. */
export interface BoardTemplate {
  /** Canonical width/height (px) the corrected board image is warped to. */
  boardWidthPx: number;
  boardHeightPx: number;
  /** Vertical offset before the first row starts. */
  marginTopPx: number;
  /** Height of a single project row, in canonical board px. */
  rowHeightPx: number;
  /** Optional hard cap on rows; otherwise derived from board/row height. */
  rowCount?: number;
  /** Field regions, as fractions of a single row's bounding box. */
  regions: {
    projectNumber: FractionalRect;
    customer: FractionalRect;
    daysRemaining: FractionalRect;
  };
}

export interface RowRect {
  index: number;
  rect: PixelRect;
  /** True when this row's bounds came from detected grid lines, not just template math. */
  detected: boolean;
}

export type FieldKey = 'projectNumber' | 'customer' | 'daysRemaining';

export interface FieldResult {
  /** Raw OCR text, never silently discarded. */
  rawText: string;
  /** Cleaned/normalized value used for display and the email. */
  value: string;
  /** OCR engine confidence, 0-100. */
  confidence: number;
  /** Whether the cleaned value satisfies this field's format rules. */
  formatValid: boolean;
}

export interface ReviewRow {
  id: string;
  rowIndex: number;
  projectNumber: FieldResult;
  customer: FieldResult;
  daysRemaining: FieldResult;
  /** True once the user has explicitly confirmed the row (or it auto-passed). */
  verified: boolean;
  /** True if any field is below the confidence/format bar and needs attention. */
  needsReview: boolean;
  /** Debug: crop thumbnails as data URLs, only kept when debug mode is on. */
  debugCrops?: Partial<Record<FieldKey, string>>;
}

export type ScanPhase =
  'idle' | 'scanning' | 'confirmIncomplete' | 'stitching' | 'adjustCorners' | 'correcting' | 'detecting' | 'ocr' | 'review' | 'email';

export interface ScanIssue {
  code: 'motion_too_fast' | 'blur' | 'low_light' | 'glare' | 'tracking_lost' | 'insufficient_overlap' | 'incomplete_scan';
  message: string;
  recoverable: boolean;
  timestamp: number;
}

export interface CameraResolutionPreset {
  label: string;
  width: number;
  height: number;
}

export interface AppSettings {
  boardTemplate: BoardTemplate;
  ocrConfidenceThreshold: number;
  coverageThreshold: number;
  cameraResolution: CameraResolutionPreset;
  debugMode: boolean;
}
