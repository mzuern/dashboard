import { createWorker, PSM, type Worker } from 'tesseract.js';
import type { OCRProvider, OCRRecognizeOptions, OCRResult } from './OCRProvider';

/**
 * Local OCR via Tesseract.js. All assets (worker script, WASM core, and
 * English trained data) are served from /vendor - never from a CDN - so
 * OCR keeps working offline after the first load. See
 * scripts/vendor-assets.mjs for how those files get there.
 */
export class TesseractOCRProvider implements OCRProvider {
  private worker: Worker | null = null;
  private initPromise: Promise<void> | null = null;

  initialize(): Promise<void> {
    if (!this.initPromise) {
      this.initPromise = this.doInitialize();
    }
    return this.initPromise;
  }

  private async doInitialize(): Promise<void> {
    this.worker = await createWorker('eng', 1, {
      workerPath: '/vendor/tesseract/worker.min.js',
      corePath: '/vendor/tesseract/',
      langPath: '/vendor/tessdata/',
      gzip: true,
      cacheMethod: 'none',
    });
    await this.worker.setParameters({ tessedit_pageseg_mode: PSM.SINGLE_LINE });
  }

  async recognize(image: HTMLCanvasElement, options: OCRRecognizeOptions = {}): Promise<OCRResult> {
    if (!this.worker) throw new Error('TesseractOCRProvider not initialized.');
    await this.worker.setParameters({
      tessedit_char_whitelist: options.whitelist ?? '',
    });
    const { data } = await this.worker.recognize(image);
    return { text: data.text.trim(), confidence: data.confidence };
  }

  async terminate(): Promise<void> {
    await this.worker?.terminate();
    this.worker = null;
    this.initPromise = null;
  }
}
