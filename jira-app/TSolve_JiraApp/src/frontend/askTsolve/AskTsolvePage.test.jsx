import fs from 'fs';
import path from 'path';
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useProductContext } from '@forge/react';
import { CreateIssueModal } from '@forge/jira-bridge';
import { askQuestion, getEligibility } from '../api/tsolveClient';
import { message } from '../messages';
import { AskTsolvePage } from './AskTsolvePage';

jest.mock('../api/tsolveClient', () => ({ getEligibility: jest.fn(), askQuestion: jest.fn() }));
jest.mock('@forge/jira-bridge', () => ({ CreateIssueModal: jest.fn() }));

const WIKI = { number: 2, type: 'WIKI_DOCUMENT', wikiDocumentId: 'w-1', title: 'VPN guide' };
const TICKET = {
  number: 1,
  type: 'TICKET_SOLUTION',
  solution: {
    symptoms: 'VPN drops',
    rootCause: 'Old client',
    resolutionSteps: 'Update the client',
    workaround: null,
    scopeOfApplicability: 'Windows laptops',
  },
};
const ANSWERED = { answerId: 'a-1', outcome: 'ANSWERED', text: 'Update the VPN client [1] [2]', citations: [WIKI, TICKET] };
const NO_SOURCE = { answerId: 'a-2', outcome: 'NO_SOURCE', text: '', citations: [] };

const failure = (code) => Object.assign(new Error(code), { code });

let open;

beforeEach(() => {
  jest.clearAllMocks();
  open = jest.fn();
  CreateIssueModal.mockImplementation(() => ({ open }));
  useProductContext.mockReturnValue({ extension: { project: { id: '10001', key: 'SAM1' } } });
  getEligibility.mockResolvedValue({ linked: true });
});

const box = () => screen.getByRole('textbox');
const type = (text) => fireEvent.change(box(), { target: { value: text } });
const send = () => fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));

async function ask(text, answer) {
  askQuestion.mockResolvedValueOnce(answer);
  type(text);
  send();
  await screen.findByRole('button', { name: 'Chưa được, tạo ticket' });
}

async function renderLinked() {
  render(<AskTsolvePage />);
  await screen.findByRole('textbox');
}

