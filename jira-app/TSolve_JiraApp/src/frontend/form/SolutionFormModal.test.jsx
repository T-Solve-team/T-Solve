import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { invokeRemote } from '@forge/bridge';
import { rewriteSolution, saveSolutionDraft, TsolveError } from '../api/tsolveClient';
import { message } from '../messages';
import { doneFlag, LABELS, SolutionFormModal } from './SolutionFormModal';
import { EMPTY_SECTIONS, validateSections } from './sections';

jest.mock('../api/tsolveClient', () => {
  const actual = jest.requireActual('../api/tsolveClient');
  return { ...actual, rewriteSolution: jest.fn(), saveSolutionDraft: jest.fn(), submitSolution: jest.fn() };
});
jest.mock('@forge/bridge', () => ({ invokeRemote: jest.fn() }));

const FULL = {
  symptoms: 'Drops hourly',
  rootCause: 'Old client',
  resolutionSteps: 'Update to 5.2',
  workaround: '',
  scopeOfApplicability: 'Windows laptops',
};

let submit;
let onDone;

beforeEach(() => {
  jest.clearAllMocks();
  rewriteSolution.mockReset();
  invokeRemote.mockReset();
  submit = jest.fn().mockResolvedValue({ ticketId: 'ITSUP-12', status: 'PENDING_REVIEW' });
  onDone = jest.fn();
});

afterEach(() => jest.useRealTimers());

function open(form = { mayOpen: true, origin: 'DRAFT', sections: FULL, message: null }) {
  render(<SolutionFormModal issueKey="ITSUP-12" summary="VPN drops" form={form} onDone={onDone} submit={submit} />);
}

const field = (label) => screen.getByLabelText(new RegExp(`^${label}`));
const click = (name) => fireEvent.click(screen.getByRole('button', { name }));

