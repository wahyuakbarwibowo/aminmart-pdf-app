# PDF Tools - Android Application

A Kotlin Android application for PDF manipulation operations including compress, merge, and delete pages functionality.

## Features

- **Compress PDF**: Reduce PDF file size with adjustable compression levels (Low, Medium, High)
- **Merge PDF**: Combine multiple PDF files into a single document
- **Delete Pages**: Remove specific pages from a PDF file using page numbers or ranges
- **Reorder Pages**: Rearrange pages using a custom order, including reverse ranges like `5-1`

## Technical Details

- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **PDF Library**: OpenPDF (open source, no watermark)
- **Architecture**: MVVM with Coroutines
- **No Database**: All operations use temporary files that are cleaned up after use

## Project Structure

```
app/
├── src/main/
│   ├── java/com/aminmart/pdftools/
│   │   ├── data/
│   │   │   └── Models.kt          # Data classes
│   │   ├── utils/
│   │   │   ├── FileUtils.kt       # File management utilities
│   │   │   └── PdfUtils.kt        # PDF operations
│   │   └── ui/
│   │       ├── MainActivity.kt          # Main menu
│   │       ├── CompressPdfActivity.kt   # Compress feature
│   │       ├── MergePdfActivity.kt      # Merge feature
│   │       ├── DeletePagesActivity.kt   # Delete pages feature
│   │       ├── ReorderPagesActivity.kt  # Reorder pages feature
│   │       ├── PdfToolViewModel.kt      # Shared ViewModel (survives rotation)
│   │       └── PdfFileAdapter.kt        # RecyclerView adapter
│   ├── res/
│   │   ├── layout/              # UI layouts
│   │   ├── values/              # Strings, colors, themes
│   │   ├── xml/                 # FileProvider paths
│   │   └── drawable/            # Icons
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## Storage Management

The app follows a strict temporary file policy:

1. **Input files** are copied to the app's cache directory (`cache/pdf_temp/`)
2. **Output files** are saved to the app's external files directory (`Android/data/com.aminmart.pdftools/files/PDF Tools/`); on Android 10+ the Download button also publishes a copy to the system Downloads folder via MediaStore
3. **Automatic cleanup**: Temporary files are deleted when the user leaves a feature screen (the ViewModel is cleared)

## Permissions

None. Input files are selected through the system file picker (Storage Access
Framework) and outputs are written to app-specific storage or published via
MediaStore, neither of which requires a runtime permission.

## Building the App

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Steps

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on device or emulator

```bash
# Or build from command line
./gradlew assembleDebug
```

## Usage

### Compress PDF
1. Tap "Compress PDF" on the main menu
2. Select a PDF file
3. Choose compression level (Low/Medium/High)
4. Enter output filename (optional)
5. Tap "Process"
6. Tap "Download" to view the compressed file

### Merge PDF
1. Tap "Merge PDF" on the main menu
2. Tap "Add Files" and select multiple PDFs
3. Arrange files in desired order (files are processed in selection order)
4. Tap "Process"
5. Tap "Download" to view the merged file

### Delete Pages
1. Tap "Delete Pages" on the main menu
2. Select a PDF file
3. Enter pages to delete using formats like:
   - `1,3,5` - Delete specific pages
   - `2-4` - Delete page range
   - `1,3-5,8` - Combination
4. Preview shows which pages will be deleted
5. Tap "Process" and confirm
6. Tap "Download" to view the result

## Dependencies

- **AndroidX**: Core libraries and Material Design components
- **OpenPDF** (`com.github.librepdf:openpdf:1.3.30`): PDF manipulation
- **Kotlin Coroutines**: Async operations

## License

This project is open source and available for educational purposes.

## Notes

- OpenPDF library has some limitations compared to commercial PDF libraries
- Compression effectiveness depends on the source PDF structure
- Large PDF files may take longer to process
