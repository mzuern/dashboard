import type { OpenCV } from './opencvLoader';
import type { KeyFrame } from './FrameCollector';
import { canvasFromMat } from './imageUtils';

export interface StitchDebugInfo {
  frameIndex: number;
  matchedFeatures: number;
  usedFallback: boolean;
  placement: { x: number; y: number };
}

export interface StitchResult {
  canvas: HTMLCanvasElement;
  debug: StitchDebugInfo[];
}

const MIN_GOOD_MATCHES = 8;
const RANSAC_REPROJ_THRESHOLD = 4;
const MAX_CANVAS_DIMENSION = 6000;

/**
 * Stitches keyframes into a single mosaic image using ORB feature
 * matching + RANSAC homography estimation, in the classic OpenCV
 * "panorama" recipe: detect features, match, estimate a transform that
 * maps each new frame into the growing mosaic's coordinate space, warp,
 * and composite. Frames are matched against a cropped region of interest
 * of the mosaic (near where FrameCollector's tracked position predicts
 * they should land) rather than the whole mosaic, which keeps matching
 * fast and avoids false matches against repeated whiteboard text
 * elsewhere on the board.
 */
export class ImageStitcher {
  private readonly cv: OpenCV;
  private readonly trackUnitToWorkingPx: number;

  constructor(cv: OpenCV, trackUnitToWorkingPx: number) {
    this.cv = cv;
    this.trackUnitToWorkingPx = trackUnitToWorkingPx;
  }

  async stitch(frames: readonly KeyFrame[], onProgress?: (done: number, total: number) => void): Promise<StitchResult> {
    if (frames.length === 0) throw new Error('No frames to stitch.');
    const cv = this.cv;
    const debug: StitchDebugInfo[] = [];

    if (frames.length === 1) {
      return { canvas: frames[0].canvas, debug };
    }

    const conv = this.trackUnitToWorkingPx;
    const xs = frames.map((f) => f.camX * conv);
    const ys = frames.map((f) => f.camY * conv);
    const frameW = frames[0].canvas.width;
    const frameH = frames[0].canvas.height;
    const pad = Math.round(Math.max(frameW, frameH) * 0.5);

    const minX = Math.min(...xs) - pad;
    const maxX = Math.max(...xs) + frameW + pad;
    const minY = Math.min(...ys) - pad;
    const maxY = Math.max(...ys) + frameH + pad;

    let canvasW = Math.round(maxX - minX);
    let canvasH = Math.round(maxY - minY);
    let scaleDown = 1;
    if (Math.max(canvasW, canvasH) > MAX_CANVAS_DIMENSION) {
      scaleDown = MAX_CANVAS_DIMENSION / Math.max(canvasW, canvasH);
      canvasW = Math.round(canvasW * scaleDown);
      canvasH = Math.round(canvasH * scaleDown);
    }

    const originX = -minX * scaleDown;
    const originY = -minY * scaleDown;

    const mosaic = new cv.Mat(canvasH, canvasW, cv.CV_8UC4, new cv.Scalar(0, 0, 0, 0));
    const mosaicMask = new cv.Mat(canvasH, canvasW, cv.CV_8UC1, new cv.Scalar(0));

    try {
      // Place the first frame with a pure scale+translation.
      const H0 = scaleTranslateMat(cv, scaleDown, originX, originY);
      this.warpAndComposite(frames[0].canvas, H0, mosaic, mosaicMask);
      H0.delete();
      debug.push({ frameIndex: 0, matchedFeatures: 0, usedFallback: false, placement: { x: originX, y: originY } });
      onProgress?.(1, frames.length);

      for (let i = 1; i < frames.length; i++) {
        const frame = frames[i];
        const expectedX = originX + (xs[i] - xs[0]) * scaleDown;
        const expectedY = originY + (ys[i] - ys[0]) * scaleDown;

        const match = this.estimateHomography(frame.canvas, mosaic, mosaicMask, expectedX, expectedY, scaleDown);
        let H: InstanceType<OpenCV['Mat']>;
        let usedFallback = false;
        let matchedFeatures = 0;
        if (match) {
          H = match.H;
          matchedFeatures = match.inliers;
        } else {
          H = scaleTranslateMat(cv, scaleDown, expectedX, expectedY);
          usedFallback = true;
        }

        this.warpAndComposite(frame.canvas, H, mosaic, mosaicMask);
        H.delete();
        debug.push({ frameIndex: i, matchedFeatures, usedFallback, placement: { x: expectedX, y: expectedY } });
        onProgress?.(i + 1, frames.length);
        // Yield to the event loop so the processing screen stays responsive.
        await new Promise((resolve) => setTimeout(resolve, 0));
      }

      const cropped = this.cropToContent(mosaic, mosaicMask);
      const canvas = canvasFromMat(cv, cropped);
      cropped.delete();
      return { canvas, debug };
    } finally {
      mosaic.delete();
      mosaicMask.delete();
    }
  }

