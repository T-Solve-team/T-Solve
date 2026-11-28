import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TicketSolution } from './TicketSolution';

describe('TicketSolution', () => {
  it('AC-001.11 shows only the sections that have text', () => {
    render(
      <TicketSolution
        solution={{
          symptoms: null,
          rootCause: 'Bad token',
          resolutionSteps: 'Restart',
          workaround: null,
          scopeOfApplicability: null,
        }}
      />,
    );

    expect(screen.getByText('Root cause')).toBeInTheDocument();
    expect(screen.getByText('Resolution steps')).toBeInTheDocument();
    expect(screen.queryByText('Symptoms')).not.toBeInTheDocument();
    expect(screen.queryByText('Scope of applicability')).not.toBeInTheDocument();
  });
});
