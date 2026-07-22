# Production Board Photo-to-Email (Android)

A standalone native Android app that turns photos of a physical
production whiteboard into an editable daily status email - Project
Number, Customer, and Estimated Days Remaining for each row. Everything
runs on-device: photo capture/selection, image cropping and perspective
correction, row detection, and OCR. No server, no cloud services, no AI
APIs, no internet access required after installation.

**Read this before anything else:** [Known limitations](#known-limitations) -
none of this code has been compiled or run on a device. See why below.

## Workflow

1. **Take or choose photos** of the whiteboard. The board doesn't need
   to fit in one photo - take as many as you need to cover it, from
   whatever angle is convenient.
2. **Process Photos** - each photo is read locally and run through the
   extraction pipeline independently.
3. **Review** the extracted rows: correct any field, delete a false
   row, mark a row "Complete" or "Unknown", remove duplicates (the app
   flags likely duplicates across photos for you), or add a missed
   project by hand. Uncheck "Include" on any row you don't want in the
   email.
4. **Generate the email** - included rows become a subject + body you
   can copy or hand off to any app via Android's share sheet.

There is no automatic sending, no photo upload, no account, and no
report history - this is a single-pass tool: photos in, email text out.

## Stack

Kotlin, Jetpack Compose, the system Camera app (via a `TakePicture`
intent + `FileProvider`) and Android's Photo Picker for photo
acquisition, OpenCV for Android (`org.opencv:opencv`, Maven Central) for
perspective correction, Tesseract OCR via `com.rmtheis:tess-two` (JNI
wrapper, Maven Central), Room + DataStore for local storage. MVVM with
`StateFlow`. No React Native / Flutter / Capacitor / Electron / WebView -
this is a true native app, and there is no CameraX dependency since
photo capture is handed off to the system Camera app.

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

**The app itself requests no runtime permissions.** "Take Photo" launches
the system Camera app (which handles its own camera permission) and
writes the result to this app's private storage via `FileProvider`;
"Choose Photos" uses Android's Photo Picker, which needs no storage
permission at all. Everything is written to app-private cache storage,
optionally cleared automatically after generating an email (Settings).

## Known limitations

**This code was written in a sandboxed development environment with no
Android SDK, and no working path to install one.** Concretely: this
environment's network policy blocks `dl.google.com` (Android SDK
platform/build-tools) and `maven.google.com` redirects to that same
blocked host (needed for *every* AndroidX/Compose artifact and the
Android Gradle Plugin itself). Maven Central worked fine, which is why
OpenCV and the OCR library resolve from there - but the app module
itself couldn't be configured, let alone compiled, in that environment.

**What this means concretely:**
- Zero compiler verification. No `./gradlew build`, no lint pass, no
  IDE red-squiggle check has touched this code. Package/class/method
  names for Compose, OpenCV's Java bindings, tess-two, and the
  `FileProvider`/Photo Picker/`TakePicture` APIs are written from
  documented API knowledge, not verified against the actual artifacts.
- Zero runtime verification. Nothing has been run on an emulator or
  physical device.
- **Do the first build/run yourself in Android Studio before trusting
  any of this.** Expect to fix real compile errors - most likely
  candidates: an OpenCV Java API signature that doesn't match exactly
  (`Imgproc` method overloads), a Compose API that moved between
  versions, or a `FileProvider` path/authority mismatch. These should be
  small, mechanical fixes given the shape of the code is right, not a
  rewrite - but budget time for it.