  private estimateHomography(
    frameCanvas: HTMLCanvasElement,
    mosaic: InstanceType<OpenCV['Mat']>,
    mosaicMask: InstanceType<OpenCV['Mat']>,
    expectedX: number,
    expectedY: number,
    scaleDown: number,
  ): { H: InstanceType<OpenCV['Mat']>; inliers: number } | null {
    const cv = this.cv;
    const frameW = Math.round(frameCanvas.width * scaleDown);
    const frameH = Math.round(frameCanvas.height * scaleDown);
    const roiPad = Math.round(Math.max(frameW, frameH) * 0.5);

    const roiX = clamp(Math.round(expectedX - roiPad), 0, mosaic.cols - 1);
    const roiY = clamp(Math.round(expectedY - roiPad), 0, mosaic.rows - 1);
    const roiW = clamp(frameW + roiPad * 2, 1, mosaic.cols - roiX);
    const roiH = clamp(frameH + roiPad * 2, 1, mosaic.rows - roiY);
    const roiRect = new cv.Rect(roiX, roiY, roiW, roiH);

    const mosaicROI = mosaic.roi(roiRect);
    const maskROI = mosaicMask.roi(roiRect);
    if (cv.countNonZero(maskROI) < 200) {
      mosaicROI.delete();
      maskROI.delete();
      return null;
    }

    const frameFull = cv.imread(frameCanvas);
    const frame = new cv.Mat();
    cv.resize(frameFull, frame, new cv.Size(frameW, frameH), 0, 0, cv.INTER_AREA);
    frameFull.delete();

    const grayFrame = new cv.Mat();
    const grayRoi = new cv.Mat();
    cv.cvtColor(frame, grayFrame, cv.COLOR_RGBA2GRAY);
    cv.cvtColor(mosaicROI, grayRoi, cv.COLOR_RGBA2GRAY);

    const orb = new cv.ORB(600);
    const kp1 = new cv.KeyPointVector();
    const kp2 = new cv.KeyPointVector();
    const desc1 = new cv.Mat();
    const desc2 = new cv.Mat();
    const noMask = new cv.Mat();
    orb.detectAndCompute(grayFrame, noMask, kp1, desc1);
    orb.detectAndCompute(grayRoi, maskROI, kp2, desc2);

    let result: { H: InstanceType<OpenCV['Mat']>; inliers: number } | null = null;

    if (desc1.rows >= 4 && desc2.rows >= 4) {
      const bf = new cv.BFMatcher(cv.NORM_HAMMING, false);
      const knn = new cv.DMatchVectorVector();
      bf.knnMatch(desc1, desc2, knn, 2);

      const srcPts: number[] = [];
      const dstPts: number[] = [];
      for (let i = 0; i < knn.size(); i++) {
        const pair = knn.get(i);
        if (pair.size() < 2) continue;
        const m = pair.get(0);
        const n = pair.get(1);
        if (m.distance < 0.75 * n.distance) {
          const p1 = kp1.get(m.queryIdx).pt;
          const p2 = kp2.get(m.trainIdx).pt;
          srcPts.push(p1.x, p1.y);
          dstPts.push(p2.x + roiX, p2.y + roiY);
        }
      }

      if (srcPts.length / 2 >= MIN_GOOD_MATCHES) {
        const srcMat = cv.matFromArray(srcPts.length / 2, 1, cv.CV_32FC2, srcPts);
        const dstMat = cv.matFromArray(dstPts.length / 2, 1, cv.CV_32FC2, dstPts);
        const inlierMask = new cv.Mat();
        const H = cv.findHomography(srcMat, dstMat, cv.RANSAC, RANSAC_REPROJ_THRESHOLD, inlierMask);
        const inliers = cv.countNonZero(inlierMask);

        if (!H.empty() && inliers >= MIN_GOOD_MATCHES && isSaneHomography(H, expectedX, expectedY, frameW)) {
          result = { H, inliers };
        } else {
          H.delete();
        }
        srcMat.delete();
        dstMat.delete();
        inlierMask.delete();
      }

      bf.delete();
      knn.delete();
    }

    if (result) {
      // The homography above maps the *scaled-down* frame into mosaic
      // coordinates. Compose with the downscale so callers can warp the
      // original-resolution frame directly.
      const S = cv.matFromArray(3, 3, cv.CV_64F, [scaleDown, 0, 0, 0, scaleDown, 0, 0, 0, 1]);
      const composed = new cv.Mat();
      cv.gemm(result.H, S, 1, new cv.Mat(), 0, composed);
      result.H.delete();
      result.H = composed;
    }

    frame.delete();
    grayFrame.delete();
    grayRoi.delete();
    orb.delete();
    kp1.delete();
    kp2.delete();
    desc1.delete();
    desc2.delete();
    noMask.delete();
    mosaicROI.delete();
    maskROI.delete();

    return result;
  }

