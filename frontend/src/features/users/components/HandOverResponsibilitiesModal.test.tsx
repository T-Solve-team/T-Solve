import { act, cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { getGetUserHandoverOptionsQueryKey } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp, renderWithProviders } from '@/test/render';
import { HandOverResponsibilitiesModal } from './HandOverResponsibilitiesModal';

describe('Hand Over Responsibilities recipient refresh', () => {
  it('shows only the MSG07 dialog when handover answers 401 (BUG-124)', async () => {
    server.use(http.put('*/api/v1/users/:userId/status', () => problem(401)));
    const user = userEvent.setup();
    const { queryClient } = renderApp('/users/10000000-0000-4000-8000-000000000005', {
      role: 'ADMIN',
    });
    await user.click(await screen.findByRole('button', { name: 'Deactivate' }, { timeout: 5000 }));
    const handover = await screen.findByRole('dialog', { name: 'Hand Over Responsibilities' });
    for (const name of ['IT Support', 'IT Network']) {
      await user.click(await within(handover).findByRole('combobox', { name }));
      await user.click(
        await screen.findByRole('option', { name: /Bùi Anh Tuấn/, selected: false }),
      );
    }
    await user.click(within(handover).getByRole('button', { name: 'Deactivate' }));
    await screen.findAllByText(msg('MSG07'));
    try {
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
      expect(
        screen.getAllByRole('dialog').filter((el) => el.textContent?.includes(msg('MSG07'))),
      ).toHaveLength(1);
      expect(
        screen.getAllByText(msg('MSG07')).filter((el) => !el.closest('[role="dialog"]')),
      ).toHaveLength(0);
    } finally {
      const expired = screen
        .queryAllByRole('dialog')
        .find((el) => el.textContent?.includes(msg('MSG07')));
      if (expired) await user.click(within(expired).getByRole('button', { name: 'OK' }));
      cleanup();
      await queryClient.cancelQueries();
      queryClient.clear();
    }
  });

  it.each([
    { account: 5, names: ['IT Support', 'IT Network'], recipient: /Bùi Anh Tuấn/ },
    { account: 3, names: ['IT Knowledge'], recipient: /Võ Thanh Tâm/ },
  ])(
    'AC-008.8 / AC-009.8 blocks cached choices for account $account while refreshing and after a failed refresh (BUG-067)',
    async ({ account, names, recipient }) => {
      const userId = `10000000-0000-4000-8000-${String(account).padStart(12, '0')}`;
      const onSubmit = vi.fn();
      const user = userEvent.setup();
      const { queryClient } = renderWithProviders(
        <HandOverResponsibilitiesModal
          userId={userId}
          userName="Ngô Thị Mai"
          isSubmitting={false}
          onCancel={vi.fn()}
          onSubmit={onSubmit}
        />,
        { role: 'ADMIN' },
      );
      const dialog = await screen.findByRole('dialog', { name: 'Hand Over Responsibilities' });
      for (const name of names) {
        await user.click(await within(dialog).findByRole('combobox', { name }));
        await user.click(await screen.findByRole('option', { name: recipient, selected: false }));
      }
      const confirm = within(dialog).getByRole('button', { name: 'Deactivate' });
      expect(confirm).toBeEnabled();

      let release: (() => void) | undefined;
      const response = new Promise<void>((resolve) => {
        release = resolve;
      });
      server.use(
        http.get('*/api/v1/users/:userId/handover-options', async () => {
          await response;
          return problem(500);
        }),
      );
      let refresh: Promise<void> | undefined;
      try {
        act(() => {
          refresh = queryClient.invalidateQueries({
            queryKey: getGetUserHandoverOptionsQueryKey(userId),
          });
        });
        await waitFor(() => expect(queryClient.isFetching()).toBeGreaterThan(0));
        expect(confirm).toBeDisabled();
        await user.click(confirm);
        expect(onSubmit).not.toHaveBeenCalled();
        await act(async () => {
          release?.();
          await refresh;
        });
        expect(await within(dialog).findByText(msg('MSG06'))).toBeInTheDocument();
        expect(confirm).toBeDisabled();
        await user.click(confirm);
        expect(onSubmit).not.toHaveBeenCalled();

        server.resetHandlers();
        await user.click(within(dialog).getByRole('button', { name: 'Try again' }));
        await waitFor(() => expect(confirm).toBeEnabled());
        expect(within(dialog).getAllByText(recipient)).toHaveLength(names.length);
        await user.click(confirm);
        await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
        expect(onSubmit).toHaveBeenCalledWith(
          {
            projectHandovers:
              account === 5
                ? [
                    {
                      projectId: '00000000-0000-4000-8000-000000000101',
                      recipientId: '10000000-0000-4000-8000-000000000009',
                    },
                    {
                      projectId: '00000000-0000-4000-8000-000000000102',
                      recipientId: '10000000-0000-4000-8000-000000000009',
                    },
                  ]
                : [],
            spaceHandovers:
              account === 3
                ? [
                    {
                      spaceId: '00000000-0000-4000-8000-0000000000a1',
                      recipientId: '10000000-0000-4000-8000-000000000007',
                    },
                  ]
                : [],
          },
          expect.any(Function),
        );
      } finally {
        release?.();
        await refresh;
        cleanup();
        // antd useDelayState leaves a 10 ms timer after unmount; let it fire before jsdom goes away
        await new Promise((r) => setTimeout(r, 20));
        await queryClient.cancelQueries();
        queryClient.clear();
      }
    },
  );
});
