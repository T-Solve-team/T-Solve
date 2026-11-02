import { historyDateRange } from './history-filters';

describe('suggestion history dates', () => {
  it('AC-001.2 leaves the range unrestricted by default', () => {
    expect(historyDateRange(null, null)).toEqual({ valid: true });
  });

  it.each([
    ['2026-02-30', '2026-03-01'],
    ['2026-11-02', '2026-11-01'],
    ['2026-11-01', null],
    [null, '2026-11-01'],
    ['nonsense', '2026-11-01'],
    ['2026-1-01', '2026-11-01'],
  ])('AC-001.5 rejects invalid pair %s / %s', (from, to) => {
    expect(historyDateRange(from, to)).toEqual({ valid: false });
  });

  it('AC-001.3 includes both Vietnam calendar days', () => {
    expect(historyDateRange('2026-11-01', '2026-11-01')).toEqual({
      valid: true,
      from: '2026-11-01',
      to: '2026-11-01',
      start: Date.parse('2026-10-31T17:00:00Z'),
      end: Date.parse('2026-11-01T17:00:00Z'),
    });
  });
});
