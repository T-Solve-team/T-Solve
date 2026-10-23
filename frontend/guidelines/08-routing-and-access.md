# 08 · Routing & access

> **Applies to:** `src/app/router/`, `src/shared/routing/`, `src/shared/auth/`, every `features/*/routes.ts`,
> and any code that shows or hides something by role.
> **Why it matters:** each role must land on its own screen, never see another role's screens, and get a
> clear 403/404 instead of a broken page. Declaring access in one place per route keeps that consistent.

## 1. Route map (all 35 web screens)

Paths live in `shared/routing/paths.ts` (pre-filled). Always use `paths.*`, never type a path string.
Roles follow Report3's Permission Matrix; "Employee" = every signed-in user (each user has the Employee role).

| # | Screen | Path | Page | Roles |
|---|---|---|---|---|
| 1.1.1 | Login | `/login` | `auth/LoginPage` | signed out |
| 1.2.1 | My Profile | `/profile` | `my-profile/MyProfilePage` | Employee |
| 1.3.1 | User List | `/users` | `users/UserListPage` | Admin |
| 1.3.2 | User Detail | `/users/new` · `/users/:userId` | `users/UserDetailPage` | Admin |
| 1.3.3 | Hand Over Responsibilities (popup) | — | `users/HandOverResponsibilitiesModal` | Admin |
| 2.1 | Workspace Setup | `/workspace-setup` | `workspace/WorkspaceSetupPage` | Admin |
| 2.2 | Workspace Settings | `/workspace-settings` | `workspace/WorkspaceSettingsPage` | Admin |
| 2.3 | Department Detail | `/workspace-settings/departments/:departmentId` | `workspace/DepartmentDetailPage` | Admin |
| 2.4 | Delete Department (popup) | — | `workspace/DeleteDepartmentModal` | Admin |
| 3.1 | Jira Integration | `/jira-integration` | `jira-integration/JiraIntegrationPage` | Admin, Project Manager |
| 3.2 | Project Detail | `/jira-integration/projects/:projectId` | `jira-integration/ProjectDetailPage` | Project Manager |
| 5.1 | Import Tickets | `/import` (`?tab=jira\|csv\|manual`) | `ticket-import/ImportTicketsPage` | Project Manager |
| 5.2 | Import Preview | `/import/preview` | `ticket-import/ImportPreviewPage` | Project Manager |
| 5.3 | Import Ticket Preview (popup) | — | `ticket-import/ImportTicketPreviewModal` | Project Manager |
| 5.4 | Import History | `/import/history` | `ticket-import/ImportHistoryPage` | Project Manager |
| 5.5 | Ticket Detail | `/tickets/:ticketId` | `tickets/TicketDetailPage` | Ticket Resolver, Project Manager |
| 6.1 | Search | `/search` | `search/SearchPage` | Ticket Resolver, Project Manager |
| 6.2 | Ticket List | `/tickets` | `tickets/TicketListPage` | Ticket Resolver, Project Manager |
| 7.1 | Review Queue | `/review-queue` | `review/ReviewQueuePage` | Project Manager |
| 7.2 | Request Changes (popup) | — | `review/RequestChangesModal` | Project Manager |
| 7.3 | Reject Ticket (popup) | — | `review/RejectTicketModal` | Project Manager |
| 7.4 | Review History | `/review-history` | `review/ReviewHistoryPage` | Project Manager |
| 7.5 | Expired Tickets | `/expired-tickets` | `review/ExpiredTicketsPage` | Project Manager |
| 8.1 | Give Feedback (popup) | — | `feedback/GiveFeedbackModal` | Ticket Resolver, Project Manager |
| 9.1 | Knowledge Dashboard | `/dashboard` | `knowledge-dashboard/KnowledgeDashboardPage` | Project Manager, Admin |
| 9.2 | Audit Log | `/audit-log` | `audit-log/AuditLogPage` | Admin |
| 9.3 | Audit Entry Detail (popup) | — | `audit-log/AuditEntryDetailModal` | Admin |
| 10.1 | Triage History | `/triage-history` | `triage/TriageHistoryPage` | Project Manager |
| 10.2 | Triage Result Detail (popup) | — | `triage/TriageResultDetailModal` | Project Manager |
| 11.1 | Wiki Home | `/wiki` (`?space=&folder=`) | `wiki/WikiHomePage` | Employee |
| 11.2 | Wiki Document | `/wiki/documents/:documentId` | `wiki/WikiDocumentPage` | Employee |
| 11.4 | Upload Documents | `/wiki/upload` | `wiki/UploadDocumentsPage` | Knowledge Manager |
| 11.5 | Folder (popup) | — | `wiki/FolderModal` | Knowledge Manager |
| 11.7 | Space Settings | `/wiki/spaces/:spaceId/settings` | `wiki/SpaceSettingsPage` | Knowledge Manager |
| 12.1 | Knowledge Chatbot | `/chatbot` | `chatbot/KnowledgeChatbotPage` | Employee |

