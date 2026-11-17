import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { router } from '@forge/bridge';
import { getAccountLink, startAccountLink, TsolveError } from '../api/tsolveClient';
import { message } from '../messages';
import { AccountSettingsPage, POLL_INTERVAL_MS, POLL_LIMIT_MS, SETTINGS_LABELS } from './AccountSettingsPage';

jest.mock('@forge/bridge', () => ({
  router: { open: jest.fn(() => Promise.resolve()), navigate: jest.fn(() => Promise.resolve()) },
  invokeRemote: jest.fn(),
}));
jest.mock('../api/tsolveClient', () => {
  const actual = jest.requireActual('../api/tsolveClient');
  return { ...actual, getAccountLink: jest.fn(), startAccountLink: jest.fn() };
});

const URL = 'https://acme.tsolve.io.vn/jira-link?request=abc';
const NOT_LINKED = { status: 'NOT_LINKED' };
const LINKED = { status: 'LINKED', fullName: 'An Nguyen', email: 'an@acme.example', linkedAt: '2026-11-17T08:00:00Z' };
const RELINKED = { ...LINKED, fullName: 'Binh Dang', email: 'binh@acme.example', linkedAt: '2026-11-17T09:00:00Z' };

beforeEach(() => {
  jest.clearAllMocks();
  jest.useRealTimers();
});

async function show(first) {
  getAccountLink.mockResolvedValueOnce(first);
  render(<AccountSettingsPage />);
  await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument());
}

async function tick(ms) {
  await act(async () => {
    jest.advanceTimersByTime(ms);
  });
}

describe('AccountSettingsPage', () => {
  it('AC-014.2 reads the link status when the page opens', async () => {
    await show(NOT_LINKED);

    expect(getAccountLink).toHaveBeenCalledTimes(1);
  });

  it('AC-014.3 offers "Sign in to T-Solve" to a Jira account that is not linked', async () => {
    await show(NOT_LINKED);

    expect(screen.getByText(message('NOT_LINKED'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: SETTINGS_LABELS.signIn })).toBeInTheDocument();
  });

  it('AC-014.4 shows the linked Account and "Link another account", and no unlink', async () => {
    await show(LINKED);

    expect(screen.getByText('Linked to T-Solve as An Nguyen (an@acme.example).')).toBeInTheDocument();
    expect(screen.getAllByRole('button')).toHaveLength(1);
    expect(screen.getByRole('button', { name: SETTINGS_LABELS.linkAnother })).toBeInTheDocument();
  });

  it('AC-014.5 shows MSG124 and no button for a site of no Workspace', async () => {
    await show({ status: 'SITE_NOT_CONNECTED' });

    expect(screen.getByText(message('MSG124'))).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('AC-014.6 AC-014.7 starts a link, opens it in a new tab and shows MSG74 for the page', async () => {
    await show(NOT_LINKED);
    startAccountLink.mockResolvedValue(URL);

    await act(async () => fireEvent.click(screen.getByRole('button', { name: SETTINGS_LABELS.signIn })));

    expect(startAccountLink).toHaveBeenCalledTimes(1);
    expect(router.open).toHaveBeenCalledWith(URL);
    expect(screen.getByText(message('MSG74_PAGE'))).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('AC-014.8 re-checks every 5 seconds and shows the new link as soon as it exists', async () => {
    await show(NOT_LINKED);
    jest.useFakeTimers();
    startAccountLink.mockResolvedValue(URL);
    getAccountLink.mockResolvedValueOnce(NOT_LINKED).mockResolvedValueOnce(LINKED);
    await act(async () => fireEvent.click(screen.getByRole('button', { name: SETTINGS_LABELS.signIn })));

    await tick(POLL_INTERVAL_MS);
    expect(screen.getByText(message('MSG74_PAGE'))).toBeInTheDocument();
    await tick(POLL_INTERVAL_MS);

    expect(screen.getByText('Linked to T-Solve as An Nguyen (an@acme.example).')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: SETTINGS_LABELS.linkAnother })).toBeInTheDocument();
    const calls = getAccountLink.mock.calls.length;
    await tick(POLL_INTERVAL_MS * 3);
    expect(getAccountLink).toHaveBeenCalledTimes(calls);
  });

  it('AC-014.8 waits for a link later than the one it knew when "Link another account" was clicked', async () => {
    await show(LINKED);
    jest.useFakeTimers();
    startAccountLink.mockResolvedValue(URL);
    getAccountLink.mockResolvedValueOnce(LINKED).mockResolvedValueOnce(RELINKED);
    await act(async () => fireEvent.click(screen.getByRole('button', { name: SETTINGS_LABELS.linkAnother })));

    await tick(POLL_INTERVAL_MS);
    expect(screen.getByText(message('MSG74_PAGE'))).toBeInTheDocument();
    await tick(POLL_INTERVAL_MS);

    expect(screen.getByText('Linked to T-Solve as Binh Dang (binh@acme.example).')).toBeInTheDocument();
  });

  it('AC-014.8 stops after 10 minutes and shows the last state with its button again', async () => {
    await show(NOT_LINKED);
    jest.useFakeTimers();
    startAccountLink.mockResolvedValue(URL);
    getAccountLink.mockResolvedValue(NOT_LINKED);
    await act(async () => fireEvent.click(screen.getByRole('button', { name: SETTINGS_LABELS.signIn })));

    await tick(POLL_LIMIT_MS);

    expect(screen.queryByText(message('MSG74_PAGE'))).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: SETTINGS_LABELS.signIn })).toBeInTheDocument();
    const calls = getAccountLink.mock.calls.length;
    await tick(POLL_INTERVAL_MS * 3);
    expect(getAccountLink).toHaveBeenCalledTimes(calls);
  });

  it.each(['MSG124', 'MSG08', 'MSG06'])('AC-014.9 shows %s, opens no tab and keeps the button', async (code) => {
    await show(NOT_LINKED);
    startAccountLink.mockRejectedValue(new TsolveError(code, { status: code === 'MSG06' ? 503 : 403 }));

    await act(async () => fireEvent.click(screen.getByRole('button', { name: SETTINGS_LABELS.signIn })));

    expect(screen.getByText(message(code))).toBeInTheDocument();
    expect(router.open).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: SETTINGS_LABELS.signIn })).toBeInTheDocument();
  });

  it('shows MSG06 when the status cannot be read', async () => {
    getAccountLink.mockRejectedValueOnce(new TsolveError('MSG06'));
    render(<AccountSettingsPage />);

    expect(await screen.findByText(message('MSG06'))).toBeInTheDocument();
  });
});
