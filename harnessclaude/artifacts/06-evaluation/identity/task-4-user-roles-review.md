# Task 4 review — User roles (KAN-110)

**Module:** identity
**Reviewer:** independent reviewer agent; parent verified output
**Iteration:** 1
**Verdict:** PASS after fixes; full verification is a separate gate

## Spec compliance
AC-007.1–7 covered by named UI and API tests: confirmed edit values, Employee/PM/KM validation, scoped handovers, self/last Admin, actual TR confirmation/removal with retained history, immediate session refresh and error/cancel/retry.

## Quality review
Shared popup, generated API and existing forms reused. No new dependency. Atomic mock validation and Workspace isolation retained. Explicit permission-edit projection keeps PM-only Department, derives TR Departments from retained memberships and drops former permissions. Incoming main sign-in/import changes preserved.

## Findings resolved
BUG-026: popup must show assignment errors for fields not registered there.
BUG-027: remove old current Project permissions while preserving history.
BUG-028: option-level disabled must include pending state.
BUG-032: session Department/Departments follow effective roles and current membership, not raw form values.
Final independent review found no remaining critical issue. Source diff and fresh UI19/19 plus API/session74/74 support this verdict. Production enforcement remains backend scope; central session adapter requires FE Lead review as authorized.

## FE Lead changes requested — follow-up 2026-10-30
Parent reviewed the follow-up diff against AC006.4–8 and AC007.3–7. This is a self-review, not a new independent approval; FE Lead re-review remains pending.

- BUG034: retry uses the current dialog and waits for an enabled action. File-local status clients are disposed before mock fixture reset; paired failure and subsequent115/115 users pass recorded.
- BUG035: required handover options must succeed; confirmation stays disabled during pending/error/submit. Loading and retry regressions cover this. Small shared FormModal isOkDisabled extension is disclosed for FE Lead review.
- BUG036: visible MSG112/113 and aria-describedby explain disabled controls without hover. Duplicate button aria-labels removed; decorative loading icons are hidden, aria-busy retains pending semantics and request guards retain double-submit protection.
- BUG037: reported stale Cancel behavior not reproduced. UserAccountDetail key already includes all editable assignments; background-refetch regression proves current roles/Department restored without production changes.
- Successful Space transfer, MSG78-to-handover sequence, Department-only roles unchanged now explicitly tested. Error paths keep choices and allow retry.
- No new dependency, generated drift or acceptance change. Original full CI still required; BUG018/022 not claimed fixed.

## Inline comments follow-up — 2026-10-31
Independent read-only reviewer checked 6548ed0 through the current changes against AC007.3/.5/.7 and AC006.8: no actionable correctness or spec-compliance findings. Parent inspected the diff and fresh tests.

BUG039/040 restore confirmation error recovery and server-count confirmation; BUG041 validates the precise membership removal set before atomic writes; BUG042 maps nested recipient paths without hiding unregistered assignment errors. Department null normalization and isRecord reuse address the two small comments. The background-refetch remount remains intentional: latest confirmed assignments win, as already covered by BUG037 regression; PR notes disclose unsaved-form/dialog reset on changed assignments.

No acceptance, dependency, shared API or test-limit changes. Windows120/121 and isolated status13/14 leave AC006.2 unresolved locally; full original Linux CI must pass before claiming complete verification. Existing BUG018/022/025 remain open.
