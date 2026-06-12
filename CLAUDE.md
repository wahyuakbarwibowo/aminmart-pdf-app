# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (Kotlin, single `:app` module) for PDF manipulation: compress, merge, delete pages, reorder pages. Uses OpenPDF (`com.github.librepdf:openpdf`) — chosen because it's open source with no watermark. Min SDK 24, target SDK 34, JVM target 17, ViewBinding enabled. No database; everything works on temporary files.

## Commands

Makefile wraps Gradle and adb (most device targets require a connected device — they run `check` first):

```bash
make build            # ./gradlew assembleDebug (requires device connected)
./gradlew assembleDebug   # build without a device
make install          # install debug APK
make run              # install + launch MainActivity
make test             # ./gradlew test (unit tests)
make test-device      # ./gradlew connectedAndroidTest (instrumented)
make logs             # logcat filtered to app package
make release          # build + zipalign + apksigner sign (needs keystore.jks; see RELEASE.md)
```

Run a single test class: `./gradlew test --tests "com.aminmart.pdftools.SomeTest"`.

Release signing reads `KEYSTORE_FILE`, `KEYSTORE_ALIAS`, `KEYSTORE_PASS`, `KEY_ALIAS_PASS` env vars (or Gradle properties / `keystore.properties` from the template). `zipalign`/`apksigner` must be on PATH (`$ANDROID_HOME/build-tools/34.0.0/`).

`make clean` is aggressive: deletes `.gradle/`, `.idea/`, `*.iml` in addition to build dirs.

## Architecture

Three layers under `app/src/main/java/com/aminmart/pdftools/`:

- `ui/` — one Activity per feature (`CompressPdfActivity`, `MergePdfActivity`, `DeletePagesActivity`, `ReorderPagesActivity`), launched from `MainActivity`. Despite the README saying MVVM, there are no ViewModels: each Activity uses `lifecycleScope.launch` to call the suspend functions in `PdfUtils` directly, plus an Activity Result file picker (`launch("application/pdf")`).
- `utils/PdfUtils.kt` — all PDF operations as suspend functions on `Dispatchers.IO`. Every operation follows the same pattern: `PdfReader` → new `Document` + `PdfCopy` → `copy.addPage(copy.getImportedPage(reader, n))` → return `PdfOperationResult.Success(file)` or delete the output file and return `Error`. A new feature should copy this shape.
- `utils/FileUtils.kt` — temp/output file management. `data/Models.kt` holds `PdfFile`, `CompressionLevel`, the `PdfOperationResult` sealed class, and `parsePageOrder` (the ordered/reversible variant of `PdfUtils.parsePageRange`).

Gotchas:
- The big PDF operations in `PdfUtils` (`compressPdf`, `mergePdfs`, `deletePages`, `reorderPages`) are dead code: each Activity reimplements the same logic inline as a private `Flow` (e.g. `compressPdfWithProgress`) to get progress updates. The `PdfOperationResult.Progress(...)` expressions inside `PdfUtils` are created and discarded — nothing collects them. Fix bugs in the Activity flows, not (only) in `PdfUtils`.
- The Activity flows have no `flowOn(Dispatchers.IO)`, so PDF processing runs on the main thread.
- Compression is nominal: OpenPDF's `PdfCopy` applies only its default compression, and `CompressionLevel` is not actually used to vary it.
- No test sources exist (`app/src/test/` and `app/src/androidTest/` are absent) despite test dependencies being declared.

## File lifecycle (strict temp-file policy)

Inputs are copied from content URIs into `cache/pdf_temp/` (`FileUtils.copyUriToTempFile`); outputs go to the app's external files dir under `PDF Tools/` (`FileUtils.createOutputFile`, which sanitizes filenames). Activities must clean temp files in `onDestroy()` via `FileUtils.cleanTempFiles` — preserve this when adding features. `deleteTempFile` refuses to delete anything outside `pdf_temp`.
