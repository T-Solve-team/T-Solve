import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useProductContext } from '@forge/react';
import { requestJira } from '@forge/bridge';
import {
  getIssuePanel,
  getReviewFeedback,
  getTicket,
  openSolutionForm,
  searchTickets,
  TsolveError,
} from '../api/tsolveClient';
import { message } from '../messages';
import { PANEL_LABELS, IssuePanel } from './IssuePanel';
import { PREVIEW_LABELS } from './TicketPreviewModal';

jest.mock('@forge/bridge', () => ({ requestJira: jest.fn(), showFlag: jest.fn(), invokeRemote: jest.fn() }));
jest.mock('../api/tsolveClient', () => {
  const actual = jest.requireActual('../api/tsolveClient');
  return {
    ...actual,
    getIssuePanel: jest.fn(),
    getReviewFeedback: jest.fn(),
    getTicket: jest.fn(),
    openSolutionForm: jest.fn(),
    searchTickets: jest.fn(),
  };
});

const PANEL = { linked: true, ticketStatus: null, hasDraft: false, mayOpenForm: true, canSearch: true };
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
    await show({ linked: false, ticketStatus: null, hasDraft: false, mayOpenForm: false, canSearch: false });

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

  it('AC-008.6 shows MSG76 instead of the search box', async () => {
    await show({ ...PANEL, canSearch: false });

    expect(screen.getByText(message('MSG76'))).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(PANEL_LABELS.searchPlaceholder)).not.toBeInTheDocument();
  });

  it('AC-008.4 lists title, Ticket ID and Department of each result', async () => {
    searchTickets.mockResolvedValue([{ id: 't-1', ticketId: 'ITSUP-9', title: 'VPN fix', departmentName: 'IT' }]);
    await show(PANEL);

    fireEvent.change(screen.getByPlaceholderText(PANEL_LABELS.searchPlaceholder), { target: { value: 'vpn' } });
    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.search }));

    expect(await screen.findByRole('button', { name: 'ITSUP-9 · VPN fix · IT' })).toBeInTheDocument();
    expect(searchTickets).toHaveBeenCalledWith('vpn');
  });

  it('AC-008.5 shows MSG04 when nothing is found', async () => {
    searchTickets.mockResolvedValue([]);
    await show(PANEL);

    fireEvent.change(screen.getByPlaceholderText(PANEL_LABELS.searchPlaceholder), { target: { value: 'printer' } });
    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.search }));

    expect(await screen.findByText(message('MSG04'))).toBeInTheDocument();
  });

  it('AC-008.8 shows MSG06 in the search area when the search fails', async () => {
    searchTickets.mockRejectedValue(new TsolveError('MSG06', { status: 503 }));
    await show({ ...PANEL, ticketStatus: 'PENDING_REVIEW', mayOpenForm: false });

    fireEvent.change(screen.getByPlaceholderText(PANEL_LABELS.searchPlaceholder), { target: { value: 'vpn' } });
    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.search }));

    expect(await screen.findByText(message('MSG06'))).toBeInTheDocument();
    expect(screen.getByText(message('MSG65'))).toBeInTheDocument();
  });

  it('sends no search for an empty query', async () => {
    await show(PANEL);

    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.search }));

    expect(searchTickets).not.toHaveBeenCalled();
  });

  it('AC-009.1 opens the Published Ticket Preview of a result', async () => {
    searchTickets.mockResolvedValue([{ id: 't-1', ticketId: 'ITSUP-9', title: 'VPN fix', departmentName: 'IT' }]);
    getTicket.mockResolvedValue({
      id: 't-1',
      ticketId: 'ITSUP-9',
      title: 'VPN fix',
      departmentName: 'IT',
      solution: SECTIONS,
      webUrl: 'https://acme.tsolve.io.vn/tickets/t-1',
    });
    await show(PANEL);
    fireEvent.change(screen.getByPlaceholderText(PANEL_LABELS.searchPlaceholder), { target: { value: 'vpn' } });
    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.search }));

    fireEvent.click(await screen.findByRole('button', { name: 'ITSUP-9 · VPN fix · IT' }));

    expect(await screen.findByText('ITSUP-9 · Department: IT')).toBeInTheDocument();
    expect(screen.getByText('Update')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: PREVIEW_LABELS.openInTsolve })).toHaveAttribute(
      'href',
      'https://acme.tsolve.io.vn/tickets/t-1',
    );
  });

  it('AC-009.2 shows MSG08 when the ticket may not be seen', async () => {
    searchTickets.mockResolvedValue([{ id: 't-1', ticketId: 'ITSUP-9', title: 'VPN fix', departmentName: 'IT' }]);
    getTicket.mockRejectedValue(new TsolveError('MSG08', { status: 403 }));
    await show(PANEL);
    fireEvent.change(screen.getByPlaceholderText(PANEL_LABELS.searchPlaceholder), { target: { value: 'vpn' } });
    fireEvent.click(screen.getByRole('button', { name: PANEL_LABELS.search }));

    fireEvent.click(await screen.findByRole('button', { name: 'ITSUP-9 · VPN fix · IT' }));

    expect(await screen.findByText(message('MSG08'))).toBeInTheDocument();
  });
});
