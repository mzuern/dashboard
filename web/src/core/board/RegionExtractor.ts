import type { BoardTemplate, FieldKey, PixelRect, RowRect } from '../../types/domain';
import { cropCanvas, scaleCanvas } from '../vision/imageUtils';

const FIELD_KEYS: FieldKey[] = ['projectNumber', 'customer', 'daysRemaining'];

/** Minimum crop width fed to OCR - small regions are upscaled for legibility. */
const MIN_OCR_WIDTH = 400;

export interface ExtractedRegion {
  field: FieldKey;
  canvas: HTMLCanvasElement;
  sourceRect: PixelRect;
}

/**
 * Crops only the three configured field regions out of each detected row -
 * never the whole board. Region rectangles are defined as percentages of
 * the row's bounding box in BoardTemplate, so they scale automatically
 * with row height.
 */
export class RegionExtractor {
  private readonly template: BoardTemplate;

  constructor(template: BoardTemplate) {
    this.template = template;
  }

  extractRow(board: HTMLCanvasElement, row: RowRect): ExtractedRegion[] {
    return FIELD_KEYS.map((field) => {
      const frac = this.template.regions[field];
      const sourceRect: PixelRect = {
        x: row.rect.x + frac.xPct * row.rect.width,
        y: row.rect.y + frac.yPct * row.rect.height,
        width: frac.wPct * row.rect.width,
        height: frac.hPct * row.rect.height,
      };
      let canvas = cropCanvas(board, sourceRect);
      if (canvas.width < MIN_OCR_WIDTH) {
        canvas = scaleCanvas(canvas, MIN_OCR_WIDTH / canvas.width);
      }
      return { field, canvas, sourceRect };
    });
  }
}
