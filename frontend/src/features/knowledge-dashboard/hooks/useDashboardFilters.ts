import dayjs from 'dayjs';
import { useSearchParams } from 'react-router';
import type { GetKnowledgeDashboardParams } from '@/shared/api';
import { APP_TIME_ZONE } from '@/shared/lib';

export type DashboardPeriod = '7d' | '30d' | '90d' | 'custom';

export function dashboardToday(): string {
  return dayjs().tz(APP_TIME_ZONE).format('YYYY-MM-DD');
}

/** Round-trip calendar dates so normalized dates such as February 30 cannot reach the API. */
export function dashboardRange(from: string | null, to: string | null, today = dashboardToday()) {
  const isDate = (value: string | null): value is string =>
    value !== null &&
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    dayjs(value).isValid() &&
    dayjs(value).format('YYYY-MM-DD') === value;
  return (
    isDate(from) &&
    isDate(to) &&
    from <= to &&
    to <= today &&
    dayjs(to).diff(dayjs(from), 'day') < 366
  );
}

export function presetRange(period: Exclude<DashboardPeriod, 'custom'>, today = dashboardToday()) {
  const days = { '7d': 7, '30d': 30, '90d': 90 }[period];
  return {
    from: dayjs(today)
      .subtract(days - 1, 'day')
      .format('YYYY-MM-DD'),
    to: today,
  };
}

export function useDashboardFilters() {
  const [search, setSearch] = useSearchParams();
  const rawPeriod = search.get('period');
  const period: DashboardPeriod =
    rawPeriod === '7d' || rawPeriod === '30d' || rawPeriod === '90d' || rawPeriod === 'custom'
      ? rawPeriod
      : search.has('from') || search.has('to')
        ? 'custom'
        : '30d';
  // A preset always means the last N days from today: dates left in the URL count only for a custom range.
  const range =
    period === 'custom' ? { from: search.get('from'), to: search.get('to') } : presetRange(period);
  const isRangeValid = dashboardRange(range.from, range.to);
  const departmentId = search.get('departmentId') || undefined;
  const projectId = search.get('projectId') || undefined;

  function update(changes: Record<string, string | null>) {
    // Dependent filters change atomically, without an intermediate request for the old Project.
    setSearch((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(changes)) {
        if (value) next.set(key, value);
        else next.delete(key);
      }
      return next;
    });
  }

  const params: GetKnowledgeDashboardParams = {
    from: range.from ?? undefined,
    to: range.to ?? undefined,
    departmentId,
    projectId,
  };
  return {
    period,
    ...range,
    departmentId,
    projectId,
    isRangeValid,
    params,
    setPeriod: (value: DashboardPeriod) =>
      update(
        value === 'custom'
          ? { period: value, from: null, to: null }
          : { period: value, ...presetRange(value) },
      ),
    setRange: (from: string | null, to: string | null) => update({ period: 'custom', from, to }),
    setDepartment: (value: string) => update({ departmentId: value, projectId: null }),
    setProject: (value: string) => update({ projectId: value }),
  };
}
