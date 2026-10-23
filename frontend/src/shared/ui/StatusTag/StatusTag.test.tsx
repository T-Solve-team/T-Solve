import { render, screen } from '@testing-library/react';
import { StatusTag } from './StatusTag';
import { STATUS_TAGS, type StatusTagStatus } from './status-tags';

describe('StatusTag', () => {
  it('has exactly 17 statuses (the Figma kit plus the triage classes), without the retired ones', () => {
    expect(Object.keys(STATUS_TAGS)).toHaveLength(17);
    expect(Object.values(STATUS_TAGS).map((s) => s.label)).not.toContain('Deferred');
  });

  it('AC-007.1 has the triage classes Simple and Complex next to the six ticket statuses', () => {
    expect(Object.keys(STATUS_TAGS)).toEqual(
      expect.arrayContaining([
        'PENDING_REVIEW',
        'CHANGES_REQUESTED',
        'PUBLISHED',
        'REJECTED',
        'UNPUBLISHED',
        'EXPIRED',
        'SIMPLE',
        'COMPLEX',
      ]),
    );
  });

  it.each([
    ['SIMPLE', 'Simple'],
    ['COMPLEX', 'Complex'],
  ] as const)('AC-007.2 shows %s as "%s"', (status, label) => {
    render(<StatusTag status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('AC-007.3 rejects a value it does not know at type level', () => {
    expectTypeOf<'SENSITIVE'>().not.toMatchTypeOf<StatusTagStatus>();
    expectTypeOf<'SIMPLE'>().toMatchTypeOf<StatusTagStatus>();
  });

  it.each(Object.entries(STATUS_TAGS))('shows %s as "%o"', (status, { label }) => {
    render(<StatusTag status={status as StatusTagStatus} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
