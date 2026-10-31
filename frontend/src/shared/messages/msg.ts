import { MESSAGES, type MsgCode } from './catalog';
import { TRACKER_NAME } from '@/shared/config/tracker';

export type MsgParams = Record<string, string | number>;

/** True when `value` is an active SRS message code (e.g. a code sent by the backend). */
export function isMsgCode(value: string): value is MsgCode {
  return Object.hasOwn(MESSAGES, value);
}

/**
 * The text of a system message with its `{placeholders}` filled in.
 * `msg('MSG14', { count: 3 })` → "3 ticket(s) approved and published."
 * `{tracker_name}` defaults to the configured tracker ("Jira"). A missing parameter keeps its `{placeholder}`
 * so the gap is visible in review.
 */
export function msg(code: MsgCode, params: MsgParams = {}): string {
  const values: MsgParams = { tracker_name: TRACKER_NAME, ...params };
  return MESSAGES[code].text.replace(/\{(\w+)\}/g, (placeholder, name: string) =>
    Object.hasOwn(values, name) ? String(values[name]) : placeholder,
  );
}
