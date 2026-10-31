import { dateRange, vietnamToday } from './audit-filters';

describe('audit date filters', () => {
  it('AC-001.14 leaves dates unrestricted when both are absent', () => {
    expect(dateRange(null, null)).toEqual({ valid: true });
  });

  it('AC-001.15 includes both complete Vietnam days and fractional end-of-day seconds', () => {
    const range = dateRange('2026-10-01', '2026-10-01', '2026-10-31');
    expect(range).toEqual({
      valid: true,
      from: '2026-10-01',
      to: '2026-10-01',
      start: Date.parse('2026-09-30T17:00:00Z'),
      end: Date.parse('2026-10-01T17:00:00Z'),
    });
  });

  it.each([
    ['2026-02-30', '2026-03-01'],
    ['2026-10-02', '2026-10-01'],
    ['2026-10-01', null],
    [null, '2026-10-01'],
    ['2026-11-01', '2026-11-02'],
    ['nonsense', '2026-10-01'],
    ['2026-1-01', '2026-10-01'],
    ['', ''],
  ])('AC-001.16 rejects invalid calendar pair %s, %s', (from, to) => {
    expect(dateRange(from, to, '2026-10-31')).toEqual({ valid: false });
  });

  it('AC-001.16 uses Vietnam today across the UTC date boundary', () => {
    expect(vietnamToday(new Date('2026-10-30T17:01:00Z'))).toBe('2026-10-31');
    expect(dateRange('2024-02-29', '2024-02-29', '2026-10-31').valid).toBe(true);
  });
});