**One thing *was* tested despite all that:** the row/column-detection and
OCR *algorithms* (not the compiled Android app - that's still unbuilt)
were ported to a throwaway Python/OpenCV/pytesseract script and run
against real photos of an actual production whiteboard. That caught and
fixed two real bugs before they ever reached a device:
- `BoardDetector`'s row-divider detection originally picked the
  strongest Sobel-Y gradient in the search window, which regularly
  locked onto a card's own internal grid line instead of the true
  board divider on a real (imperfectly flat, sometimes curled) card. It
  now averages brightness across each full row and uses Otsu's
  threshold on that row-brightness profile to separate "true divider"
  rows from everything else - verified to find all the real dividers
  across four different photos with different lighting.
- `TesseractOCRProvider` was using `PSM_SINGLE_LINE`, which frequently
  returned nothing at all on bold marker handwriting; `PSM_SINGLE_BLOCK`
  reliably recognized the same crops instead (e.g. a correct "66455" at
  90% confidence vs. empty output). `PhotoProcessor` also now tries an
  Otsu-binarized crop first and only falls back to adaptive
  thresholding if that doesn't pass validation, to cover glare/uneven
  lighting without giving up a clean crop's better result.

The riskiest pieces that remain, roughly in order:
  1. **Handwriting recognition accuracy is a real, measured ceiling, not
     just a theoretical risk.** Even after the fixes above, confidence
     on real handwritten digits/customer names from that test photo set
     stayed low (0-50%) and several characters were genuinely ambiguous
     (5 vs. S vs. 8, 6 vs. 5) even to a human eye. Expect nearly every
     row to land in "Needs Review" under the default 65% threshold -
     that's the intended safety net working, but it means this app is
     closer to "smart cropper + rough first guess" than "autofill," and
     users should expect to correct most rows by hand.
  2. **A physically crooked/curled card clips a fixed-fraction column
     box.** One row in the test photos had a card taped on at a visible
     angle relative to the board's own grid, which cut off a leading
     digit at that row's calibrated column boundary. No per-row
     correction exists for this (only whole-photo rotation/perspective);
     it's a framing/taping reality to be aware of, not a bug to fix.
  3. **Perspective correction trigger** - `PhotoProcessor` only warps
     the board-area crop when `PerspectiveCorrector` finds a confident
     4-point board edge (`areaRatio > 0.5`); a photo where the board
     doesn't fill enough of the frame silently skips correction and
     uses the raw crop, which may skew row alignment. Untested on-device.
  4. **Calibration usability** - the drag-to-define board
     area/row-spacing/column-region flow in `CalibrationScreen` is new
     UI that hasn't been tried on an actual touchscreen against a real
     sample photo.

## Physical-device testing checklist

Once it builds and installs:

- [ ] "Take Photo" launches the system Camera app and the captured photo
      appears as a thumbnail back in the app
- [ ] "Choose Photos" opens the system Photo Picker and multi-selecting
      photos adds all of them as thumbnails
- [ ] Removing a thumbnail deletes it from the selection; rotating a
      thumbnail visibly rotates the preview and carries through to
      processing
- [ ] "Process Photos (N)" runs OCR locally with no network activity
      (try airplane mode) and lands on the Review screen
- [ ] Review screen: rows with genuinely unclear OCR are flagged
      "Needs Review"; possible duplicates across photos are flagged
      "Possible Duplicate"; editing a field clears the relevant flag;
      unchecking "Include" removes a row from the generated email
      without deleting it; Add/Delete row work; tapping a row's source
      thumbnail shows the cropped source image it was read from
- [ ] "+ Add More Photos" returns to photo capture, and processing the
      new photos merges the new rows into the existing review list
      (including re-running duplicate detection against the combined
      set) rather than replacing it
- [ ] Email screen: Copy Subject/Body actually populate the clipboard;
      "Share" opens the Android share sheet and Gmail/Outlook/etc. all
      receive the right text
- [ ] Kill the app mid-review (recent apps swipe-away) and relaunch -
      the draft should still be there
- [ ] Settings: "Delete photos from this device after generating the
      email" actually clears app-private photo storage when enabled,
      and leaves photos in place when disabled
- [ ] Settings → Calibration: the board-area rectangle, first-row/
      row-height lines, and the three column regions are all draggable
      against a real sample photo and persist after Save; try
      recalibrating for a differently-laid-out board
- [ ] Test on at least one small-screen and one large-screen/tablet
      device if available

## Calibration guide

The board layout is entirely data-driven via `BoardTemplate`
(`domain/BoardTemplate.kt` for the code default; editable at runtime
from **Settings → Open Calibration**) - no code changes needed for a
different whiteboard. All positions are stored as fractions (0-1) of the
photo/crop they were defined against, so one calibration keeps working
across photos of different sizes or framing.

1. Take or choose at least one sample photo of the board on the main
   screen first - Calibration uses your first selected photo as the
   backdrop for defining regions.
2. Open **Settings → Open Calibration**.
3. **Board area**: drag the rectangle to outline just the whiteboard
   itself within the photo (crop out background/wall/floor).
4. **Row spacing**: drag the "first row" line to the top of the first
   project row, and the "second row" line to the top of the next row -
   the row height is derived from the gap between them and reused for
   every row below.
5. **Columns**: drag the three colored boxes (Project Number, Customer,
   Days Remaining) to where each field sits within a single row; adjust
   Width%/Height% below if a box needs resizing.
6. Save. The template is validated (regions must have positive size and
   stay within bounds, row height must be non-trivial, etc.) before
   it's persisted to DataStore.

## Extending with additional fields

- **New OCR-extracted field**: add it to `FieldKey` and
  `FieldRegions`/`BoardTemplate` (`domain/BoardTemplate.kt`), give it a
  whitelist + a normalizer in `ocr/OCRValidator.kt` (mirror
  `projectNumber`), extend `RegionExtractor`'s field loop, and add the
  column to `ReviewRow`/`ReviewScreen`/`EmailGenerator`. Calibration UI
  in `CalibrationScreen` would also need a fourth draggable region.
- **New OCR engine**: implement `ocr/OCRProvider.kt`'s interface and
  swap it in `ProcessingViewModel`. `TesseractOCRProvider` is a
  reference implementation; any replacement must stay fully local (no
  network calls).
- **Different duplicate-matching rules**: `DuplicateDetector` is a
  single, self-contained object - the exact-project-number and
  similar-customer/nearby-days heuristics can be tuned or replaced there
  without touching the rest of the pipeline.

## Architecture

```
app/src/main/java/com/productionboard/scanner/
  photo/       SelectedPhoto, PhotoStorage,          photo acquisition (camera
               PhotoCaptureViewModel                 intent + Photo Picker) + app-private
                                                      cache storage
  processing/  ImageLoader, PhotoProcessor,           per-photo pipeline: EXIF/manual
               DuplicateDetector                      rotation, crop, perspective
                                                       correction, OCR, dedup across photos
  vision/      PerspectiveCorrector                   board-edge quad detection + warp
  board/       BoardDetector, RegionExtractor,         row detection, field cropping,
               BoardTemplateRepository                 template persistence façade
  ocr/         OCRProvider, TesseractOCRProvider,      swappable OCR engine + validation/
               OCRValidator, TessDataInstaller         normalization + asset extraction
  review/      ReviewViewModel                         editable row list + draft persistence
  report/      EmailGenerator                          subject/body text generation
  storage/     DraftDatabase (Room), DraftRepository    local single-draft persistence
  settings/    SettingsRepository, SettingsViewModel    DataStore-backed app settings
  domain/      BoardTemplate, ReviewModels,             shared data model
               AppSettings
  ui/          screens/, components/, theme/,           Compose UI
               navigation/
```

`PhotoProcessor` is the only class that knows the *order* a single
photo's pipeline runs in (orientation correction → crop to board area →
best-effort perspective correction → detect rows → extract regions →
OCR); `ProcessingViewModel` runs it once per photo and combines the
results, and `DuplicateDetector` flags likely duplicates across the
combined set. Every other module is independently usable and testable.

## Privacy

No image, OCR text, or project data ever leaves the device - there is no
network code in this app at all. The generated email is never sent
automatically; only Copy-to-clipboard and Android's own share sheet
(which hands text to whatever app the user explicitly picks) are
provided. No analytics, crash reporting, telemetry, or ads SDKs. Photos
live in app-private cache storage and can be auto-cleared after
generating an email (Settings), or removed individually at any time.
