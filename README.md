# Production Board Photo-to-Email (Android)

[![Android Build](https://github.com/mzuern/dashboard/actions/workflows/build.yml/badge.svg)](https://github.com/mzuern/dashboard/actions/workflows/build.yml)

A native Android prototype that turns photos of a physical production whiteboard into an editable daily-status email. The entire image-processing workflow runs on-device: photo capture/selection, board calibration, perspective correction, row detection, OCR, review, duplicate detection, and email text generation.

There is no backend, cloud service, AI API, account system, telemetry, or automatic email sending.

> **Project status:** working prototype / engineering portfolio project. The repository includes automated Android builds, unit tests, real-photo algorithm validation, and device-feedback-driven UI fixes. It is not presented as a deployed production system.

## What this project demonstrates

This project was built around a few deliberate engineering constraints:

- **Local-first / privacy-first processing.** Images and OCR text stay on the device.
- **Human review where automation is uncertain.** Low-confidence OCR is surfaced for correction instead of silently treated as authoritative.
- **Replaceable components.** OCR is behind an `OCRProvider` interface; board layout is data-driven rather than hard-coded to one photograph.
- **Separation of concerns.** Photo acquisition, image processing, board detection, OCR, review state, persistence, reporting, and settings are separate modules.
- **Recoverable workflow state.** A partially reviewed batch is persisted locally so an interrupted session can resume.
- **Testability.** Domain validation, OCR normalization, and email generation have unit-test coverage, while Android builds run in GitHub Actions.
- **Iteration from evidence.** Real board photos exposed failure modes in row-divider detection and OCR segmentation; those observations changed the implementation rather than being hidden behind optimistic assumptions.

## Workflow

1. **Take or choose photos** of the board. Multiple photos can cover different sections.
2. **Process photos** locally.
3. **Review extracted rows** and correct uncertain fields.
4. **Resolve duplicates / omissions** and choose which rows to include.
5. **Generate email text** and copy it or hand it to another Android app through the system share sheet.

The application intentionally stops short of automatic sending. A human remains in control of the final content and destination.

## Stack

- Kotlin
- Jetpack Compose
- MVVM + `StateFlow`
- OpenCV for Android
- Tesseract OCR through `tess-two`
- Room for draft persistence
- DataStore for settings/calibration
- Android Photo Picker
- System camera via `TakePicture` + `FileProvider`
- Gradle / GitHub Actions

No React Native, Flutter, WebView, cloud OCR, or external API is required for the application workflow.

## Architecture

```text
app/src/main/java/com/productionboard/scanner/
  photo/       photo acquisition + app-private image storage
  processing/  pipeline orchestration + duplicate detection
  vision/      perspective correction
  board/       row detection + calibrated field extraction
  ocr/         OCR interface, Tesseract implementation, validation
  review/      editable review state + recovery
  report/      email text generation
  storage/     Room-backed draft persistence
  settings/    DataStore-backed settings and calibration
  domain/      shared models and validation
  ui/          Compose screens, components, navigation, theme
```

The main single-photo path is deliberately explicit:

```text
photo
  -> orientation correction
  -> calibrated board crop
  -> best-effort perspective correction
  -> row detection
  -> field-region extraction
  -> OCR
  -> normalization / confidence checks
  -> human review
  -> report text
```

`PhotoProcessor` owns the ordering of the image pipeline. Other modules own their individual responsibilities rather than duplicating workflow logic.

## Verification status

### Automated Android build

The repository contains a GitHub Actions workflow that checks out the project, installs JDK 17 and the Android SDK, and runs:

```bash
./gradlew assembleDebug --stacktrace
```

The repository history includes successful Android build runs. See the **Actions** tab for the current build status.

### Unit tests

Current JVM unit tests include coverage for:

- board-template validation and resolution-independent geometry;
- OCR validation/normalization behavior;
- email generation.

Run locally with the Android/Gradle toolchain available.

### Real-image algorithm checks

Before relying on the Android UI, the row-detection and OCR ideas were exercised against actual photos of a physical board using a Python/OpenCV/Tesseract test harness.

That testing exposed concrete problems:

- A Sobel-gradient row-divider approach could lock onto a card's internal line instead of the actual board divider. The implementation was changed to use full-row brightness profiling plus Otsu thresholding.
- Tesseract's single-line mode frequently failed on bold handwritten marker text. The OCR strategy was changed to use block mode and to try an Otsu-binarized crop before the adaptive-threshold fallback.

The point of those checks was not to claim perfect OCR; it was to find where the design failed against real input and adjust accordingly.

### Device feedback

Calibration UI was revised after device feedback so a calibration image can be captured/selected directly from the calibration screen and draggable overlays remain readable over the photo.

## Known limitations

The most important limitation is **handwriting recognition accuracy**. On the test photos, some handwritten fields remained ambiguous even after preprocessing. The review screen is therefore a core part of the design, not an afterthought.

Other remaining concerns include:

- cards taped at an angle relative to the board grid;
- perspective-correction confidence on poorly framed photographs;
- usability of calibration across different board layouts and screen sizes;
- broader physical-device regression testing.

This project intentionally favors a visible `Needs Review` state over silently fabricating confidence.

## Build

With Android Studio / Android SDK installed:

```bash
./gradlew assembleDebug
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install to a connected device/emulator with:

```bash
./gradlew installDebug
```

## Calibration

The board geometry is represented by a `BoardTemplate` using fractional coordinates rather than fixed pixels. Calibration defines:

- board crop;
- first-row position;
- row spacing;
- Project Number region;
- Customer region;
- Days Remaining region.

Because the geometry is stored as fractions of the calibrated image/crop, the same template can be reused across different photo resolutions.

## Privacy boundary

The app itself does not upload board photos or OCR results. Data remains in app-private storage until the user explicitly copies or shares generated text through Android's system mechanisms.

This is also why the application does not include automatic email delivery, analytics, advertising SDKs, or cloud OCR.

## Why this repository is public

This repository is a portfolio example of how I approach a practical engineering problem:

1. define constraints first;
2. isolate responsibilities;
3. make uncertain automation reviewable;
4. test assumptions against real inputs;
5. preserve privacy by default;
6. document limitations instead of overstating readiness.
