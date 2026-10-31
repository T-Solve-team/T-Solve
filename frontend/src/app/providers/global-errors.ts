import type { QueryClient } from '@tanstack/react-query';
import { createQueryClient, getSiteStatus, type ApiError, type SiteStatus } from '@/shared/api';
import { resetSession, SESSION_QUERY_KEY } from '@/shared/auth';
import { dismissAcknowledgement, showAcknowledgement, showMessage } from '@/shared/messages';

/**
 * What the app does when a request fails and the screen does not handle it (guideline 06 §6):
 * - 401 with MSG123 (the cookie belongs to another Workspace site) → the session ends as at logout, no dialog;
 *   the route guard sends the user to `/login?error=MSG123`. It wins over any other 401, also over an open MSG07
 *   dialog, which is closed.
 * - any other 401 while signed in → MSG07 dialog, then the cache is dropped and the route guard sends the user to
 *   Login (with returnTo). Shown once even when several requests fail together.
 * - MSG119 / MSG120 (unknown site, Suspended Workspace) → no toast and no dialog: `http()` already switched the
 *   app to the site page; here the cache is dropped, once however many requests answer it. Also when the request
 *   is a mutation with `handlesOwnErrors`: `createQueryClient` sends these two codes to `onSiteUnavailable` before
 *   it looks at `handlesOwnErrors`, and `showMessage` never shows them.
 * - a mutation fails → toast with the error's MSG code (MSG08 for 403, MSG06 for 5xx / network),
 *   unless the mutation set `meta: { handlesOwnErrors: true }`.
 * - a query fails → nothing here: the screen shows ErrorState in place (never two messages for one error).
 */
let sessionExpiredDialogOpen = false;
/** Set when MSG123 closed the MSG07 dialog, so the expired-session path does not reset a second time. */
let sessionExpiredDialogDismissed = false;

/** The site status each client was last reset for: a status object changes only when the status does. */
const resetFor = new WeakMap<QueryClient, SiteStatus>();

/** Ends the session once per site status, whichever request noticed it first. */
async function endSessionOnce(client: QueryClient): Promise<void> {
  const status = getSiteStatus();
  if (resetFor.get(client) === status) return;
  resetFor.set(client, status);
  await resetSession(client, { signedOut: true });
}

async function handleUnauthorized(client: QueryClient): Promise<void> {
  if (getSiteStatus().foreignSession) {
    // `http()` saw the MSG123. An open MSG07 dialog closes; the MSG123 path takes over.
    if (sessionExpiredDialogOpen) {
      sessionExpiredDialogDismissed = true;
      dismissAcknowledgement('MSG07');
    }
    await endSessionOnce(client);
    return;
  }
  if (sessionExpiredDialogOpen) return;
  // Not signed in at all: the route guards already send the user to Login; nothing has "expired".
  if (!client.getQueryData(SESSION_QUERY_KEY)) return;

  sessionExpiredDialogOpen = true;
  sessionExpiredDialogDismissed = false;
  try {
    await showAcknowledgement('MSG07');
  } finally {
    sessionExpiredDialogOpen = false;
  }
  if (!sessionExpiredDialogDismissed) await resetSession(client, { signedOut: true });
}

function handleMutationError(error: ApiError): void {
  showMessage(error.code, error.params);
}

/** The app's QueryClient: shared/api defaults plus the global error behaviour above. */
export function createAppQueryClient(): QueryClient {
  return createQueryClient({
    onUnauthorized: (client) => void handleUnauthorized(client),
    onSiteUnavailable: (client) => void endSessionOnce(client),
    onMutationError: handleMutationError,
  });
}
