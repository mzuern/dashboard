import type { AppSettings } from '../../types/domain';
import { Storage, DEFAULT_SETTINGS } from '../storage/Storage';
import { validateTemplate } from '../board/BoardTemplate';
import { CAMERA_RESOLUTION_PRESETS } from '../camera/CameraManager';

export { DEFAULT_SETTINGS, CAMERA_RESOLUTION_PRESETS };

/** Settings-domain façade over Storage: load/save plus validation before persisting. */
export const Settings = {
  load: (): Promise<AppSettings> => Storage.getSettings(),

  async save(settings: AppSettings): Promise<{ ok: true } | { ok: false; errors: string[] }> {
    const errors = validateTemplate(settings.boardTemplate);
    if (settings.ocrConfidenceThreshold < 0 || settings.ocrConfidenceThreshold > 100) {
      errors.push('OCR confidence threshold must be between 0 and 100.');
    }
    if (settings.coverageThreshold <= 0 || settings.coverageThreshold > 1) {
      errors.push('Coverage threshold must be between 1% and 100%.');
    }
    if (errors.length > 0) return { ok: false, errors };
    await Storage.setSettings(settings);
    return { ok: true };
  },

  reset: (): Promise<void> => Storage.setSettings(DEFAULT_SETTINGS),
};