  private warpAndComposite(
    frameCanvas: HTMLCanvasElement,
    H: InstanceType<OpenCV['Mat']>,
    mosaic: InstanceType<OpenCV['Mat']>,
    mosaicMask: InstanceType<OpenCV['Mat']>,
  ): void {
    const cv = this.cv;
    const frame = cv.imread(frameCanvas);
    const size = new cv.Size(mosaic.cols, mosaic.rows);

    const warped = new cv.Mat();
    cv.warpPerspective(frame, warped, H, size, cv.INTER_LINEAR, cv.BORDER_CONSTANT, new cv.Scalar(0, 0, 0, 0));

    const srcMask = new cv.Mat(frame.rows, frame.cols, cv.CV_8UC1, new cv.Scalar(255));
    const warpedMask = new cv.Mat();
    cv.warpPerspective(srcMask, warpedMask, H, size, cv.INTER_NEAREST, cv.BORDER_CONSTANT, new cv.Scalar(0));

    warped.copyTo(mosaic, warpedMask);
    cv.bitwise_or(mosaicMask, warpedMask, mosaicMask);

    frame.delete();
    warped.delete();
    srcMask.delete();
    warpedMask.delete();
  }

  private cropToContent(mosaic: InstanceType<OpenCV['Mat']>, mosaicMask: InstanceType<OpenCV['Mat']>): InstanceType<OpenCV['Mat']> {
    const cv = this.cv;
    const rect = cv.boundingRect(mosaicMask);
    if (rect.width <= 0 || rect.height <= 0) return mosaic.clone();
    return mosaic.roi(rect).clone();
  }
}

function scaleTranslateMat(cv: OpenCV, scale: number, tx: number, ty: number): InstanceType<OpenCV['Mat']> {
  return cv.matFromArray(3, 3, cv.CV_64F, [scale, 0, tx, 0, scale, ty, 0, 0, 1]);
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

function isSaneHomography(H: InstanceType<OpenCV['Mat']>, expectedX: number, expectedY: number, frameW: number): boolean {
  const d = H.data64F;
  const det = d[0] * d[4] - d[1] * d[3];
  if (det < 0.4 || det > 2.5) return false;
  const tx = d[2];
  const ty = d[5];
  const dist = Math.hypot(tx - expectedX, ty - expectedY);
  return dist < frameW * 1.5;
}
