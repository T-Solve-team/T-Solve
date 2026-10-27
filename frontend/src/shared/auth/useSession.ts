import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { getGetMeQueryKey, getMe, isApiError, logout } from '@/shared/api';
import type { Role } from './roles';
import { toSession, type Session } from './session';

/** Query key of the session: the generated key of `GET /api/v1/me`. */
export const SESSION_QUERY_KEY = getGetMeQueryKey();

/**
 * Browser-storage keys that belong to the signed-in session only (e.g. the Knowledge Chatbot history, UC-52).
 * They are removed at logout and when the session ends (UC-02).
 */
export const SESSION_STORAGE_PREFIX = 'tsolve.session.';

const NO_ROLES: readonly Role[] = [];

/**
 * `/me` answers 401 when nobody is signed in: that is "no session", not an error. A 401 with MSG123 is not "nobody
 * is signed in": the cookie belongs to another Workspace site, and the global handler takes the MSG123 path
 * (AC-012.5), so it is thrown.
 */
async function fetchSession(signal: AbortSignal): Promise<Session | null> {
  try {
    return toSession(await getMe({ signal }));
  } catch (error) {
    if (isApiError(error) && error.status === 401 && error.code !== 'MSG123') return null;
    throw error;
  }
}

/** Answers that tell about the site or the cookie, not about a broken session service (AC-001.6). */
const NOT_A_SESSION_ERROR: readonly string[] = ['MSG119', 'MSG120', 'MSG123'];

/**
 * The only source of "who am I" (guideline 06 §7). Returns the session plus named access checks: roles
 * (GBR-28, a user can hold several) and Project-level permissions (guideline 08 §4).
 */
export function useSession() {
  const query = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: ({ signal }) => fetchSession(signal),
    staleTime: Infinity,
  });
  const session: Session | null = query.data ?? null;
  const roles = session?.roles ?? NO_ROLES;

  const hasRole = useCallback((role: Role) => roles.includes(role), [roles]);
  const hasAnyRole = useCallback(
    (wanted: readonly Role[]) => wanted.some((role) => roles.includes(role)),
    [roles],
  );
  const isProjectManagerOf = useCallback(
    (projectId: string) =>
      session?.projects.some(
        (p) => p.projectId === projectId && p.roleInProject === 'PROJECT_MANAGER',
      ) ?? false,
    [session],
  );
  const isMemberOf = useCallback(
    (projectId: string) => session?.projects.some((p) => p.projectId === projectId) ?? false,
    [session],
  );

  return {
    session,
    roles,
    hasRole,
    hasAnyRole,
    isPending: query.isPending,
    /**
     * `/me` failed for another reason than 401 (server down…): the app cannot tell who you are. An unknown site,
     * a Suspended Workspace (the site page shows) and a 401 MSG123 (Login shows) are not session errors.
     */
    isError:
      query.isError && !(isApiError(query.error) && NOT_A_SESSION_ERROR.includes(query.error.code)),
    retry: query.refetch,
    isProjectManagerOf,
    isMemberOf,
  };
}

function removeSessionOnlyData(): void {
  for (const storage of [window.sessionStorage, window.localStorage]) {
    try {
      for (let i = storage.length - 1; i >= 0; i--) {
        const key = storage.key(i);
        if (key?.startsWith(SESSION_STORAGE_PREFIX)) storage.removeItem(key);
      }
    } catch {
      // Storage blocked (private mode): nothing was stored there.
    }
  }
}

/**
 * After the signed-in user changed (logout, expired session, dev user switch): drops every other cached query
 * and mutation and the session-only browser data — they belong to the previous user. Then either reloads the
 * session in place (`signedOut: false`, the dev user switch) or marks it signed out without asking the server
 * (`signedOut: true`: logout and expiry — a refetch could sign the user back in if logout failed). Mounted
 * guards see the new session at once (e.g. nobody → redirect to Login).
 */
export async function resetSession(
  queryClient: QueryClient,
  { signedOut = false }: { signedOut?: boolean } = {},
): Promise<void> {
  const [sessionKey] = SESSION_QUERY_KEY;
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== sessionKey });
  queryClient.getMutationCache().clear();
  removeSessionOnlyData();
  if (signedOut) {
    await queryClient.cancelQueries({ queryKey: SESSION_QUERY_KEY });
    queryClient.setQueryData<Session | null>(SESSION_QUERY_KEY, null);
    return;
  }
  await queryClient.resetQueries({ queryKey: SESSION_QUERY_KEY });
}

/**
 * Logs out (UC-02): asks the server to end the session, then signs out in the app — also when that request
 * fails, so the user is never left signed in on this screen. Never throws.
 */
export function useSignOut() {
  const queryClient = useQueryClient();
  return useCallback(async () => {
    try {
      await logout();
    } catch {
      // The server session may still be valid; it ends with the cookie's lifetime (AC-008.3).
    }
    await resetSession(queryClient, { signedOut: true });
  }, [queryClient]);
}