describe('SolutionFormModal', () => {
  it('AC-002.1 opens with the issue text in Symptoms, the other sections empty and no notice', () => {
    const symptoms = 'VPN drops\n\nAfter sleep';
    open({ mayOpen: true, origin: 'ISSUE', sections: { ...EMPTY_SECTIONS, symptoms }, message: null });

    expect(field('Symptoms')).toHaveValue(symptoms);
    expect(field('Root cause')).toHaveValue('');
    expect(field('Scope of applicability')).toHaveValue('');
    expect(screen.queryByText(message('MSG06'))).not.toBeInTheDocument();
  });

  it('AC-002.3 shows MSG01 under the first empty required section and sends nothing', () => {
    open({ mayOpen: true, origin: 'DRAFT', sections: { ...FULL, rootCause: '  ' }, message: null });

    click(LABELS.finish);

    expect(screen.getByRole('alert')).toHaveTextContent('The Root cause field is required.');
    expect(submit).not.toHaveBeenCalled();
  });

  it('AC-002.4 shows MSG02 over 10,000 characters and sends nothing', () => {
    open({ mayOpen: true, origin: 'DRAFT', sections: { ...FULL, symptoms: 'a'.repeat(10000) }, message: null });

    click(LABELS.finish);

    expect(screen.getByRole('alert')).toHaveTextContent('Solution must not exceed 10,000 characters.');
    expect(submit).not.toHaveBeenCalled();
  });

  it('AC-004.2 submits the Solution and closes the form', async () => {
    open();

    click(LABELS.finish);

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('submitted'));
    expect(submit).toHaveBeenCalledWith('ITSUP-12', { summary: 'VPN drops', sections: FULL });
  });

  it('AC-004.4 shows MSG69 and keeps the content when T-Solve stays unreachable', async () => {
    submit.mockRejectedValue(new TsolveError('MSG69'));
    open();

    click(LABELS.finish);

    expect(await screen.findByText(message('MSG69'))).toBeInTheDocument();
    expect(field('Symptoms')).toHaveValue('Drops hourly');
    expect(onDone).not.toHaveBeenCalled();
  });

  it('AC-002.5 shows a server MSG01 under its section', async () => {
    submit.mockRejectedValue(
      new TsolveError('MSG01', { status: 400, field: 'resolutionSteps', params: { field_name: 'Resolution steps' } }),
    );
    open();

    click(LABELS.finish);

    expect(await screen.findByRole('alert')).toHaveTextContent('The Resolution steps field is required.');
  });

  it('AC-005.1 saves the Draft when Close is clicked and closes the form', async () => {
    saveSolutionDraft.mockResolvedValue(undefined);
    open();

    click(LABELS.close);

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('closed'));
    expect(saveSolutionDraft).toHaveBeenCalledWith('ITSUP-12', FULL);
    expect(submit).not.toHaveBeenCalled();
  });

  it('AC-005.1 saves the Draft when the dialog itself is closed', async () => {
    saveSolutionDraft.mockResolvedValue(undefined);
    open();

    click('Close dialog');

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('closed'));
    expect(saveSolutionDraft).toHaveBeenCalledWith('ITSUP-12', FULL);
  });

  it('AC-005.1 keeps what was typed, not what the form opened with', async () => {
    saveSolutionDraft.mockResolvedValue(undefined);
    open();
    fireEvent.change(field('Workaround'), { target: { value: 'Use the web VPN' } });

    click(LABELS.close);

    await waitFor(() => expect(saveSolutionDraft).toHaveBeenCalledWith('ITSUP-12', { ...FULL, workaround: 'Use the web VPN' }));
  });

  it('AC-005.4 shows MSG06 and keeps the form open with its content when saving fails', async () => {
    saveSolutionDraft.mockRejectedValue(new TsolveError('MSG06'));
    open();

    click(LABELS.close);

    expect(await screen.findByText(message('MSG06'))).toBeInTheDocument();
    expect(field('Root cause')).toHaveValue('Old client');
    expect(onDone).not.toHaveBeenCalled();
  });

  it('does not save a Draft when Close comes while Finish is still sending (BUG-237)', async () => {
    let finishSent;
    submit.mockImplementation(() => new Promise((resolve) => (finishSent = resolve)));
    saveSolutionDraft.mockResolvedValue(undefined);
    open();

    click(LABELS.finish);
    click('Close dialog');
    finishSent({ ticketId: 'ITSUP-12', status: 'PENDING_REVIEW' });

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('submitted'));
    expect(saveSolutionDraft).not.toHaveBeenCalled();
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('AC-005.5 offers no Save as draft button, asks nothing on Close and never submits on Close', async () => {
    saveSolutionDraft.mockResolvedValue(undefined);
    open();

    expect(screen.queryByRole('button', { name: 'Save as draft' })).not.toBeInTheDocument();
    click(LABELS.close);

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('closed'));
    expect(screen.queryByText(/An empty solution will be submitted/)).not.toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();
  });

  it('AC-012.2 shows the change request (MSG64) above the sections of a resubmission', () => {
    render(
      <SolutionFormModal
        issueKey="ITSUP-12"
        summary="VPN drops"
        form={{ mayOpen: true, origin: 'TICKET', sections: FULL, message: null }}
        resubmit
        feedback={{ code: 'MSG64', params: { manager_name: 'Le Minh', comment: 'Add steps' } }}
        onDone={onDone}
        submit={submit}
      />,
    );

    expect(screen.getByText(/Changes requested by Le Minh: "Add steps"/)).toBeInTheDocument();
    expect(field('Resolution steps')).toHaveValue('Update to 5.2');
  });

  it('AC-012.4 sends resubmit with Finish and closes the form', async () => {
    render(
      <SolutionFormModal issueKey="ITSUP-12" summary="VPN drops" form={{ mayOpen: true, origin: 'TICKET', sections: FULL }} resubmit onDone={onDone} submit={submit} />,
    );

    click(LABELS.finish);

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('submitted'));
    expect(submit).toHaveBeenCalledWith('ITSUP-12', { summary: 'VPN drops', sections: FULL, resubmit: true });
  });

  it('AC-012.5 shows MSG06 and keeps the content when the ticket can no longer be resubmitted', async () => {
    submit.mockRejectedValue(new TsolveError('MSG06', { status: 409 }));
    render(
      <SolutionFormModal issueKey="ITSUP-12" summary="VPN drops" form={{ mayOpen: true, origin: 'TICKET', sections: FULL }} resubmit onDone={onDone} submit={submit} />,
    );

    click(LABELS.finish);

    expect(await screen.findByText(message('MSG06'))).toBeInTheDocument();
    expect(field('Symptoms')).toHaveValue('Drops hourly');
    expect(onDone).not.toHaveBeenCalled();
  });

  it('AC-007.1 AC-007.2 shows the rewrite next to the Solution and Accept replaces it (BUG-345)', async () => {
    rewriteSolution.mockResolvedValue({ ...FULL, symptoms: 'The VPN drops every hour.' });
    open();

    click(LABELS.rewrite);

    expect(await screen.findByText(LABELS.rewriteTitle)).toBeInTheDocument();
    expect(screen.getByText('Drops hourly')).toBeInTheDocument();
    expect(screen.getByText('The VPN drops every hour.')).toBeInTheDocument();
    click(LABELS.accept);
    expect(field('Symptoms')).toHaveValue('The VPN drops every hour.');
  });

  it('AC-007.3 keeps the Solution on Discard', async () => {
    rewriteSolution.mockResolvedValue({ ...FULL, symptoms: 'The VPN drops every hour.' });
    open();

    click(LABELS.rewrite);
    await screen.findByText(LABELS.rewriteTitle);
    click(LABELS.discard);

    expect(field('Symptoms')).toHaveValue('Drops hourly');
  });

  it('AC-007.4 shows MSG68 and leaves the Solution unchanged when the rewrite fails', async () => {
    rewriteSolution.mockRejectedValue(new TsolveError('MSG68', { status: 503 }));
    open();

    click(LABELS.rewrite);

    expect(await screen.findByText(message('MSG68'))).toBeInTheDocument();
    expect(field('Symptoms')).toHaveValue('Drops hourly');
  });

  it.each([false, true])('AC-007.8 AC-007.1 AC-012.7 disables Rewrite while pending and enables it after DONE (resubmit=%s)', async (resubmit) => {
    jest.useFakeTimers();
    rewriteSolution.mockImplementation(jest.requireActual('../api/tsolveClient').rewriteSolution);
    invokeRemote
      .mockResolvedValueOnce({ status: 202, body: { jobId: 'rewrite-1' } })
      .mockResolvedValueOnce({ status: 200, body: { status: 'PENDING' } })
      .mockResolvedValueOnce({ status: 200, body: { status: 'DONE', sections: { ...FULL, symptoms: 'Rewritten symptoms' } } });
    render(<SolutionFormModal issueKey="ITSUP-12" summary="VPN" form={{ sections: FULL }} resubmit={resubmit} onDone={onDone} submit={submit} />);

    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeEnabled();
    click(LABELS.rewrite);
    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeDisabled();
    expect(screen.getByRole('button', { name: LABELS.close })).toBeEnabled();
    click(LABELS.rewrite);
    expect(rewriteSolution).toHaveBeenCalledTimes(1);
    expect(rewriteSolution).toHaveBeenCalledWith(FULL, { signal: expect.any(AbortSignal) });
    await act(async () => { await jest.advanceTimersByTimeAsync(1500); });
    expect(screen.queryByText(LABELS.rewriteTitle)).not.toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeDisabled();
    expect(invokeRemote).toHaveBeenCalledTimes(2);
    await act(async () => { await jest.advanceTimersByTimeAsync(1500); });

    expect(screen.getByText(LABELS.rewriteTitle)).toBeInTheDocument();
    expect(screen.getByText('Rewritten symptoms')).toBeInTheDocument();
    click(LABELS.discard);
    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeEnabled();
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-007.8 AC-007.4 enables Rewrite after FAILED, shows MSG68 and preserves every edited section', async () => {
    jest.useFakeTimers();
    rewriteSolution.mockImplementation(jest.requireActual('../api/tsolveClient').rewriteSolution);
    invokeRemote.mockResolvedValueOnce({ status: 202, body: { jobId: 'rewrite-1' } })
      .mockResolvedValue({ status: 200, body: { status: 'FAILED' } });
    open();
    fireEvent.change(field('Workaround'), { target: { value: 'Use web VPN' } });
    click(LABELS.rewrite);
    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeDisabled();
    await act(async () => { await jest.advanceTimersByTimeAsync(1500); });

    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeEnabled();
    expect(screen.getByText(message('MSG68'))).toBeInTheDocument();
    expect(screen.queryByText(LABELS.rewriteTitle)).not.toBeInTheDocument();
    ['Symptoms', 'Root cause', 'Resolution steps', 'Workaround', 'Scope of applicability'].forEach((label, index) => {
      expect(field(label)).toHaveValue(Object.values({ ...FULL, workaround: 'Use web VPN' })[index]);
    });
    expect(submit).not.toHaveBeenCalled();
  });

  it.each(['Close', 'Close dialog'])(
    'AC-005.1 AC-007.1 closing via %s during a rewrite aborts polling and saves the current Solution', async (control) => {
      jest.useFakeTimers();
      rewriteSolution.mockImplementation(jest.requireActual('../api/tsolveClient').rewriteSolution);
      invokeRemote.mockResolvedValueOnce({ status: 202, body: { jobId: 'rewrite-1' } })
        .mockResolvedValue({ status: 200, body: { status: 'PENDING' } });
      saveSolutionDraft.mockResolvedValue(undefined);
      open();
      click(LABELS.rewrite);
      await act(async () => { await jest.advanceTimersByTimeAsync(1500); });
      const { signal } = rewriteSolution.mock.calls[0][1];
      expect(screen.getByRole('button', { name: control })).toBeEnabled();
      await act(async () => { click(control); });
      await act(async () => { await jest.advanceTimersByTimeAsync(100000); });

      expect(signal.aborted).toBe(true);
      expect(invokeRemote).toHaveBeenCalledTimes(2);
      expect(saveSolutionDraft).toHaveBeenCalledWith('ITSUP-12', FULL);
      expect(onDone).toHaveBeenCalledWith('closed');
      expect(screen.queryByText(LABELS.rewriteTitle)).not.toBeInTheDocument();
      expect(screen.queryByText(message('MSG68'))).not.toBeInTheDocument();
      expect(jest.getTimerCount()).toBe(0);
    },
  );

  it('AC-007.8 AC-005.4 enables Rewrite after Close aborts the job and saving the Draft fails', async () => {
    jest.useFakeTimers();
    rewriteSolution.mockImplementation(jest.requireActual('../api/tsolveClient').rewriteSolution);
    invokeRemote.mockResolvedValueOnce({ status: 202, body: { jobId: 'rewrite-1' } })
      .mockResolvedValue({ status: 200, body: { status: 'PENDING' } });
    let rejectSave;
    saveSolutionDraft.mockImplementation(() => new Promise((resolve, reject) => { rejectSave = reject; }));
    open();
    click(LABELS.rewrite);
    await act(async () => { await jest.advanceTimersByTimeAsync(1500); });
    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeDisabled();
    expect(screen.getByRole('button', { name: LABELS.close })).toBeEnabled();
    const { signal } = rewriteSolution.mock.calls[0][1];

    await act(async () => { click(LABELS.close); });
    expect(signal.aborted).toBe(true);
    expect(saveSolutionDraft).toHaveBeenCalledWith('ITSUP-12', FULL);
    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeDisabled();
    await act(async () => { rejectSave(new TsolveError('MSG06')); });
    await act(async () => { await jest.advanceTimersByTimeAsync(100000); });

    expect(screen.getByRole('button', { name: LABELS.rewrite })).toBeEnabled();
    expect(screen.getByText(message('MSG06'))).toBeInTheDocument();
    expect(screen.queryByText(message('MSG68'))).not.toBeInTheDocument();
    expect(screen.queryByText(LABELS.rewriteTitle)).not.toBeInTheDocument();
    expect(field('Symptoms')).toHaveValue(FULL.symptoms);
    expect(onDone).not.toHaveBeenCalled();
    expect(invokeRemote).toHaveBeenCalledTimes(2);
    expect(jest.getTimerCount()).toBe(0);
  });

  it.each(['success', 'failure'])('AC-007.1 ignores a late rewrite %s after close', async (outcome) => {
    let resolve;
    let reject;
    rewriteSolution.mockImplementation(() => new Promise((done, fail) => { resolve = done; reject = fail; }));
    saveSolutionDraft.mockResolvedValue(undefined);
    open();
    click(LABELS.rewrite);
    const { signal } = rewriteSolution.mock.calls[0][1] ?? {};
    await act(async () => { click('Close dialog'); });
    await act(async () => {
      if (outcome === 'success') resolve({ ...FULL, symptoms: 'Late rewrite' });
      else reject(new TsolveError('MSG68'));
    });

    expect(signal?.aborted).toBe(true);
    expect(screen.queryByText(LABELS.rewriteTitle)).not.toBeInTheDocument();
    expect(screen.queryByText(message('MSG68'))).not.toBeInTheDocument();
    expect(field('Symptoms')).toHaveValue(FULL.symptoms);
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('AC-007.1 unmount aborts a pending rewrite without displaying an error', async () => {
    jest.useFakeTimers();
    rewriteSolution.mockImplementation(jest.requireActual('../api/tsolveClient').rewriteSolution);
    invokeRemote.mockResolvedValueOnce({ status: 202, body: { jobId: 'rewrite-1' } })
      .mockResolvedValue({ status: 200, body: { status: 'PENDING' } });
    const { unmount } = render(<SolutionFormModal issueKey="ITSUP-12" form={{ sections: FULL }} onDone={onDone} />);
    click(LABELS.rewrite);
    await act(async () => { await jest.advanceTimersByTimeAsync(1500); });
    const { signal } = rewriteSolution.mock.calls[0][1];
    await act(async () => { unmount(); });
    await act(async () => { await jest.advanceTimersByTimeAsync(100000); });

    expect(signal.aborted).toBe(true);
    expect(invokeRemote).toHaveBeenCalledTimes(2);
    expect(onDone).not.toHaveBeenCalled();
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-007.1 a new rewrite before the disabled state renders aborts the previous one and ignores its late result', async () => {
    let previous;
    let current;
    rewriteSolution.mockImplementationOnce(() => new Promise((resolve) => { previous = resolve; }))
      .mockImplementationOnce(() => new Promise((resolve) => { current = resolve; }));
    open();
    // Forge may deliver another click before the busy state reaches its UI.
    act(() => {
      click(LABELS.rewrite);
      click(LABELS.rewrite);
    });

    expect(rewriteSolution).toHaveBeenCalledTimes(2);
    expect(rewriteSolution.mock.calls[0][1].signal.aborted).toBe(true);
    expect(rewriteSolution.mock.calls[1][1].signal.aborted).toBe(false);
    await act(async () => { previous({ ...FULL, symptoms: 'Old rewrite' }); });
    expect(screen.queryByText(LABELS.rewriteTitle)).not.toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    await act(async () => { current({ ...FULL, symptoms: 'New rewrite' }); });
    expect(screen.getByText(LABELS.rewriteTitle)).toBeInTheDocument();
    expect(screen.getByText('New rewrite')).toBeInTheDocument();
    expect(screen.queryByText(message('MSG68'))).not.toBeInTheDocument();
  });
});

