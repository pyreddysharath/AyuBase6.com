Libraries served from this site (so the app and website work offline and do not depend on public CDNs).

pdfjs/      pdf.js 3.11.174 (Mozilla, Apache-2.0)  — reads lab report PDFs
jspdf/      jsPDF 2.5.1 (MIT)                      — the Download PDF buttons
tesseract/  tesseract.js 5.1.1 + core 5.1.1 (Apache-2.0) and English data 4.0.0_best_int — reads photos and scanned reports (OCR)
            tesseract-core-simd-lstm.wasm.js  for phones/browsers with WebAssembly SIMD (almost all)
            tesseract-core-lstm.wasm.js       fallback without SIMD
            eng.traineddata.gz                English reading data

The Android app bundles pdfjs/, jspdf/ and tesseract/{tesseract.min.js,worker.min.js}; the OCR engine and English data
are fetched from here when first needed (or with "Enable offline reading") and kept on the phone.
