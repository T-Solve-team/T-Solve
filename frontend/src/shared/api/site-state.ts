import { useSyncExternalStore } from 'react';
import type { MsgCode } from '@/shared/messages';

/**
 * What the responses said about this Workspace site (STORY-011) and about the session cookie (STORY-012).
 * `http()` writes it as soon as a response arrives, before any error handler runs, so a screen never renders
 * between "the server answered" and "the app knows".
 * - `site`: 'unknown' after MSG119 (no such site), 'suspended' after MSG120 (Workspace suspended), else 'ok'.
 * - `foreignSession`: a 401 with MSG123 — the cookie belongs to another Workspace site (AC-012.5).
 * The page cannot get out of these states except by loading it again (or, in mock mode, through the dev user
 * switcher and the test setup, which call `resetSiteState()`).
 */
export type SiteStatus = {
  readonly site: 'ok' | 'unknown' | 'suspended';
  readonly foreignSession: boolean;
};

const INITIAL: SiteStatus = { site: 'ok', foreignSession: false };

let status: SiteStatus = INITIAL;
const listeners = new Set<() => void>();

function update(next: SiteStatus): void {
  if (next.site === status.site && next.foreignSession === status.foreignSession) return;
  status = next;
  for (const listener of [...listeners]) listener();
}

/** The current status. The object changes only when a field does, so it also tells "something new happened". */
export function getSiteStatus(): SiteStatus {
  return status;
}

export function subscribeSiteStatus(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useSiteStatus(): SiteStatus {
  return useSyncExternalStore(subscribeSiteStatus, getSiteStatus, getSiteStatus);
}

/** Called by `http()` for every failed response. */
export function noteFailedResponse(httpStatus: number, code: MsgCode): void {
  if (code === 'MSG119') update({ ...status, site: 'unknown' });
  else if (code === 'MSG120') update({ ...status, site: 'suspended' });
  else if (httpStatus === 401 && code === 'MSG123') update({ ...status, foreignSession: true });
}

/** Back to "all fine": the dev user switcher on every pick, and the test setup after each test. */
export function resetSiteState(): void {
  update(INITIAL);
}
