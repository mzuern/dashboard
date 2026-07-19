import { useState } from 'react';
import type { AppSettings, FieldKey, FractionalRect } from '../../types/domain';
import { Settings, CAMERA_RESOLUTION_PRESETS } from '../../core/settings/Settings';
import { cloneTemplate } from '../../core/board/BoardTemplate';

interface Props {
  settings: AppSettings;
  onSaved: (settings: AppSettings) => void;
  onBack: () => void;
}

const FIELD_LABELS: Record<FieldKey, string> = {
  projectNumber: 'Project Number Region',
  customer: 'Customer Region',
  daysRemaining: 'Days Remaining Region',
};

/**
 * Everything here maps 1:1 to BoardTemplate + AppSettings, so a different
 * whiteboard layout can be calibrated entirely from this screen - no code
 * changes. See docs/CALIBRATION.md for a walkthrough.
 */
export function SettingsScreen({ settings, onSaved, onBack }: Props) {
  const [draft, setDraft] = useState<AppSettings>(() => ({ ...settings, boardTemplate: cloneTemplate(settings.boardTemplate) }));
  const [errors, setErrors] = useState<string[]>([]);
  const [saved, setSaved] = useState(false);

  function updateRegion(field: FieldKey, patch: Partial<FractionalRect>) {
    setDraft((d) => ({
      ...d,
      boardTemplate: {
        ...d.boardTemplate,
        regions: { ...d.boardTemplate.regions, [field]: { ...d.boardTemplate.regions[field], ...patch } },
      },
    }));
  }

  async function handleSave() {
    const result = await Settings.save(draft);
    if (!result.ok) {
      setErrors(result.errors);
      setSaved(false);
      return;
    }
    setErrors([]);
    setSaved(true);
    onSaved(draft);
    setTimeout(() => setSaved(false), 1500);
  }

  async function handleReset() {
    await Settings.reset();
    const fresh = await Settings.load();
    setDraft({ ...fresh, boardTemplate: cloneTemplate(fresh.boardTemplate) });
    onSaved(fresh);
  }

  return (
    <div className="settings-screen">
      <h2>Settings</h2>

      <section className="settings-section">
        <h3>Board Template</h3>
        <div className="settings-grid">
          <label>
            Board Width (px)
            <input
              type="number"
              value={draft.boardTemplate.boardWidthPx}
              onChange={(e) => setDraft((d) => ({ ...d, boardTemplate: { ...d.boardTemplate, boardWidthPx: Number(e.target.value) } }))}
            />
          </label>
          <label>
            Board Height (px)
            <input
              type="number"
              value={draft.boardTemplate.boardHeightPx}
              onChange={(e) => setDraft((d) => ({ ...d, boardTemplate: { ...d.boardTemplate, boardHeightPx: Number(e.target.value) } }))}
            />
          </label>
          <label>
            Row Height (px)
            <input
              type="number"
              value={draft.boardTemplate.rowHeightPx}
              onChange={(e) => setDraft((d) => ({ ...d, boardTemplate: { ...d.boardTemplate, rowHeightPx: Number(e.target.value) } }))}
            />
          </label>
          <label>
            Top Margin (px)
            <input
              type="number"
              value={draft.boardTemplate.marginTopPx}
              onChange={(e) => setDraft((d) => ({ ...d, boardTemplate: { ...d.boardTemplate, marginTopPx: Number(e.target.value) } }))}
            />
          </label>
        </div>
      </section>

      <section className="settings-section">
        <h3>Field Regions (% of row)</h3>
        {(Object.keys(FIELD_LABELS) as FieldKey[]).map((field) => {
          const r = draft.boardTemplate.regions[field];
          return (
            <div className="settings-region" key={field}>
              <div className="settings-region__title">{FIELD_LABELS[field]}</div>
              <div className="settings-grid settings-grid--four">
                <label>
                  X%
                  <input
                    type="number"
                    min={0}
                    max={100}
                    value={pct(r.xPct)}
                    onChange={(e) => updateRegion(field, { xPct: unpct(e.target.value) })}
                  />
                </label>
                <label>
                  Y%
                  <input
                    type="number"
                    min={0}
                    max={100}
                    value={pct(r.yPct)}
                    onChange={(e) => updateRegion(field, { yPct: unpct(e.target.value) })}
                  />
                </label>
                <label>
                  Width%
                  <input
                    type="number"
                    min={0}
                    max={100}
                    value={pct(r.wPct)}
                    onChange={(e) => updateRegion(field, { wPct: unpct(e.target.value) })}
                  />
                </label>
                <label>
                  Height%
                  <input
                    type="number"
                    min={0}
                    max={100}
                    value={pct(r.hPct)}
                    onChange={(e) => updateRegion(field, { hPct: unpct(e.target.value) })}
                  />
                </label>
              </div>
            </div>
          );
        })}
      </section>

      <section className="settings-section">
        <h3>Scanning</h3>
        <div className="settings-grid">
          <label>
            OCR Confidence Threshold (%)
            <input
              type="number"
              min={0}
              max={100}
              value={draft.ocrConfidenceThreshold}
              onChange={(e) => setDraft((d) => ({ ...d, ocrConfidenceThreshold: Number(e.target.value) }))}
            />
          </label>
          <label>
            Coverage Threshold (%)
            <input
              type="number"
              min={1}
              max={100}
              value={pct(draft.coverageThreshold)}
              onChange={(e) => setDraft((d) => ({ ...d, coverageThreshold: unpct(e.target.value) }))}
            />
          </label>
          <label>
            Camera Resolution
            <select
              value={draft.cameraResolution.label}
              onChange={(e) => {
                const preset = CAMERA_RESOLUTION_PRESETS.find((p) => p.label === e.target.value);
                if (preset) setDraft((d) => ({ ...d, cameraResolution: preset }));
              }}
            >
              {CAMERA_RESOLUTION_PRESETS.map((p) => (
                <option key={p.label} value={p.label}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="settings-checkbox">
            <input type="checkbox" checked={draft.debugMode} onChange={(e) => setDraft((d) => ({ ...d, debugMode: e.target.checked }))} />
            Debug Mode
          </label>
        </div>
      </section>

      {errors.length > 0 && (
        <ul className="settings-errors">
          {errors.map((e) => (
            <li key={e}>{e}</li>
          ))}
        </ul>
      )}

      <div className="settings-screen__actions">
        <button className="btn btn--ghost" onClick={onBack}>
          Back
        </button>
        <button className="btn btn--ghost" onClick={() => void handleReset()}>
          Reset to Defaults
        </button>
        <button className="btn btn--primary" onClick={() => void handleSave()}>
          {saved ? 'Saved!' : 'Save Settings'}
        </button>
      </div>
    </div>
  );
}

function pct(frac: number): number {
  return Math.round(frac * 1000) / 10;
}

function unpct(value: string): number {
  const n = Number(value);
  return Number.isFinite(n) ? n / 100 : 0;
}
