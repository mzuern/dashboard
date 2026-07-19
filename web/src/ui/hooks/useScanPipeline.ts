import { useEffect, useState } from 'react';
import type { ScanPipeline, ScanState } from '../../state/ScanPipeline';

export function useScanPipeline(pipeline: ScanPipeline | null): ScanState | null {
  const [state, setState] = useState<ScanState | null>(pipeline?.getState() ?? null);

  useEffect(() => {
    if (!pipeline) return;
    // subscribe() invokes the listener immediately with the current state,
    // so no separate synchronous setState call is needed here.
    return pipeline.subscribe(setState);
  }, [pipeline]);

  return state;
}
