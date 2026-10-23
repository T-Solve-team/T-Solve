# 07 · UI & styling (Figma kit v4)

> **Applies to:** every screen and every file in `src/shared/ui/`, `src/app/layout/`, `src/app/theme/`
> and any `*.module.css`.
> **Why it matters:** the Figma file is the design system. If code mirrors it one-to-one, a teammate (or an
> AI agent) looking at a Figma frame knows exactly which components to use, and every screen looks the same.

## 1. Sources of truth

- **Figma file:** "T-Solve UI Design" — page `00 Cover · Design System`:
  - section **STYLES · Colour, text, effect** → theme tokens (§2)
  - section **WEB COMPONENTS · Web/… (Ant Design-aligned)** → kit components (§3) — *the only source of truth*
  - section **CANDIDATES** → ignored until the FE Lead promotes a component into the kit
  - section **JIRA APP COMPONENTS** → not used here (Jira App is a separate app)
- **Screens:** page `01 Web App`, frames named `<number> <screen name> - Hiếu` (e.g. `7.1 Review Queue - Hiếu`).
  The number matches the SRS III section and the page file's header comment.
- **Labels and texts:** the SRS field tables. Button labels, column names and screen titles are copied
  exactly ("Send back to queue", not "Send Back" or "Requeue").

## 2. Theme tokens (`src/app/theme/theme.ts`)

All colours, fonts and shadows are set **once** as antd tokens. antd 6 exposes every token as a CSS
variable (`--ant-color-primary`, `--ant-color-border`, …), so CSS Modules use the same values.

