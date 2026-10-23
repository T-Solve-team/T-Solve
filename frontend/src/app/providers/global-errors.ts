import type { QueryClient } from '@tanstack/react-query';
import { createQueryClient, type ApiError } from '@/shared/api';
import { resetSession, SESSION_QUERY_KEY } from '@/shared/auth';
import { showAcknowledgement, showMessage } from '@/shared/messages';

/**
 * What the app does when a request fails and the screen does not handle it (guideline 06 §6):
 * - 401 while signed in → MSG07 dialog, then the cache is dropped and the route guard sends the user to
 *   Login (with returnTo). Shown once even when several requests fail together.
 * - a mutation fails → toast with the error's MSG code (MSG08 for 403, MSG06 for 5xx / network),
 *   unless the mutation set `meta: { handlesOwnErrors: true }`.
 * - a query fails → nothing here: the screen shows ErrorState in place (never two messages for one error).
 */
let sessionExpiredDialogOpen = false;

async function handleUnauthorized(client: QueryClient): Promise<void> {
  if (sessionExpiredDialogOpen) return;
  // Not signed in at all: the route guards already send the user to Login; nothing has "expired".
  if (!client.getQueryData(SESSION_QUERY_KEY)) return;
  sessionExpiredDialogOpen = true;
  try {
    await showAcknowledgement('MSG07');
  } finally {
    sessionExpiredDialogOpen = false;
  }
  await resetSession(client, { signedOut: true });
}

function handleMutationError(error: ApiError): void {
  showMessage(error.code, error.params);
}

/** The app's QueryClient: shared/api defaults plus the global error behaviour above. */
export function createAppQueryClient(): QueryClient {
  return createQueryClient({
    onUnauthorized: (client) => void handleUnauthorized(client),
    onMutationError: handleMutationError,
  });
}
