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


Release signing reads `KEYSTORE_FILE`, `KEYSTORE_ALIAS`, `KEYSTORE_PASS`, `KEY_ALIAS_PASS` env vars (or Gradle properties / `keystore.properties` from the template). `zipalign`/`apksigner` must be on PATH (`$ANDROID_HOME/build-tools/34.0.0/`).

`make clean` is aggressive: deletes `.gradle/`, `.idea/`, `*.iml` in addition to build dirs.

## Architecture

Three layers under `app/src/main/java/com/aminmart/pdftools/`:

- `ui/` — one Activity per feature (`CompressPdfActivity`, `MergePdfActivity`, `DeletePagesActivity`, `ReorderPagesActivity`), launched from `MainActivity`. All four share `PdfToolViewModel` (an `AndroidViewModel`): it holds the selected/output files, runs the operation in `viewModelScope`, and exposes a sticky `LiveData<PdfOperationResult?>` so progress/result survive rotation. The ViewModel's `resultNotified` flag prevents duplicate toasts on re-observe; `onCleared()` wipes the temp dir when the user leaves the screen (there are no `onDestroy` cleanups in Activities).
- `utils/PdfUtils.kt` — every PDF operation is a cold `Flow<PdfOperationResult>` on `flowOn(Dispatchers.IO)` emitting `Progress` updates then a terminal `Success`/`Error` (on error the output file is deleted). Merge/delete/reorder use `PdfReader` → `Document` + `PdfCopy`; compress uses `PdfReader.removeUnusedObjects()` + `PdfStamper` with `setFullCompression()` and a deflate level mapped from `CompressionLevel` (3/6/9). A new feature should copy this flow shape and be wired through `PdfToolViewModel.run(...)`.
- `utils/FileUtils.kt` — temp/output file management plus `saveToDownloads` (MediaStore, Android 10+ only — returns null below Q where the app dir is already reachable). `data/Models.kt` holds `PdfFile`, `CompressionLevel`, the `PdfOperationResult` sealed class, and `parsePageOrder` (the ordered/reversible variant of `PdfUtils.parsePageRange`; reverse ranges like "5-1" are only valid there).

Gotchas:
- The manifest declares no permissions on purpose: input comes from the SAF picker, output goes to app-specific storage or MediaStore. Don't reintroduce storage permissions.
- AGP is pinned at 8.2.2 because 8.2.0 fails `compileDebugJavaWithJavac` (jlink transform) under JDK 21.

## Tests

Unit tests live in `app/src/test/` (currently `PageParsingTest` covering `parsePageRange`/`parsePageOrder`). Run with `./gradlew test`; single class via `./gradlew test --tests "com.aminmart.pdftools.PageParsingTest"`.

## File lifecycle (strict temp-file policy)

Inputs are copied from content URIs into `cache/pdf_temp/` (`FileUtils.copyUriToTempFile`); outputs go to the app's external files dir under `PDF Tools/` (`FileUtils.createOutputFile`, which sanitizes filenames). Cleanup happens in `PdfToolViewModel.onCleared()` via `FileUtils.cleanTempFiles` — preserve this when adding features. `deleteTempFile` refuses to delete anything outside `pdf_temp`.
