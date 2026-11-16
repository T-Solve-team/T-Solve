import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import {
  deleteWikiDocumentPermanently,
  getWikiDocument,
  listWikiTrash,
  restoreWikiDocument,
  trashWikiDocument,
  type WikiTrashItem,
} from '@/shared/api';
import { getListWikiTrashMockHandler } from '@/shared/api/generated/wiki/wiki.msw';
import { problem } from '@/shared/api/mocks';
import { DEV_USER_FOR_ROLE, setDevUser } from '@/shared/auth';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const SPACE = '00000000-0000-4000-8000-0000000000a1';
const ID = '00000000-0000-4000-8000-000000009001';
const item: WikiTrashItem = {
  documentId: ID,
  title: 'VPN connection guide',
  folderName: 'VPN',
  owner: 'Dang Nhat Minh',
  deletedBy: 'Lê Thu Hương',
  deletedAt: '2026-11-15T08:00:00Z',
};
const trashPath = `${paths.spaceSettings(SPACE)}?tab=trash`;

function mockTrash() {
  let rows = [item];
  const list = vi.fn(() => rows);
  server.use(getListWikiTrashMockHandler(list));
  return {
    list,
    clear: () => {
      rows = [];
    },
  };
}

describe('TrashTable in Space Settings', () => {
  it('AC-005.14 mocks conceal trashed documents and reject trash actions by viewers (BUG-283)', async () => {
    const view = renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    await trashWikiDocument(ID);
    setDevUser(DEV_USER_FOR_ROLE.EMPLOYEE);
    await expect(getWikiDocument(ID)).rejects.toMatchObject({ status: 404, code: 'MSG110' });
    await expect(restoreWikiDocument(ID)).rejects.toMatchObject({ status: 404, code: 'MSG110' });
    await expect(deleteWikiDocumentPermanently(ID)).rejects.toMatchObject({
      status: 404,
      code: 'MSG110',
    });
    await expect(listWikiTrash(SPACE)).rejects.toMatchObject({ status: 403, code: 'MSG08' });
  });

  it('AC-005.14 AC-005.15 uses stateful mocks to hide trashed documents, read them and restore their cached views', async () => {
    const user = userEvent.setup();
    const view = renderApp(paths.wikiDocument(ID), { role: 'KNOWLEDGE_MANAGER' });
    await user.click(await screen.findByRole('button', { name: 'Delete' }, { timeout: 5000 }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    await waitFor(() => expect(view.router.state.location.pathname).toBe(paths.wikiHome));
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    expect(screen.queryByRole('link', { name: item.title })).not.toBeInTheDocument();
    await act(() => view.router.navigate(trashPath));
    await user.click(await screen.findByRole('link', { name: item.title }));
    await screen.findByRole('heading', { name: item.title });
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    await act(() => view.router.navigate(trashPath));
    await user.click(await screen.findByRole('button', { name: 'Restore' }));
    expect(await screen.findByText(msg('MSG102'))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: item.title })).not.toBeInTheDocument(),
    );
    await act(() => view.router.navigate(paths.wikiDocument(ID)));
    expect(await screen.findByRole('button', { name: 'Delete' })).toBeInTheDocument();
    await act(() => view.router.navigate(paths.wikiHome));
    expect(await screen.findByRole('link', { name: item.title })).toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.16 uses stateful mocks to make a permanently deleted document unavailable', async () => {
    const user = userEvent.setup();
    const view = renderApp(paths.wikiDocument(ID), { role: 'KNOWLEDGE_MANAGER' });
    await user.click(await screen.findByRole('button', { name: 'Delete' }, { timeout: 5000 }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    await waitFor(() => expect(view.router.state.location.pathname).toBe(paths.wikiHome));
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    await act(() => view.router.navigate(trashPath));
    await user.click(await screen.findByRole('button', { name: 'Delete permanently' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await act(() => view.router.navigate(paths.wikiDocument(ID)));
    expect(await screen.findByText(msg('MSG110'))).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.14 opens the Trash tab from the URL and lists the required columns and actions', async () => {
    const mock = mockTrash();
    const user = userEvent.setup();
    const view = renderApp(paths.spaceSettings(SPACE), { role: 'KNOWLEDGE_MANAGER' });
    const tab = await screen.findByRole('tab', { name: 'Trash' }, { timeout: 5000 });
    expect(screen.getByRole('tab', { name: 'Permissions' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(mock.list).not.toHaveBeenCalled();
    await user.click(tab);
    const row = await screen.findByRole('row', { name: /VPN connection guide/ });
    for (const label of ['Title', 'Folder', 'Owner', 'Deleted by', 'Deleted on']) {
      expect(screen.getByRole('columnheader', { name: label })).toBeInTheDocument();
    }
    for (const value of [
      item.folderName ?? '',
      item.owner,
      item.deletedBy,
      formatDateTime(item.deletedAt),
    ]) {
      expect(within(row).getByText(value)).toBeInTheDocument();
    }
    expect(within(row).getByRole('link', { name: item.title })).toHaveAttribute(
      'href',
      paths.wikiDocument(ID),
    );
    expect(within(row).getByRole('button', { name: 'Restore' })).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Delete permanently' })).toBeInTheDocument();
    expect(view.router.state.location.search).toBe('?tab=trash');
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.14 shows MSG04 for empty trash after loading', async () => {
    server.use(getListWikiTrashMockHandler([]));
    const view = renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    expect(screen.queryByRole('button', { name: 'Restore' })).not.toBeInTheDocument();
  });

  it('AC-005.14 handles loading, a failed list and retry with a top-level folder', async () => {
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/wiki/spaces/:spaceId/trash', async () => {
        await pending;
        return problem(500);
      }),
    );
    const user = userEvent.setup();
    const view = renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
    expect(await screen.findByText('Loading ...', {}, { timeout: 5000 })).toBeInTheDocument();
    release();
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(getListWikiTrashMockHandler([{ ...item, folderName: null }]));
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    const row = await screen.findByRole('row', { name: /VPN connection guide/ });
    expect(within(row).getByText('—')).toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.14 never requests another Space trash for a Knowledge Manager who does not manage it', async () => {
    const mock = mockTrash();
    const view = renderApp(
      `${paths.spaceSettings('00000000-0000-4000-8000-0000000000a2')}?tab=trash`,
      { role: 'KNOWLEDGE_MANAGER' },
    );
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    expect(mock.list).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Restore' })).not.toBeInTheDocument();
  });

  it.each([false, true])(
    'AC-005.15 restores and refreshes trash, showing MSG84 as information only when renamed=%s (BUG-282)',
    async (renamed) => {
      const mock = mockTrash();
      const restore = vi.fn();
      let release = () => {};
      const pending = new Promise<void>((resolve) => {
        release = resolve;
      });
      server.use(
        http.post('*/api/v1/wiki/documents/:documentId/restore', async ({ params }) => {
          restore(params.documentId);
          await pending;
          mock.clear();
          return HttpResponse.json({
            title: renamed ? `${item.title} (restored)` : item.title,
            renamed,
          });
        }),
      );
      const user = userEvent.setup();
      const view = renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
      await user.dblClick(
        await screen.findByRole('button', { name: 'Restore' }, { timeout: 5000 }),
      );
      await waitFor(() => expect(restore).toHaveBeenCalledExactlyOnceWith(ID));
      expect(screen.getByRole('button', { name: /Restore/ })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Delete permanently' })).toBeDisabled();
      release();
      expect(await screen.findByText(msg('MSG102'))).toBeInTheDocument();
      await waitFor(() =>
        expect(screen.queryByRole('link', { name: item.title })).not.toBeInTheDocument(),
      );
      expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
      if (renamed) {
        const information = screen
          .getAllByRole('alert')
          .find((alert) => alert.textContent?.includes(msg('MSG84')));
        if (!information) throw new Error('Missing restore rename information');
        expect(information).toHaveTextContent(msg('MSG84'));
        expect(within(information).getByRole('img', { name: 'info-circle' })).toBeInTheDocument();
      } else expect(screen.queryByText(msg('MSG84'))).not.toBeInTheDocument();
      expect(mock.list.mock.calls.length).toBeGreaterThan(1);
      await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    },
  );

  it('AC-005.15 hides the MSG84 notice once Delete permanently is chosen (BUG-305)', async () => {
    mockTrash();
    server.use(
      http.post('*/api/v1/wiki/documents/:documentId/restore', () =>
        HttpResponse.json({ title: `${item.title} (restored)`, renamed: true }),
      ),
    );
    const user = userEvent.setup();
    renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
    await user.click(await screen.findByRole('button', { name: 'Restore' }, { timeout: 5000 }));
    expect(await screen.findByText(msg('MSG84'))).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Delete permanently' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG84'))).not.toBeInTheDocument();
  });

  it('AC-005.15 keeps the row after restore conflict and allows retry without success notices', async () => {
    const mock = mockTrash();
    server.use(
      http.post('*/api/v1/wiki/documents/:documentId/restore', () => problem(409, 'MSG84')),
    );
    const user = userEvent.setup();
    const view = renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
    await user.click(await screen.findByRole('button', { name: 'Restore' }, { timeout: 5000 }));
    expect(await screen.findByText(msg('MSG84'))).toBeInTheDocument();
    expect(screen.getByRole('link', { name: item.title })).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG102'))).not.toBeInTheDocument();
    server.use(
      http.post('*/api/v1/wiki/documents/:documentId/restore', () => {
        mock.clear();
        return HttpResponse.json({ title: item.title, renamed: false });
      }),
    );
    const retry = screen.getByRole('button', { name: 'Restore' });
    await waitFor(() => expect(retry).toBeEnabled());
    await user.click(retry);
    expect(await screen.findByText(msg('MSG102'))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: item.title })).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.16 asks MSG103, cancels without a request, then deletes once and refreshes with MSG05 (BUG-281)', async () => {
    const mock = mockTrash();
    const remove = vi.fn();
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.delete('*/api/v1/wiki/documents/:documentId/permanent', async ({ params }) => {
        remove(params.documentId);
        await pending;
        mock.clear();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const view = renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
    await user.click(
      await screen.findByRole('button', { name: 'Delete permanently' }, { timeout: 5000 }),
    );
    let confirm = await screen.findByRole('dialog');
    expect(confirm).toHaveTextContent(msg('MSG103', { title: item.title }));
    expect(remove).not.toHaveBeenCalled();
    await user.click(within(confirm).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(remove).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete permanently' }));
    confirm = await screen.findByRole('dialog');
    await user.dblClick(within(confirm).getByRole('button', { name: 'OK' }));
    await waitFor(() => expect(remove).toHaveBeenCalledExactlyOnceWith(ID));
    expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
    release();
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('link', { name: item.title })).not.toBeInTheDocument();
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
    expect(mock.list.mock.calls.length).toBeGreaterThan(1);
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.16 retains confirmation and the row after failure, then permits retry', async () => {
    const mock = mockTrash();
    server.use(http.delete('*/api/v1/wiki/documents/:documentId/permanent', () => problem(500)));
    const user = userEvent.setup();
    const view = renderApp(trashPath, { role: 'KNOWLEDGE_MANAGER' });
    await user.click(
      await screen.findByRole('button', { name: 'Delete permanently' }, { timeout: 5000 }),
    );
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByRole('link', { name: item.title })).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG05'))).not.toBeInTheDocument();
    server.use(
      http.delete('*/api/v1/wiki/documents/:documentId/permanent', () => {
        mock.clear();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const retry = within(screen.getByRole('dialog')).getByRole('button', { name: 'OK' });
    await waitFor(() => expect(retry).toBeEnabled());
    await user.click(retry);
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByRole('link', { name: item.title })).not.toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });
});
