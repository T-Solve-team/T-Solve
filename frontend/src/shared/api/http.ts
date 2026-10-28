import { BACKGROUND_HEADER, isBackground } from './background';
import { ApiError, apiErrorFromResponse } from './errors';
import { noteFailedResponse } from './site-state';

/**
 * The ONLY place that calls `fetch` (guideline 06 §2). orval's generated functions call it as
 * `http<T>(url, init)` — `url` is the path from the spec (e.g. `/api/v1/me`).
 * - sends the request to the page's own origin (a relative path: the Vite proxy in dev, Caddy in production), so
 *   the Host of the page decides the Workspace (AC-011.1); it adds no Workspace, site or domain header;
 * - sends the session cookie;
 * - adds `X-TSolve-Background: 1` for a request marked as background (AC-012.6);
 * - returns the parsed JSON body (undefined for 204 / empty bodies);
 * - rejects with ApiError for every non-2xx response and for network errors; MSG119 / MSG120 / 401 MSG123 are
 *   also noted in the site state (site-state.ts).
 */
export async function http<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (isBackground(init.signal)) headers.set(BACKGROUND_HEADER, '1');

  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      headers,
      credentials: 'include',
    });
  } catch (error) {
    // Aborted by TanStack Query (unmount, new key): not an error the user should see.
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError({ status: 0, code: 'MSG06', detail: 'Network error' });
  }

  const body = await readBody(response);
  if (!response.ok) {
    const error = apiErrorFromResponse(response.status, body);
    noteFailedResponse(error.status, error.code);
    throw error;
  }
  return body as T;
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  const type = response.headers.get('Content-Type') ?? '';
  if (!type.includes('json')) return text;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return undefined;
  }
}

/** Read by orval: the error type of every generated hook is ApiError (whatever the spec declares). */
export type ErrorType<_Error> = ApiError;
