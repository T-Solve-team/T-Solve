import { ReviewDecision } from '@/shared/api';

export const DECISION_LABELS: Record<ReviewDecision, string> = {
  APPROVE: 'Approve',
  REJECT: 'Reject',
  REQUEST_CHANGES: 'Request changes',
  UNPUBLISH: 'Unpublish',
  REPUBLISH: 'Re-publish',
  SEND_BACK: 'Send back to queue',
};
export function isReviewDecision(value: string | null): value is ReviewDecision {
  return value !== null && Object.hasOwn(ReviewDecision, value);
}
function calendarDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
/** Inclusive Vietnam calendar dates; no future-date restriction belongs to Review History. */
export function historyDateRange(from: string | null, to: string | null) {
  if (from === null && to === null) return { valid: true, start: undefined, end: undefined };
  if (!from || !to || !calendarDate(from) || !calendarDate(to) || from > to)
    return { valid: false, start: undefined, end: undefined };
  return {
    valid: true,
    start: Date.parse(`${from}T00:00:00+07:00`),
    end: Date.parse(`${to}T00:00:00+07:00`) + 24 * 60 * 60 * 1000,
  };
}