describe('doneFlag', () => {
  it('AC-004.2 AC-005.1 shows MSG66 after Finish and "Saved as draft" after Close', () => {
    expect(doneFlag('ITSUP-12', 'submitted')).toMatchObject({ title: message('MSG66'), type: 'success' });
    expect(doneFlag('ITSUP-12', 'closed')).toMatchObject({ title: 'Saved as draft', type: 'info' });
  });
});

describe('validateSections', () => {
  it('AC-002.3 accepts an empty Workaround', () => {
    expect(validateSections(FULL)).toBeNull();
  });

  it('AC-002.4 counts an emoji once', () => {
    const others = FULL.rootCause.length + FULL.resolutionSteps.length + FULL.scopeOfApplicability.length;
    const atLimit = { ...FULL, symptoms: '\u{1F600}'.repeat(10000 - others) };

    expect(validateSections(atLimit)).toBeNull();
    expect(validateSections({ ...atLimit, workaround: 'x' })).toMatchObject({ code: 'MSG02' });
  });
});

describe('SolutionFormModal with a Vietnamese input method', () => {
  it('BUG-332 keeps what the input method wrote when the form renders again', () => {
    open();
    // An input method rewrites a section before the app has seen the change.
    field('Symptoms').value = 'Mất kết nối mỗi giờ';
    fireEvent.change(field('Root cause'), { target: { value: '' } });

    click('Finish'); // Root cause is empty: MSG01 renders the form again

    expect(screen.getByRole('alert')).toHaveTextContent('The Root cause field is required.');
    expect(field('Symptoms')).toHaveValue('Mất kết nối mỗi giờ');
  });
});
