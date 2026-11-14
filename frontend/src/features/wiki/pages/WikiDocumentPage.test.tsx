import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
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
  it('AC-002.19 shows the complete header, literal plain text and original-file Download (BUG-220)', async () => {
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

  it('AC-002.20 shows MSG110 for an invalid document id without requesting it (BUG-221)', async () => {
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
