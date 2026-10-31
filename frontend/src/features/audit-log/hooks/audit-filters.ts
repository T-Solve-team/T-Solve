import { AuditAction } from '@/shared/api';
import { APP_TIME_ZONE } from '@/shared/lib';

export const ACTION_LABELS: Record<AuditAction, string> = {
  APPROVE: 'Approve',
  REJECT: 'Reject',
  REQUEST_CHANGES: 'Request changes',
  PUBLISH: 'Publish',
  UNPUBLISH: 'Unpublish',
  IMPORT: 'Import',
  DELETE: 'Delete',
  ROLE_CHANGE: 'Role change',
  PERMISSION_CHANGE: 'Permission change',
  TRACKER_CONFIGURATION_CHANGE: 'Tracker configuration change',
};

export function isAuditAction(value: string | null): value is AuditAction {
  return value !== null && Object.hasOwn(AuditAction, value);
}

export function vietnamToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: APP_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const part = (name: string) => parts.find((item) => item.type === name)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function calendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

/** Calendar days stay independent of the browser timezone. Vietnam has a fixed UTC+07:00 offset. */
export function dateRange(from: string | null, to: string | null, today = vietnamToday()) {
  if (from === null && to === null) return { valid: true };
  if (!from || !to || !calendarDate(from) || !calendarDate(to) || from > to || to > today) {
    return { valid: false };
  }
  return {
    valid: true,
    from,
    to,
    start: Date.parse(`${from}T00:00:00+07:00`),
    end: Date.parse(`${to}T00:00:00+07:00`) + 24 * 60 * 60 * 1000,
  };
}