Plus: `/` → landing page of the signed-in user (or `/login`); 403 is shown in place by the guard;
anything else → 404 for a signed-in user (a signed-out user is sent to `/login?returnTo=…` first) ("This page does not exist." — the SRS has no MSG code for it yet; open point).
In dev builds only: `/dev/kit`.

**Landing page** (D4) — the first match of the user's roles: Admin → User List · Project Manager → Review
Queue · Knowledge Manager → Wiki Home · Ticket Resolver → Search · otherwise → Knowledge Chatbot. Defined
once in `shared/auth/landing.ts` (`landingPath(roles)`).

**Workspace Setup** (UC-10, D4): while `/me` says `workspaceReady: false`, an Admin who opens any screen
other than Workspace Setup or My Profile is sent to 2.1 Workspace Setup (MSG60). Other users are not
redirected.

## 2. How a feature declares its routes

We use React Router 8 in **data mode** (`createBrowserRouter`). Each feature exports a list of
`FeatureRoute`s; `app/router/routes.tsx` turns them into router objects inside the guarded shell.

```ts
// features/review/routes.ts
import { paths, type FeatureRoute } from '@/shared/routing';

export const reviewRoutes: FeatureRoute[] = [
  {
    path: paths.reviewQueue,
    title: 'Review Queue', // 7.1 — SRS screen name, shown in the header and the browser tab
    roles: ['PROJECT_MANAGER'],
    lazy: () => import('./pages/ReviewQueuePage'),
  },
  // … Review History (7.4), Expired Tickets (7.5)
];
```

- `title` is the SRS screen name; the AppShell header shows it ([07](07-ui-and-styling.md) U4).
- `roles` is **required** — the type does not compile without it. A user may open the screen when they
  have **any** of the listed roles. `'GUEST'` means signed-out users only (Login); `['EMPLOYEE']` is every
  signed-in user (My Profile, Wiki, Knowledge Chatbot). *Why:* forgetting a guard should be impossible, not
  just unlikely.
- `lazy` loads each page's code only when it is opened (smaller first load; NFR "page loads within 2 s").
- **No route `loader`s for data.** Data is fetched by TanStack Query inside the page ([06](06-data-layer.md)).
  *Why:* one data mechanism, with caching and polling, instead of two.

## 3. Guards

```
<GuestOnly>                   (Login) a signed-in user → their landing page
<RequireAuth>                 session pending → full-page loading spinner
                              /me failed (5xx, network) → MSG06 page with "Try again"
                              no session (401) → /login?returnTo=<current path>
  <AppShell>
    <RequireWorkspace>        an Admin before Workspace Setup → /workspace-setup (§1)
      <RequireRole roles=…>   none of the user's roles in route.roles → ForbiddenPage (MSG08), URL unchanged
        <Page />
```

