import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useProductContext } from '@forge/react';
import { events, requestJira } from '@forge/bridge';
import {
  getIssuePanel,
  getReviewFeedback,
  openSolutionForm,
} from '../api/tsolveClient';
import { message } from '../messages';
import { PANEL_LABELS, IssuePanel } from './IssuePanel';

jest.mock('@forge/bridge', () => ({
  events: { on: jest.fn(() => Promise.resolve({ unsubscribe: jest.fn() })) },
  requestJira: jest.fn(),
  showFlag: jest.fn(),
  invokeRemote: jest.fn(),
}));
jest.mock('../api/tsolveClient', () => {
  const actual = jest.requireActual('../api/tsolveClient');
  return {
    ...actual,
    getIssuePanel: jest.fn(),
    getReviewFeedback: jest.fn(),
    openSolutionForm: jest.fn(),
  };
});

const PANEL = { linked: true, ticketStatus: null, hasDraft: false, mayOpenForm: true };
const SECTIONS = { symptoms: 'Drops', rootCause: 'Old', resolutionSteps: 'Update', workaround: '', scopeOfApplicability: 'All' };

function issueIn(category) {
  requestJira.mockResolvedValue({
    ok: true,
    json: async () => ({ fields: { status: { statusCategory: { key: category } }, summary: 'VPN', description: null } }),
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  useProductContext.mockReturnValue({ accountId: 'me', extension: { issue: { key: 'ITSUP-12' } } });
  issueIn('done');
});

async function show(panel) {
  getIssuePanel.mockResolvedValue(panel);
  render(<IssuePanel />);
  await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument());
}

describe('IssuePanel', () => {
  it('AC-008.1 shows only MSG62 when the account is not linked', async () => {
    await show({ linked: false, ticketStatus: null, hasDraft: false, mayOpenForm: false });

    expect(screen.getByText(message('MSG62'))).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('AC-008.2 shows MSG65 while the ticket is in Pending review', async () => {
    await show({ ...PANEL, ticketStatus: 'PENDING_REVIEW', mayOpenForm: false });

    expect(screen.getByText(message('MSG65'))).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: PANEL_LABELS.writeSolution })).not.toBeInTheDocument();
  });

  it('AC-008.7 shows the change request with the manager and comment (MSG64)', async () => {
    getReviewFeedback.mockResolvedValue({ code: 'MSG64', params: { manager_name: 'Le Minh', comment: 'Add steps' } });

    await show({ ...PANEL, ticketStatus: 'CHANGES_REQUESTED', mayOpenForm: false });

    expect(await screen.findByText(/Changes requested by Le Minh: "Add steps"/)).toBeInTheDocument();
  });

  it('AC-008.3 shows MSG77 and reopens the form with the Draft', async () => {
    openSolutionForm.mockResolvedValue({ mayOpen: true, origin: 'DRAFT', sections: SECTIONS, message: null });
    await show({ ...PANEL, hasDraft: true });

    expect(screen.getByText(message('MSG77'))).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.continueDraft }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Symptoms/)).toHaveValue('Drops');
  });

  it('AC-001.4 offers the form button only while the issue is Done', async () => {
    await show(PANEL);
    expect(screen.getByRole('button', { name: PANEL_LABELS.writeSolution })).toBeInTheDocument();
  });

  it('AC-001.4 hides the form button while the issue is not Done', async () => {
    issueIn('indeterminate');
    await show(PANEL);

    expect(screen.queryByRole('button', { name: PANEL_LABELS.writeSolution })).not.toBeInTheDocument();
  });

  it('re-checks the status on Write Solution and hides the button once the issue left Done (BUG-213)', async () => {
    await show(PANEL);
    issueIn('indeterminate');

    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.writeSolution }));

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: PANEL_LABELS.writeSolution })).not.toBeInTheDocument(),
    );
    expect(openSolutionForm).not.toHaveBeenCalled();
  });

  it('reloads when Jira says the issue changed (BUG-213)', async () => {
    await show(PANEL);
    issueIn('indeterminate');

    await act(async () => events.on.mock.calls[0][1]({ changes: [{ changeType: 'updated', atlassianId: 'me' }] }));

    expect(screen.queryByRole('button', { name: PANEL_LABELS.writeSolution })).not.toBeInTheDocument();
  });

  it('shows no Published-ticket search (spec change 2026-11-14)', async () => {
    await show(PANEL);

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Search' })).not.toBeInTheDocument();
  });
});
