/**
 * Lazily loads OpenCV.js (a ~10MB WASM module) only when the first scan
 * starts, so the initial app shell stays small and fast to install. The
 * promise is memoized so repeated calls are free after the first load.
 */
export type OpenCV = typeof import('@techstark/opencv-js') extends infer M ? (M extends { default: infer D } ? D : M) : never;

let cvPromise: Promise<OpenCV> | null = null;

export function loadOpenCV(): Promise<OpenCV> {
  if (!cvPromise) {
    cvPromise = import('@techstark/opencv-js').then(async (mod) => {
      const cvModule = (mod as unknown as { default: unknown }).default ?? mod;
      // @techstark/opencv-js's default export is, depending on version, one
      // of: a Promise that resolves to the ready module, an already-ready
      // module object, or a module object that still needs
      // onRuntimeInitialized to fire. Handle all three (matches the
      // package's own documented usage).
      if (cvModule instanceof Promise) {
        return (await cvModule) as OpenCV;
      }
      const cv = cvModule as OpenCV & {
        onRuntimeInitialized?: () => void;
        Mat?: unknown;
      };
      if (cv.Mat) return cv;
      await new Promise<void>((resolve) => {
        cv.onRuntimeInitialized = () => resolve();
      });
      return cv;
    });
  }
  return cvPromise;
}
