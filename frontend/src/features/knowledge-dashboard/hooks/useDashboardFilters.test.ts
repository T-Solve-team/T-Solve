// Load the shared handlers before the timed global setup hook, as the page tests do.
import '@/mocks/server';
import { dashboardRange, dashboardToday, presetRange } from './useDashboardFilters';

describe('dashboard calendar periods', () => {
  it.each([
    ['7d', '2026-10-26'],
    ['30d', '2026-10-03'],
    ['90d', '2026-08-04'],
  ] as const)('AC-005.2 resolves %s to inclusive calendar days', (period, from) => {
    expect(presetRange(period, '2026-11-01')).toEqual({ from, to: '2026-11-01' });
  });

  it('AC-005.2 uses the Vietnam calendar day at the UTC date boundary', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2026-10-31T17:30:00Z'));
      expect(dashboardToday()).toBe('2026-11-01');
    } finally {
      vi.useRealTimers();
    }
  });

  it('AC-005.3 allows one day and exactly 366 inclusive days, including leap day', () => {
    expect(dashboardRange('2024-02-29', '2024-02-29', '2026-11-01')).toBe(true);
    expect(dashboardRange('2024-01-01', '2024-12-31', '2026-11-01')).toBe(true);
    expect(dashboardRange('2024-01-01', '2025-01-01', '2026-11-01')).toBe(false);
  });
});
