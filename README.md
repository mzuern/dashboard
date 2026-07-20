# Production Board Scanner (Android)

A standalone native Android app that scans a physical production
whiteboard with the phone's camera and generates a daily status report -
Project Number, Customer, and Estimated Days Remaining for each row.
Everything runs on-device: camera capture, image stitching, perspective
correction, row detection, and OCR. No server, no cloud services, no AI
APIs, no internet access required after installation.

**Read this before anything else:** [Known limitations](#known-limitations) -
none of this code has been compiled or run on a device. See why below.

## Stack

Kotlin, Jetpack Compose, CameraX, OpenCV for Android (`org.opencv:opencv`,
Maven Central), Tesseract OCR via `com.rmtheis:tess-two` (JNI wrapper,
Maven Central), Room + DataStore for local storage. MVVM with
`StateFlow`. No React Native / Flutter / Capacitor / Electron / WebView -
this is a true native app.

## Building

Standard Android Studio / Gradle workflow:

```bash
./gradlew assembleDebug
```

Expected output: `app/build/outputs/apk/debug/app-debug.apk`

Open the project root in Android Studio (Hedgehog/2023.1+ recommended)
and let it sync, or run the Gradle command above from a terminal with
`ANDROID_HOME` pointing at an installed SDK (Android Studio sets this up
for you on first run). `compileSdk`/`targetSdk` 34, `minSdk` 26.

The Gradle wrapper (`gradlew`, `gradle/wrapper/`) is included and points
at Gradle 8.9 from the standard `services.gradle.org` distribution URL.

## Installing

```bash
./gradlew installDebug   # installs to a connected device/emulator via adb
```

or drag `app-debug.apk` onto an emulator, or `adb install app-debug.apk`,
or open the APK on-device (enable "Install unknown apps" for whichever
app you transferred it with, if sideloading rather than using Android
Studio's Run button).

The app requests only the **Camera** permission on first launch. No
storage permission is used - everything is written to app-private
storage, cleaned up per the Settings screen's data controls.

## Known limitations

**This code was written in a sandboxed development environment with no
Android SDK, and no working path to install one.** Concretely: this
environment's network policy blocks `dl.google.com` (Android SDK
platform/build-tools) and `maven.google.com` redirects to that same
blocked host (needed for *every* AndroidX/Compose/CameraX artifact and
the Android Gradle Plugin itself). Maven Central worked fine, which is
why OpenCV and the OCR library resolve from there - but the app module
itself couldn't be configured, let alone compiled, in that environment.

**What this means concretely:**
- Zero compiler verification. No `./gradlew build`, no lint pass, no
  IDE red-squiggle check has touched this code. Package/class/method
  names for CameraX, OpenCV's Java bindings, Compose, and tess-two are
  written from documented API knowledge, not verified against the
  actual artifacts.
- Zero runtime verification. Nothing has been run on an emulator or
  physical device. The vision pipeline in particular (`MotionTracker`,
  `ImageStitcher`, `PerspectiveCorrector`, `BoardDetector`) is a direct
  Kotlin port of algorithms that *were* built and browser-tested in an
  earlier version of this project (a PWA using OpenCV.js - same
  algorithms, different language bindings), but the port itself is
  unverified.
- **Do the first build/run yourself in Android Studio before trusting
  any of this.** Expect to fix real compile errors - most likely
  candidates: an OpenCV Java API signature that doesn't match exactly
  (`Calib3d`/`Imgproc`/`features2d` method overloads), a CameraX
  `ResolutionSelector` API detail, or a Compose API that moved between
  versions. These should be small, mechanical fixes given the shape of
  the code is right, not a rewrite - but budget time for it.
- The four riskiest pieces, in order, matching what was flagged before
  writing any code:
  1. **Live frame tracking + stitching quality** - tuning
     `MIN_SHARPNESS`/`MIN_TRACKING_CONFIDENCE`/threshold constants in
     `ScanViewModel.kt` against a real camera and a real whiteboard.
     These values are carried over from the browser version's testing,
     not re-tuned for a phone camera.
  2. **Glare and lighting variance** - `GLARE_THRESHOLD` and
     `LOW_LIGHT_THRESHOLD` are heuristics; expect to adjust after
     testing under your actual shop lighting.
  3. **Handwritten "Days Remaining" entries** - Tesseract (any version)
     is weak on handwriting. The confidence-threshold review flow is
     the mitigation, not a fix.
  4. **Memory on a full-resolution stitch** on a mid-range phone -
     `MAX_CANVAS_DIMENSION` in `ImageStitcher.kt` caps the mosaic size
     as a safety valve, but it hasn't been load-tested.

## Physical-device testing checklist

Once it builds and installs:

- [ ] App launches, camera permission prompt appears, granting it shows
      the live preview
- [ ] Denying permission shows the explanation + "Open App Settings"
      path, and re-granting from there recovers without restarting the
      app
- [ ] Scan Board: sweep across a real whiteboard slowly - coverage map
      should trend toward green where you've actually pointed the
      camera, "Move slower"/"Hold steady" should trigger convincingly
      when you (deliberately) whip the phone around or blur the shot
- [ ] Scan auto-stops once coverage crosses the threshold, or "Done
      Scanning" works manually and (if coverage is short) shows the
      confirm-incomplete dialog
- [ ] Corner adjustment: detected corners are draggable and land where
      expected; try both a case where auto-detection finds the board
      edge and a low-contrast case where it falls back to the default
      inset quad
- [ ] Stitched board image looks like a flat document, not a curved
      panorama, after perspective correction
- [ ] Review screen: rows with genuinely unclear OCR are flagged red;
      editing a field and checking Verified clears the flag; Add/Delete
      row work
- [ ] Report screen: Copy Subject/Body actually populate the clipboard;
      "Share via Email/Other App" opens the Android share sheet and
      Gmail/Outlook/etc. all receive the right text
- [ ] Kill the app mid-review (recent apps swipe-away) and relaunch -
      the draft should still be there
- [ ] Turn on airplane mode after install, confirm a full scan → review
      → report cycle works with zero connectivity
- [ ] Settings: Calibration screen's draggable region boxes move
      correctly and persist after Save; try recalibrating for a
      differently-laid-out board
- [ ] Test on at least one small-screen and one large-screen/tablet
      device if available

## Calibration guide

The board layout is entirely data-driven via `BoardTemplate`
(`domain/BoardTemplate.kt` for the code default; editable at runtime
from **Settings → Open Calibration**) - no code changes needed for a
different whiteboard:

1. Scan the new board once (or use Manual Entry to get to Settings if a
   scan isn't practical yet).
2. Open **Settings → Open Calibration**.
3. The preview rectangle represents one project row at the board's
   configured aspect ratio. Drag each colored box (Project Number,
   Customer, Days Remaining) to where that field sits within a row;
   adjust Width%/Height% below if a box needs resizing.
4. Adjust **Board Width/Height** (the canonical resolution the corrected
   board image is warped to), **Row Height**, and **Top Margin** to
   match the real board's proportions and row spacing.
5. Save. The template is validated (regions must stay inside the row,
   positive size, etc.) before it's persisted to DataStore.
6. With **Debug Mode** on (Settings), the scan screen and review screen
   surface which rows snapped to a detected grid line vs. fell back to
   template math, plus per-field OCR confidence - the fastest way to
   tell whether a region needs nudging.

## Extending with additional fields

- **New OCR-extracted field**: add it to `FieldKey` and
  `FieldRegions`/`BoardTemplate` (`domain/BoardTemplate.kt`), give it a
  whitelist + a normalizer in `ocr/OCRValidator.kt` (mirror
  `projectNumber`), extend `RegionExtractor`'s field loop, and add the
  column to `ReviewRow`/`ReviewScreen`/`ReportGenerator`. Every other
  module (camera, motion tracking, coverage, stitching, perspective
  correction, board/row detection) is unaware of field semantics and
  needs no changes.
- **New OCR engine**: implement `ocr/OCRProvider.kt`'s interface and
  swap it in `ScanViewModel`. `TesseractOCRProvider` is a reference
  implementation; any replacement must stay fully local (no network
  calls).
- **Better seam blending**: `ImageStitcher.warpAndComposite` currently
  does last-frame-wins compositing (`Mat.copyTo` with a mask). A
  multi-band or feathered blend would slot in there without touching
  anything else in the pipeline.

## Architecture

```
app/src/main/java/com/productionboard/scanner/
  camera/      CameraController, YuvToBitmap        CameraX binding, frame conversion
  scanning/    MotionTracker, CoverageTracker,        live tracking + keyframe selection
               FrameSelector, ScanViewModel           + the pipeline orchestrator
  vision/      ImageStitcher, PerspectiveCorrector    ORB/homography mosaic + board-edge warp
  board/       BoardDetector, RegionExtractor,        row detection, field cropping,
               BoardTemplateRepository                template persistence façade
  ocr/         OCRProvider, TesseractOCRProvider,     swappable OCR engine + validation/
               OCRValidator, TessDataInstaller        normalization + asset extraction
  review/      ReviewViewModel                        editable row list + draft persistence
  report/      ReportGenerator, ReportViewModel        subject/body text + history
  storage/     ReportDatabase (Room), DraftRepository,
               ReportHistoryRepository                local persistence
  settings/    SettingsRepository, SettingsViewModel   DataStore-backed app settings
  domain/      BoardTemplate, ReviewModels,             shared data model
               AppSettings
  ui/          screens/, components/, theme/,           Compose UI
               navigation/
```

`ScanViewModel` is the only class that knows the *order* the vision
pipeline runs in (stitch → correct perspective → detect rows → extract
regions → OCR); every other module is independently usable and
testable.

## Privacy

No image, OCR text, or project data ever leaves the device - there is no
network code in this app at all. The generated report is never sent
automatically; only Copy-to-clipboard and Android's own share sheet
(which hands text to whatever app the user explicitly picks) are
provided. No analytics, crash reporting, telemetry, or ads SDKs.