describe('AskTsolvePage', () => {
  it('AC-010.1 shows the question box, the send button and the note', async () => {
    await renderLinked();

    expect(screen.getByRole('button', { name: 'Gửi' })).toBeEnabled();
    expect(screen.getByText('Trả lời từ Solution và Wiki của T-Solve')).toBeInTheDocument();
  });

  it('AC-010.1 titles the project page "Ask T-Solve" through the manifest, once (BUG-182)', async () => {
    const manifest = fs.readFileSync(path.join(__dirname, '../../../manifest.yml'), 'utf8');
    await renderLinked();

    expect(manifest).toMatch(/jira:projectPage:\n\s+- key: tsolve-ask-page[\s\S]*?title: Ask T-Solve\n/);
    expect(screen.queryByRole('heading', { name: 'Ask T-Solve' })).not.toBeInTheDocument();
  });

  it('AC-010.2 shows MSG62 instead of the box and a Create button with nothing prefilled', async () => {
    getEligibility.mockResolvedValue({ linked: false });
    render(<AskTsolvePage />);

    expect(await screen.findByText(message('MSG62'))).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Chưa được, tạo ticket' }));

    expect(CreateIssueModal).toHaveBeenCalledWith({ context: { projectId: '10001' } });
    expect(open).toHaveBeenCalled();
  });

  it('AC-010.2 shows the box when the eligibility check fails', async () => {
    getEligibility.mockRejectedValue(failure('MSG06'));
    render(<AskTsolvePage />);

    expect(await screen.findByRole('textbox')).toBeInTheDocument();
  });

  it.each([
    ['   ', 'MSG01'],
    ['a'.repeat(1001), 'MSG02'],
  ])('AC-010.5 shows %s as %s under the box and sends nothing', async (text, code) => {
    await renderLinked();

    type(text);
    send();

    expect(screen.getByRole('alert')).toHaveTextContent(message(code));
    expect(askQuestion).not.toHaveBeenCalled();
  });

  it('AC-010.3 sends the question with the earlier turns as history', async () => {
    await renderLinked();
    await ask('VPN fails', ANSWERED);

    await ask('Still failing', NO_SOURCE);

    expect(askQuestion).toHaveBeenNthCalledWith(1, 'VPN fails', []);
    expect(askQuestion).toHaveBeenNthCalledWith(2, 'Still failing', [
      { question: 'VPN fails', answer: 'Update the VPN client [1] [2]' },
    ]);
  });

  it('AC-010.3 disables sending while waiting for the answer', async () => {
    await renderLinked();
    askQuestion.mockReturnValue(new Promise(() => {}));

    type('VPN fails');
    send();

    expect(screen.getByRole('button', { name: 'Gửi' })).toBeDisabled();
    expect(askQuestion).toHaveBeenCalledTimes(1);
  });

  it('AC-010.6 shows the answer, its sources like the web and both buttons', async () => {
    await renderLinked();

    await ask('VPN fails', ANSWERED);

    expect(screen.getByText('Update the VPN client [1] [2]')).toBeInTheDocument();
    expect(screen.getByText('[1] Solution from a ticket')).toBeInTheDocument();
    expect(screen.getByText('[2] VPN guide · Wiki document')).toBeInTheDocument();
    expect(screen.getByText('Symptoms: VPN drops')).toBeInTheDocument();
    expect(screen.getByText('Resolution steps: Update the client')).toBeInTheDocument();
    expect(screen.queryByText(/Workaround/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đã giải quyết' })).toBeInTheDocument();
    expect(box()).toHaveValue('');
  });

  it('AC-010.7 shows MSG109 and both buttons for NO_SOURCE', async () => {
    await renderLinked();

    await ask('VPN fails', NO_SOURCE);

    expect(screen.getByText(message('MSG109'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đã giải quyết' })).toBeInTheDocument();
  });

  it('AC-010.8 shows MSG06, keeps the question and still offers to create a ticket', async () => {
    await renderLinked();
    askQuestion.mockRejectedValueOnce(failure('MSG06'));

    type('VPN fails');
    send();

    expect(await screen.findByText(message('MSG06'))).toBeInTheDocument();
    expect(box()).toHaveValue('VPN fails');
    expect(screen.getByRole('button', { name: 'Chưa được, tạo ticket' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đã giải quyết' })).not.toBeInTheDocument();
  });

  it('AC-010.9 opens the Create dialog prefilled with the conversation', async () => {
    await renderLinked();
    await ask('VPN fails', ANSWERED);

    fireEvent.click(screen.getByRole('button', { name: 'Chưa được, tạo ticket' }));

    const { context } = CreateIssueModal.mock.calls[0][0];
    expect(context).toMatchObject({ projectId: '10001', summary: 'VPN fails', labels: ['tsolve-asked'] });
    expect(JSON.stringify(context.description)).toContain('Đã thử theo T-Solve:');
    expect(open).toHaveBeenCalled();
  });

  it('AC-010.10 ends the conversation without creating an issue', async () => {
    await renderLinked();
    await ask('VPN fails', ANSWERED);

    fireEvent.click(screen.getByRole('button', { name: 'Đã giải quyết' }));

    expect(screen.getByText('Vấn đề đã được giải quyết. Cảm ơn bạn!')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Chưa được, tạo ticket' })).not.toBeInTheDocument();
    expect(CreateIssueModal).not.toHaveBeenCalled();
  });

  it('AC-010.11 switches to MSG62 when the server says the user is not linked', async () => {
    await renderLinked();
    askQuestion.mockRejectedValueOnce(failure('MSG62'));

    type('VPN fails');
    send();

    expect(await screen.findByText(message('MSG62'))).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('AC-010.5 shows a server MSG02 under the box', async () => {
    await renderLinked();
    askQuestion.mockRejectedValueOnce(failure('MSG02'));

    type('VPN fails');
    send();

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(message('MSG02')));
    expect(box()).toHaveValue('VPN fails');
  });
});
