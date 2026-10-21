import { env } from '@/shared/config/env';
import { ApiError, apiErrorFromResponse } from './errors';

/**
 * The ONLY place that calls `fetch` (guideline 06 §2). orval's generated functions call it as
 * `http<T>(url, init)` — `url` is the path from the spec (e.g. `/api/v1/me`).
 * - adds the base URL (empty = same origin: the Vite proxy in dev, Caddy in production);
 * - sends the session cookie;
 * - returns the parsed JSON body (undefined for 204 / empty bodies);
 * - rejects with ApiError for every non-2xx response and for network errors.
 */
export async function http<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes((init.method ?? 'GET').toUpperCase())) {
    const token = document.cookie.split('; ').find((cookie) => cookie.startsWith('XSRF-TOKEN='));
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token.slice('XSRF-TOKEN='.length)));
  }

  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}${url}`, {
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
  if (!response.ok) throw apiErrorFromResponse(response.status, body);
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
