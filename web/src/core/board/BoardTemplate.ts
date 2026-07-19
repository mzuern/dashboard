import type { BoardTemplate } from '../../types/domain';

/**
 * Default layout for a typical production whiteboard: project number on
 * the left, customer name in the middle, days remaining on the right.
 * Everything here is meant to be edited from the Settings screen for a
 * different board - no code changes required. See docs/CALIBRATION.md.
 */
export const DEFAULT_BOARD_TEMPLATE: BoardTemplate = {
  boardWidthPx: 2400,
  boardHeightPx: 1600,
  marginTopPx: 80,
  rowHeightPx: 90,
  regions: {
    projectNumber: { xPct: 0.02, yPct: 0.1, wPct: 0.16, hPct: 0.8 },
    customer: { xPct: 0.2, yPct: 0.1, wPct: 0.55, hPct: 0.8 },
    daysRemaining: { xPct: 0.78, yPct: 0.1, wPct: 0.2, hPct: 0.8 },
  },
};

export function computeRowCount(template: BoardTemplate): number {
  if (template.rowCount) return template.rowCount;
  const usable = template.boardHeightPx - template.marginTopPx;
  return Math.max(0, Math.floor(usable / template.rowHeightPx));
}

export function cloneTemplate(template: BoardTemplate): BoardTemplate {
  return JSON.parse(JSON.stringify(template)) as BoardTemplate;
}

const REGION_KEYS = ['projectNumber', 'customer', 'daysRemaining'] as const;

export function validateTemplate(template: BoardTemplate): string[] {
  const errors: string[] = [];
  if (template.boardWidthPx < 200) errors.push('Board width is too small.');
  if (template.boardHeightPx < 200) errors.push('Board height is too small.');
  if (template.rowHeightPx < 20) errors.push('Row height is too small.');
  if (template.marginTopPx < 0) errors.push('Top margin cannot be negative.');
  for (const key of REGION_KEYS) {
    const r = template.regions[key];
    if (r.wPct <= 0 || r.hPct <= 0) errors.push(`${key} region must have positive width/height.`);
    if (r.xPct < 0 || r.xPct + r.wPct > 1) errors.push(`${key} region extends outside the row horizontally.`);
    if (r.yPct < 0 || r.yPct + r.hPct > 1) errors.push(`${key} region extends outside the row vertically.`);
  }
  return errors;
}
