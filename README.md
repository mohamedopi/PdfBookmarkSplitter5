# PdfBookmarkSplitter5

Android app that splits a PDF at bookmarks at the selected outline depth. Output files use bookmark titles as filenames. The app uses PDFBox Android and Android's Storage Access Framework; it does not need broad storage permission.

## Build on GitHub from a phone
1. Create a public or private repository named `PdfBookmarkSplitter5`.
2. Upload the contents of this ZIP, including the hidden `.github` directory.
3. Open Actions and run **Android CI**, or push a commit to `main`.
4. Open the successful workflow run, then download `PdfBookmarkSplitter5-APK` under Artifacts.
5. Extract the downloaded ZIP and install `app-debug.apk`. Android 10 or newer is required.

## Features
- Pick a PDF using Android's document picker.
- Read PDF outline/bookmarks and choose a depth to split at.
- Save each resulting PDF using the bookmark title.
- Optional sub-bookmark outline preservation within each resulting file.
- Choose an output folder using Android's folder picker.
- Works offline after the APK and required build dependencies have been installed/built.

## Notes
This is a new implementation based on the requested behavior, not a recovery of the unavailable original source files. Bookmark entries without resolvable page destinations are skipped as split points. The split range runs from each selected bookmark's page to the page before the next selected bookmark at the same depth; the last one runs to the end of the document.
