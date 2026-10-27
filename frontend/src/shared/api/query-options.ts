import type { QueryFunction } from '@tanstack/react-query';
import { markBackground } from './background';

/**
 * orval's `override.query.queryOptions` mutator (orval.config.ts): wraps the `queryFn` of every generated query.
 * When the query already holds data, the run is a refetch (interval, window focus, reconnect, invalidation, or
 * a Refresh / Try again the user started) and its request is marked as a background request (AC-012.6). A first
 * load of a query has no data and is not marked (AC-012.7).
 */
export function withBackgroundMarking<T extends { queryFn?: unknown }>(options: T): T {
  const queryFn = options.queryFn;
  if (typeof queryFn !== 'function') return options;
  const wrapped: QueryFunction = (context) => {
    if (context.client.getQueryState(context.queryKey)?.data !== undefined) {
      markBackground(context.signal);
    }
    return (queryFn as QueryFunction)(context);
  };
  return { ...options, queryFn: wrapped };
}
