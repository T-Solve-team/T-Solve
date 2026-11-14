import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import { useProductContext } from '@forge/react';
import { events, requestJira, showFlag } from '@forge/bridge';
import { openSolutionForm } from '../api/tsolveClient';
import { BackgroundApp, POLL_MS } from './BackgroundApp';

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

let movedAt;
let movedBy;

function issue(key) {
  const fields = { status: { statusCategory: { key } }, summary: 'VPN', description: null };
  if (movedAt === undefined) {
    return { ok: true, json: async () => ({ fields }) };
  }
  const at = new Date(movedAt).toISOString();
  return {
    ok: true,
    json: async () => ({
      fields: { ...fields, statuscategorychangedate: at },
      changelog: { histories: [{ author: { accountId: movedBy }, created: at, items: [{ field: 'status' }] }] },
    }),
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  category = 'indeterminate';
  movedAt = undefined;
  movedBy = 'me';
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
  it('opens the form when Jira reloads the script right after the viewer moved the issue into Done (BUG-214)', async () => {
    category = 'done';
    movedAt = Date.now() - 3000;

    render(<BackgroundApp />);

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(openSolutionForm).toHaveBeenCalledTimes(1);
  });

  describe('when Jira sends no event and does not reload the script (BUG-214)', () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    async function moveIntoDone(by) {
      await start();
      category = 'done';
      movedAt = Date.now();
      movedBy = by;
      await act(async () => {
        await jest.advanceTimersByTimeAsync(POLL_MS);
      });
    }

    it('notices the viewer moving the issue into Done on its own check', async () => {
      await moveIntoDone('me');

      expect(openSolutionForm).toHaveBeenCalledTimes(1);
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('opens nothing when someone else moved the issue into Done', async () => {
      await moveIntoDone('other');

      expect(openSolutionForm).not.toHaveBeenCalled();
    });

    it('asks again when the viewer moves the issue out of Done and back in the same page load (BUG-220)', async () => {
      openSolutionForm.mockResolvedValue({ mayOpen: false, origin: null, sections: null, message: null });
      await moveIntoDone('me');
      expect(openSolutionForm).toHaveBeenCalledTimes(1);

      category = 'indeterminate';
      await act(async () => {
        await jest.advanceTimersByTimeAsync(POLL_MS);
      });
      category = 'done';
      movedAt = Date.now();
      openSolutionForm.mockResolvedValue(FORM);
      await act(async () => {
        await jest.advanceTimersByTimeAsync(POLL_MS);
      });

      expect(openSolutionForm).toHaveBeenCalledTimes(2);
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('asks only once when the event and the check both see the move', async () => {
      await moveIntoDone('me');
      await act(() => onChange({ changes: [{ changeType: 'updated', atlassianId: 'me' }] }));
      await act(async () => {
        await jest.advanceTimersByTimeAsync(POLL_MS);
      });

      expect(openSolutionForm).toHaveBeenCalledTimes(1);
    });
  });

  it('does not open on load for an issue moved into Done long ago (AC-001.5, BUG-214)', async () => {
    category = 'done';
    movedAt = Date.now() - 10 * 60 * 1000;
    await start();

    expect(openSolutionForm).not.toHaveBeenCalled();
  });

  it('does not open on load when someone else just moved the issue into Done (BUG-214)', async () => {
    category = 'done';
    movedAt = Date.now() - 3000;
    movedBy = 'other';
    await start();

    expect(openSolutionForm).not.toHaveBeenCalled();
  });
});
