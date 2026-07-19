import type { CoverageSnapshot } from '../../core/vision/CoverageTracker';

interface Props {
  snapshot: CoverageSnapshot | null;
  highlightLastGoodArea?: boolean;
}

/**
 * Renders the coverage heatmap (green = sufficiently overlapped, red =
 * still needed) as an SVG overlay on top of the live camera preview. Uses
 * the sparse grid from CoverageTracker directly - cells are drawn in a
 * viewBox spanning the visited bounding box, so it scales to any preview
 * size for free.
 */
export function CoverageOverlay({ snapshot, highlightLastGoodArea }: Props) {
  if (!snapshot || snapshot.cells.length === 0) return null;

  const padding = 2;
  const minGx = snapshot.minGx - padding;
  const maxGx = snapshot.maxGx + padding;
  const minGy = snapshot.minGy - padding;
  const maxGy = snapshot.maxGy + padding;
  const width = maxGx - minGx + 1;
  const height = maxGy - minGy + 1;

  return (
    <svg
      className="coverage-overlay"
      viewBox={`${minGx} ${minGy} ${width} ${height}`}
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      {highlightLastGoodArea && (
        <rect
          x={snapshot.minGx}
          y={snapshot.minGy}
          width={snapshot.maxGx - snapshot.minGx + 1}
          height={snapshot.maxGy - snapshot.minGy + 1}
          className="coverage-highlight"
        />
      )}
      {snapshot.cells.map((cell) => (
        <rect
          key={`${cell.gx},${cell.gy}`}
          x={cell.gx}
          y={cell.gy}
          width={1}
          height={1}
          className={cell.covered ? 'coverage-cell coverage-cell--covered' : 'coverage-cell coverage-cell--pending'}
        />
      ))}
      <circle cx={snapshot.cursor.gx + 0.5} cy={snapshot.cursor.gy + 0.5} r={0.6} className="coverage-cursor" />
    </svg>
  );
}
