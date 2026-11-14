import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
  submit = jest.fn().mockResolvedValue({ ticketId: 'ITSUP-12', status: 'PENDING_REVIEW' });
  onDone = jest.fn();
});

function open(form = { mayOpen: true, origin: 'DRAFT', sections: FULL, message: null }) {
  render(<SolutionFormModal issueKey="ITSUP-12" summary="VPN drops" form={form} onDone={onDone} submit={submit} />);
}

const field = (label) => screen.getByLabelText(new RegExp(`^${label}`));
const click = (name) => fireEvent.click(screen.getByRole('button', { name }));

describe('SolutionFormModal', () => {
  it('AC-002.1 shows the five sections filled with the AI draft', () => {
    open({ mayOpen: true, origin: 'AI_DRAFT', sections: FULL, message: null });

    expect(field('Symptoms')).toHaveValue('Drops hourly');
    expect(field('Scope of applicability')).toHaveValue('Windows laptops');
    expect(field('Workaround')).toHaveValue('');
  });

  it('AC-002.2 shows MSG67 when the form opens with the issue text', () => {
    open({ mayOpen: true, origin: 'ISSUE', sections: { ...EMPTY_SECTIONS, symptoms: 'VPN' }, message: { code: 'MSG67' } });

    expect(screen.getByText(message('MSG67'))).toBeInTheDocument();
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

  it('does not save a Draft when Close comes while Finish is still sending (BUG-221)', async () => {
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

  it('AC-007.1 AC-007.2 shows the rewrite next to the Solution and Accept replaces it', async () => {
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
