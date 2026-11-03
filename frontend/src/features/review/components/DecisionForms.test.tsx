import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { ticketUuid } from '../mocks/data';
import { reviewStore } from '../mocks/state';
import { RejectTicketModal } from './RejectTicketModal';
import { RequestChangesModal } from './RequestChangesModal';

describe.each(['reject', 'request changes'] as const)('%s form', (decision) => {
  it('AC-003.2 / AC-004.2 validates whitespace and raw 1001-character input, then accepts 1000 unchanged', async () => {
    const complete = vi.fn();
    const user = userEvent.setup();
    const ids = [ticketUuid(1), ticketUuid(2)];
    const { queryClient } = renderWithProviders(
      decision === 'reject' ? (
        <RejectTicketModal isOpen ticketIds={ids} onClose={vi.fn()} onRejected={complete} />
      ) : (
        <RequestChangesModal isOpen ticketIds={ids} onClose={vi.fn()} onSent={complete} />
      ),
      { role: 'PROJECT_MANAGER' },
    );
    try {
      const dialog = await screen.findByRole('dialog');
      const fieldName = decision === 'reject' ? 'Reason' : 'Comment';
      const input = within(dialog).getByLabelText(fieldName);
      const submit = within(dialog).getByRole('button', {
        name: decision === 'reject' ? 'Reject' : 'Send',
      });
      for (const [text, code] of [
        ['   ', 'MSG01'],
        ['x'.repeat(1001), 'MSG02'],
      ] as const) {
        await user.clear(input);
        await user.click(input);
        await user.paste(text);
        await user.click(submit);
        expect(
          await within(dialog).findByText(msg(code, { field_name: fieldName, max_length: 1000 })),
        ).toBeInTheDocument();
        expect(complete).not.toHaveBeenCalled();
        expect(reviewStore.history).toEqual([]);
        expect(input).toHaveValue(text);
      }
      const text = ` ${'x'.repeat(998)} `;
      await user.clear(input);
      await user.click(input);
      await user.paste(text);
      await user.click(submit);
      await waitFor(() => expect(complete).toHaveBeenCalledTimes(1));
      expect(reviewStore.history).toHaveLength(2);
      for (const entry of reviewStore.history)
        expect(decision === 'reject' ? entry.reason : entry.comment).toBe(text);
    } finally {
      cleanup();
      await queryClient.cancelQueries();
      queryClient.clear();
    }
  });

  it('AC-003.1 / AC-004.1 cancels without changing tickets or decision history', async () => {
    const cancel = vi.fn();
    const user = userEvent.setup();
    const before = structuredClone(reviewStore);
    const { queryClient } = renderWithProviders(
      decision === 'reject' ? (
        <RejectTicketModal
          isOpen
          ticketIds={[ticketUuid(1)]}
          onClose={cancel}
          onRejected={vi.fn()}
        />
      ) : (
        <RequestChangesModal isOpen ticketIds={[ticketUuid(1)]} onClose={cancel} onSent={vi.fn()} />
      ),
      { role: 'PROJECT_MANAGER' },
    );
    try {
      await user.click(
        within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
      );
      expect(cancel).toHaveBeenCalledTimes(1);
      expect(reviewStore).toEqual(before);
    } finally {
      cleanup();
      await queryClient.cancelQueries();
      queryClient.clear();
    }
  });
});
