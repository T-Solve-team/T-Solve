# 04 · TypeScript

> **Applies to:** every `.ts` / `.tsx` file in `frontend/`.
> **Why it matters:** the compiler is the cheapest reviewer we have. Strict types catch wrong API usage,
> missing `null` checks and typos before a teammate or the Tester ever sees them.

## 1. Compiler settings (`tsconfig.app.json`)

We use **TypeScript 6.0** (not 7 — see [13](13-decision-log.md)). TS 6 already turns `strict` on by
default; we state it explicitly anyway so nobody wonders. The settings that matter:

| Option | Value | Why |
|---|---|---|
| `strict` | `true` | Enables `strictNullChecks`, `noImplicitAny` and the rest of the strict family. |
| `noUncheckedIndexedAccess` | `true` | `items[0]` is `Item \| undefined`; forces you to handle "not found". |
| `exactOptionalPropertyTypes` | not set (default `false`) | Too noisy with antd/orval types for a junior team; revisit later. |
| `noImplicitOverride`, `noFallthroughCasesInSwitch` | `true` | Small, cheap safety nets. |
| `noUnusedLocals`, `noUnusedParameters` | `true` | Dead code is a review cost. (Prefix intentionally unused params with `_`.) |
| `erasableSyntaxOnly` | `true` | Bans `enum`, `namespace` and parameter properties — syntax that is not plain JS once types are removed. |
| `verbatimModuleSyntax` | `true` | Type-only imports must say `import type`; the bundler can drop them safely. |
| `module` / `moduleResolution` | `preserve` / `bundler` | What TS 6 recommends for apps built by a bundler (Vite). |
| `jsx` | `react-jsx` | React 19 automatic runtime. |
| `paths` | `{ "@/*": ["./src/*"] }` | The one path alias. (No `baseUrl` — deprecated in TS 6.) |
| `types` | `["vite/client", "vitest/globals"]` (one config for app and tests; `tsconfig.node.json`: `["node"]`) | TS 6 defaults `types` to `[]`; list what we use. |

Do not change these in a feature PR. `npm run typecheck` runs `tsc -b --noEmit` over the app, the
tests and the config files.

## 2. Rules

### R1 — No `any`. Use `unknown` and narrow it. 🔒 `@typescript-eslint/no-explicit-any`

```ts
// ✅ Do
function getMessageCode(error: unknown): MsgCode {
  return isApiError(error) ? error.code : 'MSG06';
}

// ❌ Don't
function getMessageCode(error: any) { return error.code; }
```

*Why:* `any` switches the compiler off for everything it touches, and it spreads.

### R2 — No `@ts-ignore`, no `@ts-nocheck`. 🔒 `@typescript-eslint/ban-ts-comment`

If a library type is genuinely wrong, use `// @ts-expect-error <reason>` on one line — it fails as soon as
the library is fixed, so the hack cannot outlive its reason.

### R3 — No non-null assertion `!`. Handle the missing case. 🔒 `@typescript-eslint/no-non-null-assertion`

```ts
// ✅ Do
const { ticketId } = useParams();
if (!ticketId) return <EmptyState />;

// ❌ Don't
const ticketId = useParams().ticketId!;
```

### R4 — `as` only for narrowing you have *proved*. Never `as unknown as X`.

```ts
// ✅ Do — narrowing with a check
if (status === 'PENDING_REVIEW' || status === 'CHANGES_REQUESTED') { … }

// ✅ OK — literal types
const SOURCES = ['SOLUTION_FORM', 'JIRA_IMPORT', 'CSV', 'MANUAL'] as const;

// ❌ Don't — lying to the compiler
const ticket = data as Ticket;
const ids = selected as unknown as number[];
```

### R5 — Prefer `type` over `interface`; no `I` prefix.

One way of doing things is easier to teach. Use `interface` only when you must extend a library
interface (rare).

### R6 — Let inference work; annotate boundaries.

Annotate **exported** function parameters and return types of shared helpers, and component props.
Do not annotate local variables the compiler already knows.

```ts
// ✅ Do
export function formatDateTime(value: string | null | undefined): string { … }
const count = selectedIds.length;

// ❌ Don't
const count: number = selectedIds.length;
```

### R7 — Model "one of several states" with a discriminated union, not optional flags.

```ts
// ✅ Do
type ImportRow =
  | { result: 'VALID'; solution: string }
  | { result: 'ERROR'; errorDetails: string };

// ❌ Don't
type ImportRow = { isValid?: boolean; solution?: string; errorDetails?: string };
```

*Why:* the compiler then forces every `switch` to handle each case.

## 3. How types flow from the API

```
backend (springdoc) ─► openapi/tsolve-api.yaml ─► npm run generate:api ─► src/shared/api/generated/
                                                                             ├── model/   (types)
                                                                             └── <tag>/   (hooks)
features/*/hooks ─► import types and hooks from '@/shared/api'  ─► pages/components
```

- **API shapes come only from `@/shared/api`** (which re-exports the generated code). Never declare a
  type that mirrors a request or response by hand. 🔒 (`no-restricted-imports` blocks deep imports into
  `generated/`, and code review blocks hand-written copies.)
- Need a *view* of an API type? Derive it, don't redeclare it:

```ts
import type { ReviewQueueItem } from '@/shared/api';

// ✅ Do — derived; stays correct when the API changes
type QueueRow = Pick<ReviewQueueItem, 'ticketId' | 'sourceTicketId' | 'title' | 'reviewDueDate'>;

// ❌ Don't — a copy that silently drifts
type QueueRow = { ticketId: string; sourceTicketId: string; title: string; reviewDueDate: string };
```

- Value lists in the spec (today `Role`, `ProjectRole`, `TicketSource`; statuses once the backend adds them)
  are generated as string-literal unions with a matching `const` object. Use them instead of typing the
  strings again.
- Dates arrive as ISO strings. Keep them as `string` in data; convert only when displaying, with
  `formatDate` / `formatDateTime` from `@/shared/lib`.

## 4. When you *do* write your own types

| Situation | Where |
|---|---|
| Component props | `type XxxProps` in the component file, right above the component |
| UI-only state (selected rows, open modal, form values before submit) | in the file that owns the state |
| URL search params of a page | in the page file (`type TicketListFilters`) |
| A shared UI contract (e.g. `StatusTag`'s `status` prop) | in that `shared/ui` component |

Export a type only when another file actually imports it.

## 5. Checklist

- [ ] `npm run typecheck` passes with zero errors.
- [ ] No `any`, `@ts-ignore`, `!`, `as unknown as`.
- [ ] No hand-written API types; derived types use `Pick` / `Omit` / indexed access.
- [ ] `import type` for type-only imports.

---
*Last verified against code: 2026-10-10, step 5.5 — every path, name, rule and ✅ example checked against the scaffold.*