| Figma style | antd token | Value |
|---|---|---|
| brand/primary · brand/primary-hover · brand/primary-bg | `colorPrimary` · `colorPrimaryHover` · `colorPrimaryBg` | #5577FF · #7792FF · #EEF1FF |
| info · info-bg · info-border | `colorInfo` · `colorInfoBg` · `colorInfoBorder` | #5577FF · #EEF1FF · #BBC9FF |
| success · success-bg · success-border | `colorSuccess` · `colorSuccessBg` · `colorSuccessBorder` | #52C41A · #F6FFED · #B7EB8F |
| warning · warning-bg · warning-border | `colorWarning` · `colorWarningBg` · `colorWarningBorder` | #FAAD14 · #FFFBE6 · #FFE58F |
| error · error-hover · error-bg · error-border | `colorError` · `colorErrorHover` · `colorErrorBg` · `colorErrorBorder` | #FF4D4F · #FF7875 · #FFF2F0 · #FFCCC7 |
| text/primary · text/secondary · text/inverse | `colorText` · `colorTextSecondary` · `colorTextLightSolid` | #060606 · #8E92BC · #FFFFFF |
| border · border/secondary | `colorBorder` · `colorBorderSecondary` | #D0D5DD · #F0F0F0 |
| bg/container · bg/subtle | `colorBgContainer` · `colorFillAlter` | #FFFFFF · #FAFAFA |
| Body (Inter 14/22) · Small (12/20) | `fontFamily`, `fontSize` · `fontSizeSM` | Inter 14 · 12 |
| H1 24/32 · H2 20/28 · H3 16/24 (Semi Bold) | `fontSizeHeading1` · `fontSizeHeading2` · `fontSizeHeading3` | 24 · 20 · 16 |
| Number (30/38) | `StatCard` value size | 30 |
| shadow/card · shadow/popup | `boxShadowTertiary` · `boxShadowSecondary` | per Figma |
| focus-ring (#5577FF26, spread 2) | `controlOutline` · `controlOutlineWidth` | primary at 15 % · 2 |

Not taken from Figma (placeholder values there): `text/disabled`, `bg/disabled`, `bg/hover`, `bg/layout`,
`bg/tooltip` → **antd defaults** (decision log). Font: Inter, self-hosted with `@fontsource/inter`.

Heading tokens are mapped so that `<Typography.Title level={1}>` **is** Figma H1 — one heading scale, no
custom font sizes.

## 3. Kit components (`src/shared/ui/`)

Each "Web/…" component maps to exactly one thing in code. Props mirror the Figma variant properties
(`Status=Published` → `status="PUBLISHED"`, `Kind=Stat` → `<StatCard>`).

| Figma component | Code | Notes |
|---|---|---|
| Web/Button (Primary/Default/Text/Link/Danger × states) | antd `Button` (`type`, `danger`, `loading`, `disabled`) | no wrapper; states come from antd |
| Web/Checkbox · Web/Tooltip · Web/Tabs · Web/Pagination · Web/Alert · Web/Descriptions | antd `Checkbox` · `Tooltip` · `Tabs` (`items`) · (inside `DataTable`) · `Alert` · `Descriptions` | tokens only |
| Web/Toast (Success/Error/Info) | `showMessage(code)` from `@/shared/messages` | never antd `message` directly |
| Web/Form Field (Input/TextArea/Select/Date range/Search × states) | `FormField` + antd inputs | see [09](09-forms-and-validation.md) |
| Web/Status Tag | `StatusTag` (`status`: API value, e.g. `"PENDING_REVIEW"`) | colours and labels defined only in `STATUS_TAGS` |
| Web/Table (Header Cell, Cell Kind=Text/Link/Tag/Checkbox) + Web/Pagination | `DataTable` (`label`, `columns` with `key` + `sortable`, `rows`, `rowKey`, `loading`, `error`/`onRetry`, `emptyCode`, `pagination`, `sort`/`onSortChange`, `rowSelection`, `scrollX`) + `cells.text/link/tag/dateTime` | 20 rows/page, "Total N items", server sort, one-line cells with "…" |
| Web/Empty & Loading | `EmptyState` (`code`, default MSG04) · `LoadingState` · `ErrorState` (`error`, `onRetry`) | |
| Web/Card (Kind=Section / Stat) | `SectionCard` (`title`, `children`) · `StatCard` (`label`, `value`) | |
| Web/Modal Kind=Confirm / Danger confirm | `ConfirmDialog` (`isOpen`, `code`, `params`, `isDanger`, `okText`, `isConfirming`, `onConfirm`, `onCancel`) | centred, 520 wide |
| Web/Modal Header (Form / Danger) + content + Web/Modal Footer | `FormModal` (`isOpen`, `title`, `kind`, `width`, `form`, `okText`, `isOkDanger`, `isSubmitting`, `onSubmit`, `onCancel`) | centred; form cleared when closed |
| Web/Upload (Idle/Dragging/File listed/File error) | `EvidenceUpload` · `CsvUpload` — **not built yet** (FE Lead, before the first screen that needs them) | limits and MSG34/MSG72/MSG27/MSG28 built in |
| Web/App Shell (Role=4) + Web/Menu Item | `app/layout/AppShell` + `SideMenu` | §5 |
| Icons (Ant Design Icons) | `@ant-design/icons`, same names (`Icon/Inbox` → `InboxOutlined`) | |
| *(part of every form, no own component)* | `AppForm` | antd `Form` with our defaults ([09](09-forms-and-validation.md)) |

**StatusTag values** (exactly these 15 — the Figma variants Deferred, Running and Succeeded are retired):
Pending review · Changes requested · Published · Rejected · Unpublished · Expired · Active · Deactivated ·
Removed · Connected · Not connected · Completed · Failed · Valid · Error.

Table look (Figma "Web/Table"): cell padding 12 × 8, header on bg/subtle with Body Strong (Inter Medium)
titles, no tint on the sorted column — set once as `Table` component tokens in `theme.ts`.

**`/dev/kit` page** (dev builds only, any signed-in role): every kit component in every variant on one page. Use it to compare
with the Figma section side by side and to find what already exists.

## 4. Rules

### U1 — Build screens from kit components and antd primitives only.
Banned outside `shared/ui/` 🔒 (`no-restricted-imports`): antd `Tag`, `Table`, `Modal` (incl.
`Modal.confirm`), `Upload`, `message`, `notification`. The lint message names the kit replacement.
*Why:* those are exactly the components whose T-Solve version adds rules (status colours, 20-row
pagination, MSG texts, upload limits). Using the raw one silently skips the rules.

### U2 — Lay out with antd first (`Flex`, `Space`, `Row`/`Col`, `Card`), CSS second.

```tsx
// ✅ Do
<Flex justify="space-between" align="center" gap="middle">…</Flex>

// ❌ Don't
<div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>…</div>
```

### U3 — Custom CSS only in a CSS Module next to its component, using token variables.

```css
/* features/review/pages/ReviewQueuePage.module.css */
.card {
  padding: var(--ant-padding-lg);
  border-radius: var(--ant-border-radius-lg);
  background: var(--ant-color-bg-container);
  box-shadow: var(--ant-box-shadow-tertiary);
}
```

```tsx
import styles from './ReviewQueuePage.module.css';
<div className={styles.card}>…</div>
```

- ❌ No hex/rgb colours, no pixel font sizes — use `var(--ant-…)`. (One exception in the kit: the Figma
  text style "Number" 30/38 in `StatCard`, which has no antd token.)
- ❌ No `style={{…}}` except for values computed at runtime (e.g. a chart width).
- ❌ No global CSS (the exceptions: antd's `reset.css` and the `@fontsource/inter` font files, imported once
  in `main.tsx`), no
  `:global(.ant-…)` overrides, no `!important`. antd 6 changed many internal DOM
  structures; selectors on `.ant-*` internals break on upgrades. If a kit component needs a tweak, the
  FE Lead adds it in `shared/ui` (antd's `classNames`/`styles` props).
- *Why:* CSS Modules scope class names automatically, so two features can both write `.toolbar` without
  clashing — no naming scheme to remember, no cross-feature conflicts.

### U4 — The title is in the AppShell header; a page starts with its content.
The header (Figma "Web/App Shell": *Page title*, H1) shows the screen name from the route's `title`
([08](08-routing-and-access.md) §2) and sets the browser tab to "<title> · T-Solve". Pages don't render their own H1.
A page starts with a toolbar row (filters on the left, the primary action on the right — use `Flex`),
then the table or cards.

### U5 — Text and formatting.
UI text is English, copied from the SRS. Dates use `formatDate` / `formatDateTime` (dd/MM/yyyy HH:mm,
UTC+7). Required fields show `*` (antd does this via `required` rules). Status text only via `StatusTag`.

### U6 — Desktop only, minimum 1366 × 768.
No mobile or tablet layouts in the MVP (NFR). Test every screen at 1366 × 768 — tables may scroll
horizontally, the menu may not collapse below that.

## 5. App shell and menus

`AppShell` (Figma "Web/App Shell") = left side menu (252 px: logo + product name, then 220 px
"Web/Menu Item" entries) + top bar (page title as H1, user menu with **My Profile** and **Log out**) +
content area. One shell for all roles; the menu items come
from `app/layout/menu-config.ts`, pre-filled with the menu of each role (Report3 v3 design, D4):

| Role | Menu |
|---|---|
| Employee | Knowledge Chatbot · Wiki |
| Ticket Resolver | Search · Ticket List |
| Project Manager | Review Queue · Expired Tickets · Review History · Search · Ticket List · Import Tickets · Import History · Triage History · Knowledge Dashboard · Jira Integration |
| Knowledge Manager | Wiki · Knowledge Chatbot |
| Admin | User List · Workspace Settings · Jira Integration · Knowledge Dashboard · Audit Log |

A user with several roles gets one menu, `menuFor(roles)`: the menus in the order Admin, Project Manager,
Knowledge Manager, Ticket Resolver, Employee, each item only once (its first place).

The selected item follows the current route: the longest menu path the URL starts with, so detail screens
highlight their list (Ticket Detail → Ticket List); My Profile highlights nothing. In dev builds running on
mocks (`VITE_API_MOCKING=true`) a small **user switcher** (bottom right) signs you in as any mock user — one
role or several — or signs you out; it is not in production builds. Menu changes need a UI-decision change first — they are not a feature's choice.

## 6. Checklist

- [ ] Every element on the Figma frame maps to a kit component or antd primitive.
- [ ] No banned antd imports, no hex colours, no inline layout styles, no `.ant-*` overrides.
- [ ] Title, labels and buttons match the SRS text exactly.
- [ ] Looks right at 1366 × 768.

---
*Last verified against code: 2026-10-23, KAN-63 — screens, roles, paths and MSG codes checked against the Report3 foundation (`src/test/guidelines.test.ts`).*
