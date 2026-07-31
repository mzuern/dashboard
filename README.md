# Production Board Scanner (Android)

A standalone Android app that turns a physical production job board into an editable daily status email containing Project Number, Customer, and Estimated Days Remaining. Processing stays on the phone.

## Primary workflow

1. Tap **Scan Board**.
2. Start near the upper-left area of the board and move slowly across it in overlapping strips, keeping roughly half of the previous view visible.
3. The app analyzes the live CameraX preview and automatically keeps useful frames while rejecting frames that are too blurry, too glared-out, or too close to the last retained frame.
4. Tap **Use Scan** when the board is covered.
5. The retained frames are assembled into a planar board mosaic using the motion positions measured during capture.
6. The stitched mosaic is treated as the board coordinate system itself; it is not cropped using stale calibration coordinates from another photograph.
7. Horizontal board dividers are detected directly from the mosaic, each job row is extracted, and the calibrated Project / Customer / Days column proportions are OCR'd locally.
8. Review/correct uncertain rows and generate the email.

The older **Take Photo** and **Choose Photos** paths remain available as fallbacks. Ordinary photos are processed independently; only frames created by the guided scanner are automatically mosaicked.

## Why guided scanning

A full-board photograph makes handwriting too small for dependable OCR. Guided scanning lets the user stand closer to the board so each retained frame contains substantially more text detail, while overlap provides enough shared image content to reconstruct the larger board.

This is closer to scanning a large document than taking a conventional panorama. The app therefore builds a flat/planar mosaic rather than using a spherical panorama projection.

## Current architecture

```text
CameraX preview
    ↓
MotionTracker
  - frame-to-frame phase correlation
  - blur/sharpness score
  - glare score
    ↓
FrameSelector
  - keeps useful overlapping frames
  - stores tracked X/Y position
    ↓
BoardStitcher
  - planar motion-position mosaic
    ↓
BoardDetector.detectRowsAuto
  - finds real horizontal job dividers
    ↓
RegionExtractor
  - Project Number
  - Customer
  - Days Remaining
    ↓
Tesseract OCR
    ↓
Review / correct
    ↓
EmailGenerator
```

## Calibration

Guided scanning reduces what calibration needs to accomplish. Photo-relative board crop coordinates and first-row position are not used on the stitched scan. The important reusable calibration is the **column geometry inside a row**: where Project Number, Customer, and Days Remaining appear horizontally.

The existing calibration screen is still present while that UI is being simplified. For ordinary single-photo fallback processing, its older board-area and row-spacing values are still used.

## Privacy

No board image, OCR text, or project data is sent to a server. Camera analysis, stitching, OCR, review, and email generation happen locally. The app only hands the final text to another app when the user explicitly chooses Share.

## Build

```bash
./gradlew assembleDebug
```

The GitHub Actions Android build now runs on pull requests as well as pushes to `main` so scanner changes can be compiler-checked before merge.

## Device-validation checklist for guided scan

- [ ] Camera permission request appears only when opening Scan Board.
- [ ] Live rear-camera preview fills the scan screen.
- [ ] Moving too quickly produces a slow-down instruction.
- [ ] A blurry frame is rejected rather than counted.
- [ ] Strong glare produces a tilt-phone instruction.
- [ ] Useful frame count increases as the phone moves across the board.
- [ ] Scanning left-to-right and then down/back produces sensible tracked placement.
- [ ] **Use Scan** saves selected frames and moves to processing.
- [ ] The resulting mosaic has the correct orientation and does not reverse horizontal/vertical motion.
- [ ] Overlap seams do not cut through OCR text badly enough to affect row reading.
- [ ] Real horizontal board dividers become detected job rows.
- [ ] Project / Customer / Days crops line up with the correct columns.
- [ ] Low-confidence OCR lands in Review rather than being silently trusted.
- [ ] Review corrections flow into the generated email.

## Known risks / next validation work

The architecture now matches the intended workflow, but the guided scanner still needs physical-device testing against the actual board. The most important measurements are the sign/scale of CameraX phase-correlation motion on the target phone, how well simple planar placement handles small phone rotations, and how reliably full-width divider detection survives mosaic seams. Those should be tuned from real scan frames rather than guessed in code.
