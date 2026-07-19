# Production Board Scanner

A browser-based PWA that scans a physical production whiteboard with a
phone/tablet/desktop camera and generates a daily status email - Project
Number, Customer, and Estimated Days Remaining for each row. Everything
runs on-device: camera capture, image stitching, perspective correction,
row detection, and OCR. No cloud services, no AI APIs, no server.

## Getting started

```bash
npm install       # also vendors OCR runtime assets + generates app icons
npm run dev        # http://localhost:5173
npm run build       # type-checks, then produces dist/
npm run lint
npm run format
```

`npm install` runs `scripts/vendor-assets.mjs` and `scripts/generate-icons.mjs`
automatically (via `postinstall`) - see [Offline & OCR assets](#offline--ocr-assets)
below for why that's needed.

## Architecture

```
src/
  core/
    camera/      CameraManager              rear-camera access, frame grabbing
    vision/      MotionTracker              frame-to-frame shift/blur/lighting via OpenCV
                 CoverageTracker            builds the live coverage heatmap
                 FrameCollector             picks which frames to keep for stitching
                 ImageStitcher              ORB + homography mosaic builder
                 PerspectiveCorrector       board-edge detection + warp to rectangle
    board/       BoardTemplate              the editable board layout schema
                 BoardDetector              locates project rows in the corrected image
                 RegionExtractor            crops the 3 field regions per row
    ocr/         OCRProvider                OCR engine abstraction
                 TesseractOCRProvider       local Tesseract.js implementation
                 validation                 per-field cleanup/format rules
    email/       EmailGenerator             subject/body/mailto builder
    storage/     Storage                    IndexedDB persistence (settings, draft rows)
    settings/    Settings                   settings façade + validation
  state/
    ScanPipeline                            orchestrates all of the above, phase by phase
  ui/
    screens/     ScanScreen, ReviewScreen, EmailScreen, SettingsScreen
    components/  CoverageOverlay, CornerAdjuster, DebugPanel
    hooks/       useScanPipeline
```

`ScanPipeline` is the only module that knows the *order* things happen in;
every other module is independently testable and replaceable (in
particular `OCRProvider` - see [Extending](#extending-with-additional-fields)).

## How it works

### 1. Frame stitching

While scanning, `FrameCollector` keeps a subset of live frames ("keyframes")
spaced so consecutive frames overlap roughly 40-70% - enough for reliable
feature matching without stitching far more frames than necessary.

Once scanning stops, `ImageStitcher` builds one mosaic image using the
classic OpenCV panorama recipe, frame by frame:

1. Detect ORB keypoints/descriptors on the new frame.
2. Detect ORB keypoints in a *region of interest* of the growing mosaic -
   a crop near where the frame is expected to land, predicted from the
   camera-motion trajectory tracked during scanning. Restricting matching
   to this ROI (instead of the whole mosaic) is both faster and avoids
   false matches against repeated text elsewhere on the board.
3. Match descriptors (`BFMatcher`, Hamming distance, Lowe's ratio test),
   then estimate a homography with `cv.findHomography` + RANSAC.
4. If there aren't enough inlier matches (or the homography looks
   implausible - too much skew, translation far from the predicted
   position), fall back to a pure translation placement from the tracked
   trajectory rather than failing the whole stitch.
5. Warp the frame into the mosaic's coordinate space and composite it in
   (`copyTo` with the warped frame's mask - last-frame-wins in overlap
   regions; there's no multi-band seam blending, see
   [Extending](#extending-with-additional-fields) for how to add it).

The result is cropped to its non-empty bounding box and handed to
perspective correction.

### 2. Live coverage map

There's no true camera pose/SLAM here. `MotionTracker` estimates
frame-to-frame shift cheaply using phase correlation (`cv.phaseCorrelate`)
on downsampled grayscale frames, plus a Laplacian-variance sharpness score
and mean brightness/saturation for blur/lighting/glare detection - all
fast enough to run on every live-preview tick (~6 fps).

`CoverageTracker` integrates those per-tick shifts into a running
camera-center trajectory, and marks a footprint around that center as
"observed" on every good-quality tick (sharp + tracked confidently). A
sparse grid cell only turns green once it's been observed at least twice
- i.e. by two overlapping frames, mirroring the physical overlap the
stitcher itself needs. `CoverageOverlay` renders that grid directly as an
SVG (green = covered, red = still needed) scaled over the camera preview.
"Board Coverage %" is the fraction of the visited bounding box that's
green, so systematically sweeping the whole board (not just visiting many
scattered spots) is what drives the number up - the same heuristic modern
phone document-scanner apps use for their coverage heatmap.

Scanning auto-stops once coverage crosses the configured threshold (see
Settings) with a minimum frame count/time also satisfied. Stopping early
manually pauses for confirmation instead of silently producing a partial
scan.

### 3. Board detection

The stitched mosaic still has whatever skew the camera sweep left behind,
so `PerspectiveCorrector` finds the whiteboard's boundary as a
quadrilateral (Canny edges → dilate → largest 4-point contour) and warps
it to a flat rectangle sized to `boardWidthPx`/`boardHeightPx` from the
board template - the same "document scanner" recipe used by phone scanner
apps. Detection isn't always confident (low contrast against the wall,
partial occlusion, etc.), so the user always gets a corner-adjustment step
(`CornerAdjuster`) pre-filled with the detected (or a sane default) quad
before continuing - never a hard failure.

### 4. Row identification

The board layout is fixed and already known via `BoardTemplate`, so
`BoardDetector` does not attempt general table detection. It computes a
row-wise gradient-energy projection (Sobel-Y, averaged per row) to find
strong horizontal edges - the whiteboard's row-separator lines - and
snaps each template row's expected top/bottom to a nearby detected line
when one exists, falling back to pure template math (`marginTopPx +
index * rowHeightPx`) otherwise. This keeps row detection cheap and
robust to a row or two having faint/missing grid lines.

### 5. Region extraction (Project Number / Customer / Days Remaining)

Only these three regions are ever cropped or OCR'd - never the whole
board. `RegionExtractor` reads each field's rectangle from
`BoardTemplate.regions` as **percentages of the row's bounding box**
(`xPct/yPct/wPct/hPct`), so the crop rectangles automatically scale with
row height. Small crops are upscaled to a minimum width before OCR for
legibility. Each field also gets its own OCR character whitelist
(`core/ocr/validation.ts`): digits only for Project Number, a broader
mixed-case set for Customer, and digits + "Complete" for Days Remaining.

### 6. Calibrating a different whiteboard layout

Everything above is driven by `BoardTemplate` (`core/board/BoardTemplate.ts`
for the code default, editable at runtime from **Settings**) - no code
changes needed for a new board:

1. Scan the new board once and open **Settings**.
2. **Board Width / Height** - the canonical size (in px) the corrected
   board image is warped to. Pick something proportional to the real
   board's aspect ratio; higher values mean sharper crops for OCR at the
   cost of more processing time.
3. **Row Height** / **Top Margin** - vertical spacing of project rows.
   With Debug Mode on, the scan screen shows which rows snapped to a
   detected grid line vs. fell back to template math, which is the
   fastest way to dial these in.
4. **Field Regions** - for each of Project Number / Customer / Days
   Remaining, set X%/Y%/Width%/Height% *relative to a single row's box*.
   With Debug Mode on, the review screen's OCR confidence table and the
   `debugCrops` thumbnails make it obvious when a region needs nudging.
5. **OCR Confidence Threshold** / **Coverage Threshold** - how strict to
   be before a row is auto-accepted vs. flagged for manual review, and
   how much of the board must be green before auto-stop.
6. Save. The template is validated (regions must stay inside the row,
   positive size, etc.) before it's persisted.

### 7. Extending with additional fields

- **New OCR-extracted field**: add it to `BoardTemplate.regions` (type in
  `types/domain.ts`, default in `core/board/BoardTemplate.ts`), give it a
  whitelist + validator in `core/ocr/validation.ts` (mirror
  `buildProjectNumberField`), extend `RegionExtractor`'s field list, and
  add the column to `ReviewRow`/`ReviewScreen`/`EmailGenerator`. Every
  other module (camera, motion, coverage, stitching, perspective, board
  detection) is unaware of field semantics and needs no changes.
- **New OCR engine**: implement `OCRProvider` (`core/ocr/OCRProvider.ts`)
  and swap it in `ScanPipeline.runOcr` - `TesseractOCRProvider` is a
  drop-in reference implementation. Any replacement must stay local (no
  network calls) to preserve offline operation.
- **Better seam blending**: `ImageStitcher.warpAndComposite` currently
  does last-frame-wins compositing (`copyTo` with a mask). A multi-band
  or feathered blend would slot in there without touching anything else
  in the pipeline.

## Offline & OCR assets

Tesseract.js needs a worker script, a WASM core, and English trained data
at runtime. To keep OCR fully local and working offline after the first
load, none of that is fetched from a CDN: `scripts/vendor-assets.mjs`
copies those files out of `node_modules` (`tesseract.js`,
`tesseract.js-core`, `@tesseract.js-data/eng`) into `public/vendor/` at
install/build time, and `TesseractOCRProvider` points `workerPath` /
`corePath` / `langPath` at those same-origin files. `vite-plugin-pwa`
precaches everything (including the multi-MB WASM/traineddata files - see
`workbox.maximumFileSizeToCacheInBytes` in `vite.config.ts`) so the whole
scanning pipeline keeps working with no network connection after the app
has been opened once. OpenCV.js is loaded via a normal dynamic `import()`
(see `core/vision/opencvLoader.ts`), so Vite code-splits and precaches it
the same way as any other build asset - it's only fetched when a scan
actually starts, not on initial page load.

## Notes

- No image or OCR text ever leaves the device - everything above runs in
  the browser/WASM.
- The review screen never silently guesses a value: any OCR result that
  fails format validation or falls below the confidence threshold is left
  as the raw OCR text and flagged for the user to fix or verify.
- The generated email is never sent automatically - only copied to the
  clipboard or handed to a `mailto:` link the user opens themselves.
