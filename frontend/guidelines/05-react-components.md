# 05 · React components

> **Applies to:** every `.tsx` file in `src/features/`, `src/shared/ui/` and `src/app/`.
> **Why it matters:** small, predictable components are easy to review, test and hand to an AI agent.
> Most bugs in React apps come from effects, duplicated state and components that do too much.

## 1. Three kinds of component

| Kind | Lives in | Knows about | Example |
|---|---|---|---|
| **Page** | `features/*/pages/` | URL params, feature hooks, layout of one screen | `ReviewQueuePage` |
| **Feature component** | `features/*/components/` | its props and (if needed) one feature hook | `ReviewQueueTable`, `RejectTicketModal` |
| **Kit component** | `shared/ui/` | only its props — no API, no routing, no business rules | `StatusTag`, `DataTable` |

We do **not** use a strict "container vs presentational" split in every file — it doubles the file count
for little gain on a team of five. The rule that remains: **data fetching happens in pages or in a
feature hook called by a page; kit components never fetch.** A feature component may call a hook when
passing data down would mean threading props through three levels.

## 2. Rules

### C1 — One component per file; the file is named after it. (See [03](03-naming.md).)
Small private sub-components used only in that file are fine below the main export.

### C2 — Size limits: a component ≤ ~150 lines, a page ≤ ~200 lines.
When you pass that, extract a feature component or a hook. *Why:* past that size a reviewer can no
longer hold the whole component in their head. (Industry guidance varies; these numbers are ours.)

### C3 — Function components with named exports; pages export `default` too (for lazy routes).

```tsx
/** 7.1 Review Queue — SRS III.7.1 */
export function ReviewQueuePage() { … }
export default ReviewQueuePage; // used by the lazy import in routes.ts
```

### C4 — Props: few, typed, specific.

- Type props with `type XxxProps`. Destructure in the signature.
- Pass **what the component needs**, not whole API objects it only reads one field of.
- Booleans are `isX`/`hasX`; callbacks are `onX` (`onConfirm`, `onCancel`).
- More than ~6 props usually means the component does two jobs — split it.
- No prop drilling past two levels; lift the data hook down to where it is used instead.
- React 19: `ref` is a normal prop — no `forwardRef` needed.

```tsx
// ✅ Do
type RejectTicketModalProps = {
  ticketIds: number[];
  isOpen: boolean;
  onClose: () => void;
};

// ❌ Don't
type Props = { data: any; config: object; flag1?: boolean; flag2?: boolean; cb: Function };
```

### C5 — Compose with `children` instead of adding configuration props.

```tsx
// ✅ Do
<SectionCard title="Review workload">
  <ReviewWorkloadChart data={data} />
</SectionCard>

// ❌ Don't
<SectionCard title="Review workload" showChart chartType="bar" chartData={data} />
```

### C6 — Do not fetch in `useEffect`. Use TanStack Query hooks. (See [06](06-data-layer.md).)

```tsx
// ✅ Do
const { data, isPending, isError } = useReviewQueue(filters);

// ❌ Don't
const [tickets, setTickets] = useState([]);
useEffect(() => { fetch('/api/v1/review-queue').then(r => r.json()).then(setTickets); }, []);
```

*Why:* hand-written fetching forgets loading, errors, cancellation, caching and refetching — the query
library already does all five.

### C7 — `useEffect` only to sync with something outside React.

Good reasons: subscribing to `window` events, focusing an element, a third-party widget. Bad reasons:
copying props into state, computing values from other state, reacting to a button click.

```tsx
// ✅ Do — derive during render
const selectedCount = selectedIds.length;
const canApprove = selectedCount > 0 && !approve.isPending;

// ❌ Don't — duplicated state kept in sync by an effect
const [canApprove, setCanApprove] = useState(false);
useEffect(() => setCanApprove(selectedIds.length > 0), [selectedIds]);
```

Handle user actions in event handlers, not in effects. (React docs: "You Might Not Need an Effect".)

### C8 — Keep state minimal and as low as possible.

- State that lives in the URL (filters, tab, page number) is read from `useSearchParams`, not copied
  into `useState`. List screens use `useListSearchParams(defaultSort)` from `@/shared/routing`: page
  (from 1), sort and filters in the URL, plus `apiPaging` ready for the generated hook. *Why:* the back button and shared links work, and there is one source of truth.
- Server data stays in the query cache. Never `setState(data)`.
- Modal open/close and selections are local `useState` in the page or feature component that owns them.

### C9 — Every data view has four states: loading, error, empty, data.

```tsx
if (query.isPending) return <LoadingState />;
if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
if (query.data.content.length === 0) return <EmptyState code="MSG13" />; // Review Queue's empty text
return <TicketCards tickets={query.data.content} />;
```

Use the kit's `LoadingState`, `ErrorState` and `EmptyState` (Figma "Web/Empty & Loading"). The default
empty message is MSG04; use the screen-specific code where the SRS names one (MSG13 for Review Queue).
`DataTable` handles these states for you when you pass `loading`, `error`, `onRetry` and `rows`
(see `features/review/components/ReviewQueueTable.tsx`).

### C10 — Lists need stable keys from the data.

`key={ticket.id}` — never the array index for rows that can be re-ordered, filtered or selected.

### C11 — No business rules hidden in JSX.

Pull non-trivial conditions into a named variable or a small function so they can be read and tested.

```tsx
// ✅ Do
const canRequestChanges = ticket.source === 'SOLUTION_FORM'; // MSG18 otherwise
```

## 3. Semantic HTML basics

Not an NFR (the FE Lead removed the accessibility NFR on 2026-10-09), but cheap and it keeps tests simple —
tests find elements by role and label ([10](10-testing.md) T1):

- **Clickable things are `<Button>` or `<a>`/`<Link>`** — never a `<div onClick>`.
- **Every form field has a visible label** via `FormField`/`Form.Item label`. Placeholders are not labels.
- **Icon-only buttons have `aria-label`** (`<Button icon={<DeleteOutlined />} aria-label="Remove" />`).
- **Status is text, not only colour** — `StatusTag` shows the status name.
- Don't disable antd's built-in keyboard behaviour (Esc closes modals, Enter submits forms).

## 4. Checklist

- [ ] Page has the SRS screen comment and default export.
- [ ] No component over the size limits; no `useEffect` for data or derived state.
- [ ] Loading, error and empty states handled.
- [ ] Buttons/links are real buttons/links; fields have labels; icon buttons have `aria-label`.
- [ ] No business condition buried inline in JSX.

---
*Last verified against code: 2026-10-23, KAN-63 — screens, roles, paths and MSG codes checked against the Report3 foundation (`src/test/guidelines.test.ts`).*