- The guards read **only** `useSession()` ([06](06-data-layer.md) §7).
- `/login` when already signed in → redirect to the landing page.
- After sign-in the backend returns the user to `returnTo` if present, otherwise `/`.

## 4. Access inside a screen (beyond the role)

The role decides **which screens** you can open. Inside a screen, some actions also depend on the
**Project** or **Department** (Permission Matrix footnotes ¹–⁴). The backend always enforces this; the UI
only hides what you cannot do.

| Need | Use |
|---|---|
| "Does the user have this role?" | `const { hasRole } = useSession(); hasRole('PROJECT_MANAGER')` |
| "Does the user have any of these roles?" | `useSession().hasAnyRole(['TICKET_RESOLVER', 'PROJECT_MANAGER'])` |
| "Is the user the Project Manager of this ticket's Project?" | `useSession().isProjectManagerOf(ticket.projectId)` |
| "Is the user a member of this Project?" | `useSession().isMemberOf(projectId)` |

```tsx
// ✅ Do — a named, testable condition
const canDecide = isProjectManagerOf(ticket.projectId);
{canDecide && <TicketDecisionActions ticket={ticket} />}

// ❌ Don't — role strings scattered through JSX, missing the Project check
{session.roles.includes('PROJECT_MANAGER') && <Button>Approve</Button>}
```

Data you are not allowed to see is never sent by the backend; a 403 on a page's main query shows the
403 state with MSG08 (e.g. a ticket of another Department, GBR-02).

## 5. URLs as state

- Tabs, filters, sort, page number and search text live in **search params** (`?tab=csv&page=2`), read with
  `useSearchParams`. *Why:* reload, back button and shared links all work.
- Detail screens use **path params** (`/tickets/:ticketId`); read with `useParams` and handle a missing or
  invalid id with the 404 state.
- Navigate with `<Link to={paths.ticketDetail(id)}>` or `navigate(paths.reviewQueue)` — never string
  concatenation.

## 6. Popups have no route

Hand Over Responsibilities, Delete Department, Import Ticket Preview, Request Changes, Reject Ticket, Give
Feedback, Audit Entry Detail, Triage Result Detail and Folder are modals opened by their parent screen with local state (`const [isRejectOpen, setRejectOpen] = useState(false)`).
*Why:* the SRS defines them as popups of a screen; a URL for each adds routes nobody bookmarks.

## 7. Leaving a screen with unsaved work

- **Import Preview** (5.2): leaving before Finish/Confirm asks **MSG37**; confirming discards everything
  (an import is never saved as a draft). Use React Router's `useBlocker` with `ConfirmDialog`, plus
  `beforeunload` for reload/close. The import data lives only in that page's memory — opening
  `/import/preview` directly (no data) redirects to `/import`.
- Other forms: no leave-confirmation unless the SRS asks for one.

## 8. Sign-in and session expiry

- **Login** has one button, "Sign in with Google". It does a full-page navigation to the backend's sign-in
  URL; the backend runs OIDC and redirects back. Errors come back as a code (`/login?error=MSG09`) and are
  shown in the Login message area (MSG09, MSG10, MSG06). *(Backend contract — decision log.)*
- **Session expired** (any 401 after sign-in): the global handler shows the **MSG07** dialog, then goes to
  `/login`. Never redirect silently.
- Open point (UI decisions 04/10): whether Login also shows MSG73/MSG75 (the sign-in link from Jira). Not
  built until the FE Lead decides.

## 9. Checklist

- [ ] Route in the feature's `routes.ts` with `title`, `roles`, `paths.*` and `lazy`.
- [ ] Filters/tabs/pagination in search params; detail ids in path params with a 404 state.
- [ ] Actions hidden with named `useSession()` checks, never raw role strings in JSX.
- [ ] Popups are modals with local state.

---
*Last verified against code: 2026-10-23, KAN-63 — screens, roles, paths and MSG codes checked against the Report3 foundation (`src/test/guidelines.test.ts`).*
