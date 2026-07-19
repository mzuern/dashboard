import type { ScanState } from '../../state/ScanPipeline';

interface Props {
  state: ScanState;
}

/**
 * Surfaces internals that make calibrating a new board layout tractable:
 * detected features/overlap per stitched frame, coverage numbers, row
 * detection results, and perspective quad confidence. Only mounted when
 * Settings > Debug Mode is on.
 */
export function DebugPanel({ state }: Props) {
  return (
    <div className="debug-panel">
      <div className="debug-panel__title">Debug</div>
      <dl className="debug-panel__grid">
        <dt>Phase</dt>
        <dd>{state.phase}</dd>
        <dt>Frames captured</dt>
        <dd>{state.frameCount}</dd>
        <dt>Coverage</dt>
        <dd>{Math.round(state.coverageFraction * 100)}%</dd>
        {state.quadAreaRatio !== undefined && (
          <>
            <dt>Board quad area ratio</dt>
            <dd>{state.quadAreaRatio.toFixed(2)}</dd>
          </>
        )}
        {state.rows && (
          <>
            <dt>Rows detected</dt>
            <dd>
              {state.rows.length} ({state.rows.filter((r) => r.detected).length} from grid lines)
            </dd>
          </>
        )}
      </dl>

      {state.stitchDebug && (
        <>
          <div className="debug-panel__subtitle">Stitching</div>
          <table className="debug-panel__table">
            <thead>
              <tr>
                <th>#</th>
                <th>Matched features</th>
                <th>Fallback</th>
              </tr>
            </thead>
            <tbody>
              {state.stitchDebug.map((d) => (
                <tr key={d.frameIndex}>
                  <td>{d.frameIndex}</td>
                  <td>{d.matchedFeatures}</td>
                  <td>{d.usedFallback ? 'yes (translation-only)' : 'no'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {state.reviewRows && (
        <>
          <div className="debug-panel__subtitle">OCR confidence</div>
          <table className="debug-panel__table">
            <thead>
              <tr>
                <th>Row</th>
                <th>Project #</th>
                <th>Customer</th>
                <th>Days</th>
              </tr>
            </thead>
            <tbody>
              {state.reviewRows.map((r) => (
                <tr key={r.id}>
                  <td>{r.rowIndex}</td>
                  <td>{r.projectNumber.confidence.toFixed(0)}</td>
                  <td>{r.customer.confidence.toFixed(0)}</td>
                  <td>{r.daysRemaining.confidence.toFixed(0)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
