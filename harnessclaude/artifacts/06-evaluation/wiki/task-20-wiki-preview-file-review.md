---
module: wiki
feature: wiki-preview-file
task: 20
verdict: pass
attempt: 1
date: 2026-11-23
reviewer: harnessclaude-reviewer
---

# Review: wiki — Task 20: First page of the original file in the 11.1 preview pane

## Summary
`WikiPreview` now renders the shared `WikiFileView` with `variant="preview"` and passes the 420-character excerpt as the fallback. A PDF opens in the browser's own viewer at `#page=1&view=FitH&toolbar=0`, in an A4-ratio box. A DOCX keeps only the first `section` that `docx-preview` renders, and that page is zoomed to the pane's width; a `ResizeObserver` redoes the fit when the pane changes size and is disconnected on cleanup. `key={documentId}` resets the failed state for each row. 11.2 uses the default `variant="full"`, so its code path is unchanged apart from the extra effect dependency. All frontend checks pass, and every STORY-002 criterion has a test. There are no blocking findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.24: the title and details, the first page of the file at the pane's width, Open and Download are shown; `?doc=` and the first-row default are unchanged. AC-002.27: the excerpt is shown when the file is missing (`file.isError`), when the blob is empty, or when DOCX rendering fails (`.catch → setFailed`). AC-002.25 is unchanged (`WikiPreview.tsx:25-27`). |
| Completeness | pass | Both criteria of the task are implemented. The decisions in rows 5, 5a and 5b are followed, except that zoom is used instead of a CSS transform (Issue 2). |
| Correctness | pass | 2011/2011 tests, lint, typecheck, format, build and generate:api all pass. The zoom fit was checked in headless Chrome and does not oscillate. |
| Integration | pass | Frontend only. It uses the existing `useDownloadWikiDocumentFile` and adds no new dependency or API change. The 11.2 tests for AC-002.19 and AC-002.26, and those for BUG-356, BUG-357 and BUG-255, still pass. |

## Traceability
```
trace wiki (STORY-002): 27/27 acceptance criteria have a test
  AC-002.19  frontend/src/features/wiki/pages/WikiDocumentPage.test.tsx, WikiHomePage.test.tsx
  AC-002.24  frontend/src/features/wiki/pages/WikiHomePage.test.tsx
  AC-002.25  frontend/src/features/wiki/pages/WikiHomePage.test.tsx
  AC-002.26  frontend/src/features/wiki/pages/WikiDocumentPage.test.tsx
  AC-002.27  frontend/src/features/wiki/pages/WikiHomePage.test.tsx
  (AC-002.1 to AC-002.23 also have tests; see the test report)
```

## Issues
None blocking. Non-blocking observations:

1. **Low — the render-failure branch of AC-002.27 is not tested on 11.1.** `frontend/src/features/wiki/pages/WikiHomePage.test.tsx:535` covers only a download that fails (404). The other half of the criterion, a DOCX that `docx-preview` cannot render, goes through the same `.catch(() => setFailed(true))` (`WikiFileView.tsx:69`), which is tested only on 11.2 (`WikiDocumentPage.test.tsx:171`). Suggested: add `it('AC-002.27 shows the first 420 characters when the DOCX cannot be rendered')` with `renderDocx.mockRejectedValue(...)`.
2. **Low — the code differs from the task description.** `tasks.md` Task 20 and decision 5a say "CSS transform"; `WikiFileView.tsx:63` sets `style.zoom`. Zoom works in current browsers (Chrome; Firefox 126 and later), and it shrinks the layout box as well, which a transform would not do without a separate height fix. Suggested: update the task text or decision row 5a to say `zoom`. The code needs no change.
3. **Info — the scaling is not covered by automated tests.** jsdom has no layout, so `fit()` does nothing in the test suite. The reviewer checked it in headless Chrome (see the test report).
4. **Info — the PDF hints depend on the browser's viewer.** Firefox's pdf.js honours `page` and `view` but ignores `toolbar=0`. The spec only requires "opened at page 1", so this is acceptable.

## Assumptions made
- "Scaled to the pane's width" for a PDF is met by `view=FitH` in a box with an A4 aspect ratio. The browser's viewer does the scaling; jsdom cannot check it.
- A DOCX page narrower than the pane is not scaled up (`Math.min(1, …)`). I read "scaled to the pane's width" as "fits the width", not "fills the width". The user may want to confirm this.
- Re-downloading the file for each previewed row is the accepted cost recorded in decision row 5 (the user, 2026-11-23). I did not treat it as a performance finding.

## Test Report
See `task-20-wiki-preview-file-test-report.md`. format:check, lint (0 errors), typecheck, test (122 files, 2011 tests), build and generate:api (no diff) all pass, and trace is 27/27.
