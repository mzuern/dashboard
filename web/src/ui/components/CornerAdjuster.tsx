import { useCallback, useEffect, useRef, useState } from 'react';
import type { Quad } from '../../core/vision/PerspectiveCorrector';

interface Props {
  canvas: HTMLCanvasElement;
  quad: Quad;
  onChange: (quad: Quad) => void;
}

const HANDLE_LABELS = ['Top-left', 'Top-right', 'Bottom-right', 'Bottom-left'];

/**
 * Lets the user drag the four detected board corners before perspective
 * correction runs. Auto-detection (PerspectiveCorrector) gets it right
 * most of the time, but whiteboards with low contrast against their
 * surroundings can fool contour detection, so this manual fallback is
 * always available rather than being an error state.
 */
export function CornerAdjuster({ canvas, quad, onChange }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [dataUrl, setDataUrl] = useState('');
  const [dragIndex, setDragIndex] = useState<number | null>(null);

  // Read via a ref inside the window listener below so the listener never
  // needs to be re-attached mid-drag while still always seeing the latest
  // quad (important once >1 corner has moved during the same drag).
  const quadRef = useRef(quad);
  useEffect(() => {
    quadRef.current = quad;
  }, [quad]);

  useEffect(() => {
    setDataUrl(canvas.toDataURL('image/jpeg', 0.85));
  }, [canvas]);

  const toImageCoords = useCallback(
    (clientX: number, clientY: number): { x: number; y: number } => {
      const rect = containerRef.current!.getBoundingClientRect();
      const relX = clamp01((clientX - rect.left) / rect.width);
      const relY = clamp01((clientY - rect.top) / rect.height);
      return { x: relX * canvas.width, y: relY * canvas.height };
    },
    [canvas],
  );

  useEffect(() => {
    if (dragIndex === null) return;

    const onMove = (e: PointerEvent) => {
      const point = toImageCoords(e.clientX, e.clientY);
      const next = [...quadRef.current] as Quad;
      next[dragIndex] = point;
      onChange(next);
    };
    const onUp = () => setDragIndex(null);

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
  }, [dragIndex, onChange, toImageCoords]);

  const points = quad.map((c) => `${(c.x / canvas.width) * 100},${(c.y / canvas.height) * 100}`).join(' ');

  return (
    <div className="corner-adjuster" ref={containerRef}>
      {dataUrl && <img src={dataUrl} alt="Scanned board" className="corner-adjuster__image" draggable={false} />}
      <svg className="corner-adjuster__overlay" viewBox="0 0 100 100" preserveAspectRatio="none">
        <polygon points={points} className="corner-adjuster__polygon" />
      </svg>
      {quad.map((c, i) => (
        <button
          key={i}
          type="button"
          aria-label={`Drag ${HANDLE_LABELS[i]} corner`}
          className="corner-adjuster__handle"
          style={{ left: `${(c.x / canvas.width) * 100}%`, top: `${(c.y / canvas.height) * 100}%` }}
          onPointerDown={(e) => {
            e.preventDefault();
            setDragIndex(i);
          }}
        />
      ))}
    </div>
  );
}

function clamp01(v: number): number {
  return Math.max(0, Math.min(1, v));
}
