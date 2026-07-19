export interface OCRRecognizeOptions {
  /** Restrict recognition to these characters, e.g. digits only. */
  whitelist?: string;
}

export interface OCRResult {
  text: string;
  /** 0-100 engine confidence. */
  confidence: number;
}

/**
 * Abstraction over the local OCR engine, so the Tesseract.js-based
 * implementation can be swapped for a different local engine later
 * without touching RegionExtractor/ReviewTable/validation code. No
 * provider may call a cloud/network OCR service - see TesseractOCRProvider
 * for how offline operation is enforced (vendored worker/core/lang files).
 */
export interface OCRProvider {
  initialize(): Promise<void>;
  recognize(image: HTMLCanvasElement, options?: OCRRecognizeOptions): Promise<OCRResult>;
  terminate(): Promise<void>;
}
