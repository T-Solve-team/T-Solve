# View images in Wiki documents — decisions (2026-11-22)

Request (user): the Wiki should show the images inside uploaded documents.
Decided by Claude under the auto-decide rule; review and override any row.

| # | Question | Options | Choice | Why |
|---|----------|---------|--------|-----|
| 1 | How to show images | (a) render the original file on 11.2; (b) extract images at upload and store them; (c) docling Markdown with embedded images | (a) | No backend, storage or indexing change; images stay in place with tables and layout |
| 2 | PDF viewer | (a) browser's built-in viewer in a `blob:` iframe; (b) pdf.js (`pdfjs-dist`) | (a) | Native, no dependency; needs CSP `frame-src blob:` |
| 3 | DOCX viewer | (a) `docx-preview` (Apache-2.0, needs jszip); (b) `mammoth` (HTML only, loses layout); (c) convert to PDF on the server (LibreOffice) | (a) | Browsers cannot show DOCX; keeps pages, images and tables; no server change |
| 4 | Fallback | plain text when the file fails to load or render, or the document is in the trash (file answers 404) | — | Keeps 11.2 readable; AC-002.26 |
| 5 | 11.1 preview pane | keep the 420-character text | — | The pane is a short preview; showing the file there would load every selected file |
