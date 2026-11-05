function calendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

/** Validate a pair of inclusive calendar-day strings without converting them to timestamps. */
export function historyDateRange(from: string | null, to: string | null) {
  if (from === null && to === null) return { valid: true } as const;
  if (!from || !to || !calendarDate(from) || !calendarDate(to) || from > to) {
    return { valid: false } as const;
  }
  return {
    valid: true,
    from,
    to,
  } as const;
}
