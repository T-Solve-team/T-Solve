# View images in Wiki documents — decisions (2026-11-22)

Request (user): the Wiki should show the images inside uploaded documents.
Decided by Claude under the auto-decide rule; review and override any row.

| # | Question | Options | Choice | Why |
|---|----------|---------|--------|-----|
| 1 | How to show images | (a) render the original file on 11.2; (b) extract images at upload and store them; (c) docling Markdown with embedded images | (a) | No backend, storage or indexing change; images stay in place with tables and layout |
| 2 | PDF viewer | (a) browser's built-in viewer in a `blob:` iframe; (b) pdf.js (`pdfjs-dist`) | (a) | Native, no dependency; needs CSP `frame-src blob:` |
| 3 | DOCX viewer | (a) `docx-preview` (Apache-2.0, needs jszip); (b) `mammoth` (HTML only, loses layout); (c) convert to PDF on the server (LibreOffice) | (a) | Browsers cannot show DOCX; keeps pages, images and tables; no server change |
| 4 | Fallback | plain text when the file fails to load or render, or the document is in the trash (file answers 404) | — | Keeps 11.2 readable; AC-002.26 |
| 5 | 11.1 preview pane | ~~keep the 420-character text~~ — revised 2026-11-23 (KAN-372, user): (a) first page of the original file; (b) full file scrolling in the pane; (c) 420-character text | (a) | The pane should look like the opened document. Each selected row downloads its file once (React Query caches it, so Open fetches nothing again). AC-002.24, AC-002.27 |
| 5a | DOCX in the 360px pane | (a) first page only, scaled to the pane width; (b) all pages scaled; (c) real size, scroll sideways | (a) | User, 2026-11-23. `docx-preview` renders every page; the pane keeps the first `section` and scales it down with CSS `zoom` (never above 1) |
| 5b | PDF in the pane | (a) native viewer at `#page=1&view=FitH&toolbar=0`, about one page tall; (b) `pdfjs-dist`, page 1 on a canvas | (a) | User, 2026-11-23. No new dependency; the viewer still lets the user scroll to later pages |
