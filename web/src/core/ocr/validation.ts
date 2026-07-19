import type { FieldResult } from '../../types/domain';

const DIGITS_WHITELIST = '0123456789';
const CUSTOMER_WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 &.,'/-";
const DAYS_WHITELIST = '0123456789Ccomplete'; // digits, or the word "Complete"

export const FIELD_WHITELISTS = {
  projectNumber: DIGITS_WHITELIST,
  customer: CUSTOMER_WHITELIST,
  daysRemaining: DAYS_WHITELIST,
} as const;

/**
 * Turns raw OCR output into a FieldResult. Never guesses or silently
 * drops a value - if the cleaned text doesn't match the expected format,
 * the raw OCR text is kept as-is and `formatValid` is false, so the
 * review screen can flag the row for the user to correct by hand.
 */
export function buildProjectNumberField(rawText: string, confidence: number): FieldResult {
  const cleaned = rawText.replace(/[^\d]/g, '');
  const formatValid = cleaned.length >= 3;
  return { rawText, value: cleaned || rawText.trim(), confidence, formatValid };
}

export function buildCustomerField(rawText: string, confidence: number): FieldResult {
  const cleaned = rawText.replace(/\s+/g, ' ').trim();
  const formatValid = cleaned.length > 0;
  return { rawText, value: cleaned, confidence, formatValid };
}

export function buildDaysRemainingField(rawText: string, confidence: number): FieldResult {
  const trimmed = rawText.trim();
  if (/^complete\.?$/i.test(trimmed)) {
    return { rawText, value: 'Complete', confidence, formatValid: true };
  }
  const digitsOnly = trimmed.replace(/[^\d]/g, '');
  if (digitsOnly.length > 0 && /^\d+$/.test(digitsOnly)) {
    return { rawText, value: digitsOnly, confidence, formatValid: true };
  }
  return { rawText, value: trimmed, confidence, formatValid: false };
}

export function isFieldOk(field: FieldResult, confidenceThreshold: number): boolean {
  return field.formatValid && field.confidence >= confidenceThreshold;
}
