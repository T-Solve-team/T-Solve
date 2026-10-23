import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { renderWithProviders } from '@/test/render';
import { msg } from '@/shared/messages';
import { RequestChangesModal } from './RequestChangesModal';

describe('RequestChangesModal', () => {
  it('AC-004.3 keeps the popup open for blank or oversized comments', async () => {
    const user = userEvent.setup();
    const onSent = vi.fn();
    renderWithProviders(
      <RequestChangesModal ticketIds={[7, 8]} isOpen onClose={vi.fn()} onSent={onSent} />,
    );
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Comment' })),
    ).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText('Comment'), {
      target: { value: 'x'.repeat(1001) },
    });
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    expect(
      await within(dialog).findByText(msg('MSG02', { field_name: 'Comment', max_length: 1000 })),
    ).toBeInTheDocument();
    expect(onSent).not.toHaveBeenCalled();
  });
  it('AC-004.1 submits every selected ticketId with one comment (BUG-019)', async () => {
    let submitted: unknown;
    server.use(
      http.post('*/api/v1/reviews/request-changes', async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json({ count: 2 });
      }),
    );
    const onSent = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <RequestChangesModal ticketIds={[7, 8]} isOpen onClose={vi.fn()} onSent={onSent} />,
    );
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Comment'), 'Add the exact error message.');
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    await vi.waitFor(() => expect(onSent).toHaveBeenCalledOnce());
    expect(submitted).toEqual({ ticketIds: [7, 8], comment: 'Add the exact error message.' });
  });
});
