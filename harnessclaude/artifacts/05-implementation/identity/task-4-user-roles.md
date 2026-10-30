# Task 4: User roles and assignments — KAN-110

**Module:** identity
**Assignee:** Phạm Tùng Dương
**Status:** passing
**Iteration:** 1

## Session 2026-10-29

### Working on: user-roles
- Implements STORY-007, AC-007.1–7 in existing User Detail editing mode. Employee retained, own Admin protected, PM Department and KM Spaces required.
- Reuses the handover popup for removed Project/Space duties and MSG78 for actual TR memberships. All validation precedes atomic mock state changes; history survives role removal.
- OpenAPI PUT /users/{userId}, generated update hook, feature-local mock handlers and session projection. Shared session adapter was explicitly authorized for KAN-109/110 and remains small for FE Lead review.
- Keeps edits on error/cancel, disables controls while saving, refreshes users/session after success. No new dependency, production backend or shared UI change.
- BUG-026 error popup, BUG-027 former permissions, BUG-028 pending checkboxes and BUG-032 role-specific session Departments reproduced with named regressions and fixed.
- User-authorized issue branch feat/KAN-110-identity-user-roles includes main7296269 and repaired PR78 integration5895f6e. KAN108 PR77 is already merged.
- API/session focused74/74, independent UIroles/status19/19; all7 criteria traced. Format/lint/typecheck/build and generated-client hash check pass. Full verification result follows.
- Independent final review: PASS, no remaining blocker. Browser: successful Department handover, retained Project history, direct role save, no error logs. Screenshots registered.

### Commits
default@f1a5b412d5df51d504834cd8e007c53e0e0bf416 pushed on feat/KAN-110-identity-user-roles. Feature remains evaluating until original Linux CI passes.

### Next up
Full verification, source commit/push and original Linux CI; no merge into main.

### PR creation limitation
User explicitly requested PRs to main. KAN109 already has PR78 (mergeable, all checks passed on5895f6e). GitHub connector rejected PR creation/update with403. Browser fallback successfully updated PR78 metadata, but stopped during KAN110 creation because the Windows browser URL could not be verified. No KAN110 PR creation was confirmed. Prepared body: local graphify-out/verification/kan110-pr-body.md. Resume creation and full CI; do not mark passing before verification.

## Final PR verification — 2026-10-29
PR86 targets main. Original CI40870288080 on ec472d7:44/44 files,851/851 tests,128.01s, all frontend gates passed (format/lint/types/tests/generated-client drift/build). Evidence: https://github.com/T-Solve-team/T-Solve/actions/runs/40870288080/job/123435358184 . PR-title and gitleaks checks40870639932 also passed after the title trailing whitespace was removed. Earlier PR creation limitation is resolved by existing PR86. Feature may pass; module remains implementing for unrelated identity sign-in/backend work. BUG-025 stays open as a workstation load issue.

### Debug: PR86 BUG026 CI failure — 2026-10-29
- Root cause: AC007.6 ended with2 assignment-option queries still fetching after saved roles remounted the form. Immediate isFetching===0 failed; next BUG026 detail request rejected status0 and showed MSG06 before Save appeared. BUG026 alone passed; the pair reproduced reliably. This corrects the earlier load-only hypothesis.
- Fix: waitFor queryClient.isFetching()===0 at the end of the session-refresh test. Existing assertions/timeouts unchanged; no application or shared code/dependency change.
- Guard: named BUG033 test, bug-log row#86; red pair1/2, direct unsettled-query assertion red(expected2to0), green pair2/2, fresh entirefile9/9. Independent review PASS. Format/lint/types/build and clean API regeneration pass.
- Source default@2e6b48307c825496d0ecbd56ce796497e846be59. Full unchanged CI follows on PR86; passing source tests are not a claim of full CI until complete.

### FE Lead review follow-up — 2026-10-30
- User authorized fixing review and pushing PR86; two human-approved quick changes cover exact files. No spec/approval changes.
- default@c46c690: current-dialog retry; handover confirmation disabled until options succeed and while submitting; loading/error/retry tests. Small shared FormModal prop requires FE Lead review.
- default@67af2b4: visible accessible self-account explanations, duplicate labels removed with decorative pending spinner hidden, Space and combined MSG78 flow tests, current Cancel regression and status-test client cleanup.
- Final users115/115; format/lint/types/build and clean API regeneration pass. No test/settings weakened. Browser screenshot and self-review evidence recorded; full original PR CI follows push.
- BUG037 Cancel is not reproduced on existing keyed form; no production change. BUG018/022 remain Open/flaky. KAN111 WIP retained in stash and restored after verification.

### PR86 inline comments — 2026-10-31
- Three human-approved quick changes; source default@f306bf5 (MSG78 recovery), default@18ceab8 (combined PM/TR membership removal), default@cbae7f5 (nested handover field errors and shared guard reuse).
- BUG039/040: form errors become visible/editable after confirmation; stale membership detail now accepts server MSG78 parameters and confirmed retry. Red tests fail with the fix removed; roles page14/14 passes.
- BUG041: count the same handed-over memberships that will be removed before committing any change. Both mock and UI tests reproduce skipped confirmation; roles mock/page45/45, final simplified mock30/30 pass. Retained management stays excluded.
- BUG042: nested Project/Space recipient paths map to actual registered fields, including bracket/dot notation. Both regressions fail before the fix and pass in users runs. Unmapped role-assignment errors remain visible (BUG026).
- Normalize null Department comparison and reuse isRecord in account creation. Refetch remount note reviewed: latest confirmed assignments still win; document this existing behavior instead of changing refresh policy.
- Independent read-only review found no actionable correctness/spec issue; parent verified diff and checks. Format/lint/types/build/generation pass; clean generated diff. Initial type/build invocation overlapped generator cleanup and was rerun successfully in order.
- Windows users verification118/121 under concurrent load, then120/121; isolated status13/14. Remaining AC006.2 fails awaiting Reactivate; no timeout/assertion weakened and no full-pass claim. Original Linux CI follows push. BUG018/022/025 remain open as previously documented.
- Browser: real MSW occupied-Space error after MSG78 closes confirmation, keeps editable unsaved choices, then correcting Space and confirming again saves successfully; no error logs. Screenshot registered. Preserve and restore the separate KAN111 diagrams WIP.
