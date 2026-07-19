import { useEffect, useRef } from 'react';
import type { ScanPipeline } from '../../state/ScanPipeline';
import type { AppSettings, ReviewRow } from '../../types/domain';
import { useScanPipeline } from '../hooks/useScanPipeline';
import { CoverageOverlay } from '../components/CoverageOverlay';
import { CornerAdjuster } from '../components/CornerAdjuster';
import { DebugPanel } from '../components/DebugPanel';

interface Props {
  pipeline: ScanPipeline;
  settings: AppSettings;
  onComplete: (rows: ReviewRow[]) => void;
  onCancel: () => void;
}

const PROCESSING_LABELS: Record<string, string> = {
  stitching: 'Stitching frames into one board image...',
  adjustCorners: 'Confirm the board edges',
  correcting: 'Correcting perspective...',
  detecting: 'Detecting project rows...',
  ocr: 'Reading project numbers, customers, and days remaining...',
};

export function ScanScreen({ pipeline, settings, onComplete, onCancel }: Props) {
  const state = useScanPipeline(pipeline);
  const videoHostRef = useRef<HTMLDivElement>(null);
  const startedComplete = useRef(false);

  useEffect(() => {
    void pipeline.preload();
  }, [pipeline]);

  useEffect(() => {
    const host = videoHostRef.current;
    if (!host) return;
    const video = pipeline.video;
    // pipeline.video is a live HTMLVideoElement owned by CameraManager, not
    // a React-managed value - mounting it imperatively is intentional.
    // eslint-disable-next-line react-hooks/immutability
    video.className = 'scan-video';
    host.appendChild(video);
    return () => {
      if (host.contains(video)) host.removeChild(video);
    };
  }, [pipeline]);

  useEffect(() => {
    if (state?.phase === 'review' && state.reviewRows && !startedComplete.current) {
      startedComplete.current = true;
      onComplete(state.reviewRows);
    }
    if (state?.phase !== 'review') {
      startedComplete.current = false;
    }
  }, [state, onComplete]);

  if (!state) return null;

  const isLiveScanning = state.phase === 'scanning' || state.phase === 'confirmIncomplete';
  const isProcessing = ['stitching', 'correcting', 'detecting', 'ocr'].includes(state.phase);

  return (
    <div className="scan-screen">
      <div className="scan-screen__viewport" ref={videoHostRef}>
        {isLiveScanning && (
          <CoverageOverlay snapshot={state.coverageSnapshot} highlightLastGoodArea={state.activeIssue?.code === 'tracking_lost'} />
        )}

        {state.phase === 'idle' && (
          <div className="scan-screen__start">
            <p>Position the camera over the whiteboard, then start scanning.</p>
            {!state.engineReady && <p className="scan-screen__loading">Loading scanner engine...</p>}
            {state.error && <p className="scan-screen__error">{state.error}</p>}
            <button className="btn btn--primary" disabled={!state.engineReady} onClick={() => void pipeline.startScanning()}>
              Scan Board
            </button>
          </div>
        )}

        {isLiveScanning && (
          <div className="scan-hud">
            <div className="scan-hud__top">
              <div className="scan-hud__coverage">
                <span className="scan-hud__coverage-label">Board Coverage</span>
                <span className="scan-hud__coverage-value">{Math.round(state.coverageFraction * 100)}%</span>
              </div>
              <QualityIndicator issueCode={state.activeIssue?.code ?? null} />
            </div>

            <div className="scan-hud__guidance" data-severity={state.activeIssue ? 'warn' : 'ok'}>
              {state.guidance}
            </div>

            <div className="scan-hud__bottom">
              <button className="btn btn--ghost" onClick={onCancel}>
                Cancel
              </button>
              <button className="btn btn--secondary" onClick={() => void pipeline.requestStop()}>
                Done Scanning
              </button>
            </div>
          </div>
        )}

        {state.phase === 'confirmIncomplete' && (
          <div className="modal-backdrop">
            <div className="modal">
              <h3>Board scan looks incomplete</h3>
              <p>{state.activeIssue?.message}</p>
              <p>You can keep scanning to fill in the missing (red) areas, or process what's captured so far.</p>
              <div className="modal__actions">
                <button className="btn btn--secondary" onClick={() => pipeline.resumeScanning()}>
                  Keep Scanning
                </button>
                <button className="btn btn--primary" onClick={() => void pipeline.forceProcessAnyway()}>
                  Process Anyway
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {isProcessing && (
        <div className="processing-overlay">
          <div className="processing-overlay__label">{PROCESSING_LABELS[state.phase] ?? 'Processing...'}</div>
          <div className="progress-bar">
            <div className="progress-bar__fill" style={{ width: `${Math.round(state.processingProgress * 100)}%` }} />
          </div>
        </div>
      )}

      {state.phase === 'adjustCorners' && state.mosaicCanvas && state.quad && (
        <div className="corner-screen">
          <h2>Confirm board edges</h2>
          <p>Drag the corners so they line up with the whiteboard's edges.</p>
          <CornerAdjuster canvas={state.mosaicCanvas} quad={state.quad} onChange={(quad) => pipeline.updateQuad(quad)} />
          <div className="corner-screen__actions">
            <button className="btn btn--primary" onClick={() => void pipeline.confirmCorners(state.quad!)}>
              Looks Good - Continue
            </button>
          </div>
        </div>
      )}

      {settings.debugMode && <DebugPanel state={state} />}
    </div>
  );
}

function QualityIndicator({ issueCode }: { issueCode: string | null }) {
  const status = !issueCode ? 'good' : issueCode === 'blur' || issueCode === 'tracking_lost' ? 'bad' : 'warn';
  const label = status === 'good' ? 'Good' : status === 'bad' ? 'Poor' : 'Fair';
  return (
    <div className={`quality-indicator quality-indicator--${status}`}>
      <span className="quality-indicator__dot" />
      {label}
    </div>
  );
}
