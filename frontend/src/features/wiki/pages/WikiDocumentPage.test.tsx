import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { WikiDocumentDetail } from '@/shared/api';
import { getGetWikiDocumentMockHandler } from '@/shared/api/generated/wiki/wiki.msw';
import { problem } from '@/shared/api/mocks';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const ID = '00000000-0000-4000-8000-000000009001';
const document: WikiDocumentDetail = {
  documentId: ID,
  title: 'VPN guide',
  spaceId: '00000000-0000-4000-8000-0000000000a1',
  spaceName: 'IT Space',
  folderId: null,
  folderName: null,
  visibility: 'COMPANY_WIDE',
  owner: 'Dang Nhat Minh',
  updatedAt: '2026-11-13T08:00:00Z',
  content: '<script>alert("test")</script>\nVPN instructions\n  Keep indentation.',
  fileName: 'vpn.pdf',
  fileSize: 1024,
  canDelete: false,
};

describe('WikiDocumentPage', () => {
  it('AC-005.13 asks MSG100, cancels without deleting, then shows MSG101 and returns home (BUG-281)', async () => {
    server.use(getGetWikiDocumentMockHandler({ ...document, canDelete: true }));
    const remove = vi.fn();
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.delete('*/api/v1/wiki/documents/:documentId', async ({ params }) => {
        remove(params.documentId);
        await pending;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const view = renderApp(paths.wikiDocument(ID), { role: 'KNOWLEDGE_MANAGER' });
    await user.click(await screen.findByRole('button', { name: 'Delete' }, { timeout: 5000 }));
    const confirm = await screen.findByRole('dialog');
    expect(confirm).toHaveTextContent(msg('MSG100', { title: document.title }));
    expect(remove).not.toHaveBeenCalled();
    await user.click(within(confirm).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(remove).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    await user.dblClick(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }),
    );
    await waitFor(() => expect(remove).toHaveBeenCalledExactlyOnceWith(ID));
    expect(view.router.state.location.pathname).toBe(paths.wikiDocument(ID));
    release();
    expect(await screen.findByText(msg('MSG101'))).toBeInTheDocument();
    await waitFor(() => expect(view.router.state.location.pathname).toBe(paths.wikiHome));
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.13 keeps the document and confirmation after a failed delete and allows retry', async () => {
    server.use(getGetWikiDocumentMockHandler({ ...document, canDelete: true }));
    server.use(http.delete('*/api/v1/wiki/documents/:documentId', () => problem(500)));
    const user = userEvent.setup();
    const view = renderApp(paths.wikiDocument(ID), { role: 'KNOWLEDGE_MANAGER' });
    await user.click(await screen.findByRole('button', { name: 'Delete' }, { timeout: 5000 }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(view.router.state.location.pathname).toBe(paths.wikiDocument(ID));
    expect(screen.queryByText(msg('MSG101'))).not.toBeInTheDocument();
    server.use(
      http.delete(
        '*/api/v1/wiki/documents/:documentId',
        () => new HttpResponse(null, { status: 204 }),
      ),
    );
    const retry = within(screen.getByRole('dialog')).getByRole('button', { name: 'OK' });
    await waitFor(() => expect(retry).toBeEnabled());
    await user.click(retry);
    expect(await screen.findByText(msg('MSG101'))).toBeInTheDocument();
    await waitFor(() => expect(view.router.state.location.pathname).toBe(paths.wikiHome));
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-005.13 hides Delete when canDelete is false even for a Knowledge Manager', async () => {
    server.use(getGetWikiDocumentMockHandler(document));
    renderApp(paths.wikiDocument(ID), { role: 'KNOWLEDGE_MANAGER' });
    await screen.findByRole('heading', { name: document.title }, { timeout: 5000 });
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  });

  it('AC-002.19 shows the complete header, literal plain text and original-file Download (BUG-255)', async () => {
    server.use(getGetWikiDocumentMockHandler(document));
    const view = renderApp(paths.wikiDocument(ID), { role: 'EMPLOYEE' });
    expect(
      await screen.findByRole('heading', { name: 'VPN guide' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    for (const value of [
      'IT Space',
      'Company-wide',
      'Dang Nhat Minh',
      formatDateTime(document.updatedAt),
    ]) {
      expect(screen.getByText(value)).toBeInTheDocument();
    }
    expect(screen.getByText('Folder')).toBeInTheDocument();
    const content = screen.getByRole('region', { name: 'Content' });
    expect(content.textContent).toBe(document.content);
    expect(content.querySelector('script')).toBeNull();
    expect(screen.getByRole('link', { name: 'Download' })).toHaveAttribute(
      'href',
      `/api/v1/wiki/documents/${ID}/file`,
    );
    expect(screen.getByRole('link', { name: 'Download' })).toHaveAttribute('download', 'vpn.pdf');
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
  });

  it('AC-002.19 shows Department visibility and a named folder', async () => {
    server.use(
      getGetWikiDocumentMockHandler({
        ...document,
        folderName: 'Guides',
        visibility: 'DEPARTMENT',
      }),
    );
    renderApp(paths.wikiDocument(ID), { role: 'EMPLOYEE' });
    expect(await screen.findByText('Guides', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText('Department')).toBeInTheDocument();
  });

  it('AC-002.20 replaces an unavailable document with MSG110 and no Download', async () => {
    server.use(http.get('*/api/v1/wiki/documents/:documentId', () => problem(404, 'MSG110')));
    renderApp(paths.wikiDocument(ID), { role: 'EMPLOYEE' });
    expect(await screen.findByText(msg('MSG110'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Download' })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Content' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('AC-002.20 shows MSG110 for an invalid document id without requesting it (BUG-256)', async () => {
    const requests = vi.fn();
    server.use(
      http.get('*/api/v1/wiki/documents/:documentId', () => {
        requests();
        return problem(400);
      }),
    );
    const { queryClient } = renderApp(paths.wikiDocument('invalid-id'), { role: 'EMPLOYEE' });
    expect(await screen.findByText(msg('MSG110'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Download' })).not.toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(requests).not.toHaveBeenCalled();
  });

  it('AC-002.19 shows loading while pending and retries a failed document request', async () => {
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/wiki/documents/:documentId', async () => {
        await pending;
        return problem(500);
      }),
    );
    const user = userEvent.setup();
    renderApp(paths.wikiDocument(ID), { role: 'EMPLOYEE' });
    expect(await screen.findByText('Loading ...', {}, { timeout: 5000 })).toBeInTheDocument();
    release();
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(getGetWikiDocumentMockHandler(document));
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'VPN guide' })).toBeInTheDocument();
  });
});
