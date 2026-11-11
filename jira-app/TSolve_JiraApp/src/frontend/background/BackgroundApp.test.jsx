import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import { useProductContext } from '@forge/react';
import { events, requestJira, showFlag } from '@forge/bridge';
import { openSolutionForm } from '../api/tsolveClient';
import { BackgroundApp } from './BackgroundApp';

jest.mock('@forge/bridge', () => ({
  events: { on: jest.fn() },
  requestJira: jest.fn(),
  showFlag: jest.fn(),
  invokeRemote: jest.fn(),
}));
jest.mock('../api/tsolveClient', () => {
  const actual = jest.requireActual('../api/tsolveClient');
  return { ...actual, openSolutionForm: jest.fn() };
});

const FORM = {
  mayOpen: true,
  origin: 'AI_DRAFT',
  sections: { symptoms: 'Drops', rootCause: 'Old', resolutionSteps: 'Update', workaround: '', scopeOfApplicability: 'All' },
  message: null,
};

let category;
let onChange;

function issue(key) {
  return { ok: true, json: async () => ({ fields: { status: { statusCategory: { key } }, summary: 'VPN', description: null } }) };
}

beforeEach(() => {
  jest.clearAllMocks();
  category = 'indeterminate';
  requestJira.mockImplementation(async () => issue(category));
  events.on.mockImplementation((name, handler) => {
    onChange = handler;
    return Promise.resolve({ unsubscribe: jest.fn() });
  });
  useProductContext.mockReturnValue({ accountId: 'me', extension: { issue: { key: 'ITSUP-12' } } });
  openSolutionForm.mockResolvedValue(FORM);
});

async function start() {
  render(<BackgroundApp />);
  await waitFor(() => expect(requestJira).toHaveBeenCalledTimes(1));
}

describe('BackgroundApp', () => {
  it('AC-001.1 opens the form when the viewer moves the issue into Done', async () => {
    await start();
    category = 'done';

    await act(() => onChange({ changes: [{ changeType: 'updated', atlassianId: 'me' }] }));

    expect(openSolutionForm).toHaveBeenCalledWith('ITSUP-12', { summary: 'VPN', description: '' });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('AC-001.1 ignores a change made by someone else', async () => {
    await start();
    category = 'done';

    await act(() => onChange({ changes: [{ changeType: 'updated', atlassianId: 'other' }] }));

    expect(openSolutionForm).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('AC-001.5 does not open for an issue that was already Done', async () => {
    category = 'done';
    await start();

    await act(() => onChange({ changes: [{ changeType: 'commented', atlassianId: 'me' }] }));

    expect(openSolutionForm).not.toHaveBeenCalled();
  });

  it('AC-001.3 does not open when the server says the form may not open', async () => {
    openSolutionForm.mockResolvedValue({ mayOpen: false, origin: null, sections: null, message: null });
    await start();
    category = 'done';

    await act(() => onChange({ changes: [{ changeType: 'updated', atlassianId: 'me' }] }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(showFlag).not.toHaveBeenCalled();
  });
});
