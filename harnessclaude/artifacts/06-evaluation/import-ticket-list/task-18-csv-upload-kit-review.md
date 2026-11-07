---
module: import-ticket-list
feature: csv-upload-kit
task: 18
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 18: CsvUpload kit component

## Summary
Commit `09ab2ee` (repo `default`, range `09ab2ee^..09ab2ee`) adds `frontend/src/shared/ui/CsvUpload/` and exports it from `shared/ui/index.ts`. No later commit on the branch changes these files, so HEAD (`15d346c`) has the same code. `CsvUpload` is a `FormField` that wraps antd `Upload.Dragger`. Its value is the chosen `File`. `beforeUpload` checks the name (`.csv` in any case) and the size (over 10 MB is rejected), then returns `Upload.LIST_IGNORE`, so the Dragger never sends the file. An invalid file sets the field to `undefined` with the MSG27 or MSG28 text from the catalog. Remove clears the field. All three STORY-011 criteria have tests that check them, and the shared verify run at HEAD is green. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-011.1: you can pick a file by clicking or by dropping it (`CsvUpload.tsx:59-72`). `maxCount={1}` and a field-driven `fileList` (:45) keep only one file. The file name is listed, and `onRemove` (:69) clears the value. AC-011.2: `choose()` (:47-56) does a case-insensitive `.csv` check, rejects anything over `10 * 1024 * 1024` bytes, and sets `value: undefined` plus `errors: [msg(code)]` on the field. `accept` uses `filter: 'native'` (:62), so a dropped non-CSV file still reaches the check. AC-011.3: `beforeUpload` always returns `LIST_IGNORE` (:65-68), and the `File` reaches the form only through `onFinish`. |
| Completeness | pass | Spec D12 is fully covered: Figma Idle, Dragging, File listed and File error states (antd Dragger), the .csv and 10 MB checks with MSG27 and MSG28, MSG01 when required (:29-33), and the export from `shared/ui/index.ts`. Design edge case 37 (`REPORT.CSV`, `data.csv.txt`, exactly 10 MB, 10 MB + 1 byte) is tested. Nothing EXTRA was added. |
| Correctness | pass | `CsvUpload.test.tsx`: 9 test cases (7 `it`, one of them `it.each` × 3), all passing. Full vitest: 1074/1074. typecheck, lint (0 errors), format:check, build and generate:api (no diff) all pass. No CRITICAL finding. |
| Integration | pass | It is used by `CsvImportForm.tsx:85` (Task 19) with `name="file"`, which matches `UploadCsvImportBody.file: Blob \| File`. The generated `uploadCsvImport` appends it to `FormData`. Raw antd `Upload` appears only inside `shared/ui`, as hard rule 6 requires (lint passes). |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-008 STORY-011` → `8/11 acceptance criteria have a test`. AC-011.1–011.3 show ✗ "ambiguous: `frontend/src/shared/ui/CsvUpload/CsvUpload.test.tsx` (also fe-foundation)". fe-foundation's spec has its own AC-011.1–011.6 (Workspace site), and its `paths` (`frontend/src/shared/**`) also cover `shared/ui/CsvUpload/**`. So the tool cannot tell whose test it is. I traced these by hand: the tests are about `CsvUpload`, which this module owns.

| AC | Test (`frontend/src/shared/ui/CsvUpload/CsvUpload.test.tsx`) | Status |
|---|---|---|
| AC-011.1 | `keeps one file chosen by clicking, shows its name and lets the user remove it` (:35) · `keeps a dragged file and replaces the earlier one` (:53) | PASS |
| AC-011.2 | `does not keep %s and shows MSG27 at the field` ×3 (:67) · `reports MSG27 for a dropped file that is not a CSV file` (:80) · `keeps REPORT.CSV (any case) and a file of exactly 10 MB` (:91) · `does not keep a file larger than 10 MB and shows MSG28 at the field` (:101) | PASS |
| AC-011.3 | `never sends the file itself; the form gets the File when it is submitted` (:113), which spies on `fetch` and `XMLHttpRequest.open` | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `frontend/guidelines/07-ui-and-styling.md:64` and `frontend/guidelines/09-forms-and-validation.md:129` still say `CsvUpload` is "not built yet (FE Lead…)". Expected: the guidelines say it now exists in `shared/ui/CsvUpload` and show its props (`name`, `label`, `required`). Fix: update both lines in the same PR. These files belong to fe-foundation, so the FE Lead reviews them along with D12.
2. [Low] Traceability ID collision: `harnessclaude/artifacts/03-modules/fe-foundation/module.md` paths (`frontend/src/shared/**`) overlap `import-ticket-list` paths (`frontend/src/shared/ui/CsvUpload/**`, `DetailModal/**`), and both specs use AC-011.x. So `sdd.ts trace` reports AC-011.1–011.3 as ✗ ambiguous even though the tests exist. Fix (harness config, not code): exclude those two kit folders from fe-foundation's `paths`, or accept the manual trace above.
3. [Low] `CsvUpload.tsx:53-55`: choosing an invalid file while a valid one is listed also removes the valid file, because the field is set to `undefined`. This matches the Figma "File error" state and keeps one source of truth, but the user loses the earlier file. Spec AC-011.2 only says the bad file is not kept, so this is left as an assumption (below).

## Assumptions made
- AC-011.2 "not keep it": I read this as "the field ends up empty, showing the error". Dropping a previously kept valid file when a bad one is chosen is accepted. If the earlier file should stay, the fix is a one-line change in `choose()`: keep `value` and set only `errors`. **Open judgment call for the user.**
- The `sdd.ts trace` "ambiguous" result for AC-011.x is a tool or config limitation, not a missing test. I counted the manual trace instead of failing on it. **Open judgment call for the user** (see issue 2).
- `type: 'object'` in the required rule (`CsvUpload.tsx:31`) in place of `rules.required(label)` (design §Kit) gives the same MSG01 text. That is a design detail, not a spec deviation.

## Test Report
`task-18-csv-upload-kit-test-report.md`: the shared run at HEAD `15d346c` (second run; the first was invalid and is ignored). format:check, lint (0 errors, 1 unrelated warning), typecheck, vitest 57/57 files and 1074/1074 tests, build, and generate:api with no diff (`VERIFY-OK`, `frontend exit=0`). `CsvUpload.test.tsx` passed in 6.4 s. Coverage not measured (spec silent).
