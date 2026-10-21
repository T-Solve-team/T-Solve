import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { renderWithProviders } from '@/test/render';
import { msg } from '@/shared/messages';
import { RequestChangesModal } from './RequestChangesModal';

describe('RequestChangesModal', () => {
  it('keeps the popup open for blank or oversized comments (AC-004.3)', async () => {
    const user = userEvent.setup();
    const onSent = vi.fn();
    renderWithProviders(
      <RequestChangesModal ticketId={7} isOpen onClose={vi.fn()} onSent={onSent} />,
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
  it('submits one ticketId with the comment (BUG-013, AC-003.1)', async () => {
    let submitted: unknown;
    server.use(
      http.post('*/api/v1/reviews/request-changes', async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json({ count: 1 });
      }),
    );
    const onSent = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <RequestChangesModal ticketId={7} isOpen onClose={vi.fn()} onSent={onSent} />,
    );
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Comment'), 'Add the exact error message.');
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    await vi.waitFor(() => expect(onSent).toHaveBeenCalledOnce());
    expect(submitted).toEqual({ ticketId: 7, comment: 'Add the exact error message.' });
  });
});
