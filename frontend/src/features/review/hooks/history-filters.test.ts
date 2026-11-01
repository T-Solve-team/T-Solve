import { historyDateRange } from './history-filters';

describe('Review History date filters', () => {
  it('AC-008.3 accepts leap days/future dates and uses inclusive Vietnam dates', () => {
    expect(historyDateRange('2028-02-29', '2028-02-29')).toEqual({
      valid: true,
      start: Date.parse('2028-02-28T17:00:00Z'),
      end: Date.parse('2028-02-29T17:00:00Z'),
    });
    expect(historyDateRange('2099-01-01', '2099-01-02').valid).toBe(true);
    expect(historyDateRange(null, null).valid).toBe(true);
  });
  it.each([
    ['2027-02-29', '2027-03-01'],
    ['2028-02-30', '2028-03-01'],
    ['2026-11-02', '2026-11-01'],
    ['2026-11-01', null],
    [null, '2026-11-01'],
    ['2026-1-01', '2026-11-01'],
  ])('AC-008.3 rejects invalid or unpaired dates %s / %s', (from, to) => {
    expect(historyDateRange(from, to).valid).toBe(false);
  });
});
