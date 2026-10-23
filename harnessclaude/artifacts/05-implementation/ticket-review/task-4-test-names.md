# Normalize frontend review test names

Date: 2026-10-22. User approved normalization after syncing the new team rules from main. User opened the exact three-file quick change through sdd.ts.

## Scope

- RequestChangesModal.test.tsx: 2 test names.
- ReviewQueuePage.test.tsx: 10 test names.
- TicketDetailPage.test.tsx: 6 test names.
- Move existing AC identifiers to the beginning and existing BUG identifiers to the end. Preserve every identifier and existing coverage claim; this task does not reassess which criterion each test proves.
- A programmatic comparison against HEAD masks only the it-name strings and confirms all remaining source content is identical, including assertions and test bodies.

## Verification

- format:check, lint, typecheck and production build passed.
- generate:api passed and left no diff in src/shared/api/generated.
- Harness audit and git diff --check passed. Graphify refreshed locally; the known missing SQL extractor remains unchanged.
- First full Vitest run: 76 passed, 2 Review Queue tests failed waiting for initial ticket/error alert. Quality checks including build/generation were running concurrently. Recorded as BUG-028 in docs/bug-log.md; resource contention is only a hypothesis. Initial-load wait already uses 5000 ms. No timeout or assertion changed, and no tests skipped.
- Second full run without concurrent quality/build/generation: 77 passed, 1 failed (the initial queue load). Concurrent generation is therefore not necessary to reproduce the issue.
- Isolated ReviewQueuePage: all 10 tests passed.
- Full suite with command-line --maxWorkers=2: 12 files, 78 tests passed, no unhandled errors. No persistent worker configuration changed. This supports investigating suite concurrency/resource contention, but does not establish the precise cause or fix the default command; BUG-028 remains Open.

## Checkpoint

User approved commit and push with the default-suite timeout explicitly disclosed. Code committed as 73e1f68: test(review): normalize acceptance criterion test names (KAN-24). The quick change is closed through sdd.ts quick done. Existing feature/module status is unchanged; BUG-028 remains Open and no persistent test configuration changed.
