import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { ApiError, isApiError } from './errors';

// Types for the whole app: every query/mutation error is an ApiError, and a mutation can say it shows
// its own error message (guideline 06 §6).
declare module '@tanstack/react-query' {
  interface Register {
    defaultError: ApiError;
    mutationMeta: { handlesOwnErrors?: boolean };
  }
}

export type GlobalErrorHandlers = {
  /** A query failed (after its retries). */
  onQueryError?: (error: ApiError, client: QueryClient) => void;
  /** A mutation failed and did not set `meta: { handlesOwnErrors: true }`. */
  onMutationError?: (error: ApiError, client: QueryClient) => void;
  /** Any request answered 401: the session has expired. Called for queries and all mutations. */
  onUnauthorized?: (client: QueryClient) => void;
  /**
   * Any request answered MSG119 (unknown site) or MSG120 (Suspended Workspace). Called for queries and all
   * mutations, also those with `handlesOwnErrors`: the whole app is replaced by the site page, so no screen may
   * keep the error for itself.
   */
  onSiteUnavailable?: (client: QueryClient) => void;
};

/** 4xx means "the request is wrong" — retrying cannot help. Network errors and 5xx retry once. */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (isApiError(error) && error.status >= 400 && error.status < 500) return false;
  return failureCount < 1;
}

/**
 * Cache defaults for the whole app (guideline 06 §4). Don't override them per hook without a reason.
 * The global error behaviour is passed in by the app (app/providers/global-errors.ts) so this module stays
 * free of UI code.
 */
export function createQueryClient(handlers: GlobalErrorHandlers = {}): QueryClient {
  // The caches call `handle` only once requests run, so `client` (declared below) exists by then.
  const handle = (thrown: unknown, kind: 'query' | 'mutation', handlesOwnErrors: boolean) => {
    // A bug in a queryFn throws a plain Error: shown as MSG06 like any unexpected failure.
    const error = isApiError(thrown) ? thrown : new ApiError({ status: 0, code: 'MSG06' });
    if (error.status === 401) {
      handlers.onUnauthorized?.(client);
      return;
    }
    if (error.code === 'MSG119' || error.code === 'MSG120') {
      handlers.onSiteUnavailable?.(client);
      return;
    }
    if (kind === 'query') handlers.onQueryError?.(error, client);
    else if (!handlesOwnErrors) handlers.onMutationError?.(error, client);
  };

  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error) => handle(error, 'query', false),
    }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _onMutateResult, mutation) =>
        handle(error, 'mutation', mutation.meta?.handlesOwnErrors === true),
    }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        retry: shouldRetry,
        refetchOnWindowFocus: true,
      },
      mutations: { retry: 0 },
    },
  });
  return client;
}
