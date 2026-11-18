import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import type { WikiDocumentSummary, WikiSpace } from '@/shared/api';
import {
  getListCompanyWideDocumentsMockHandler,
  getListWikiDocumentsMockHandler,
  getListWikiFoldersMockHandler,
  getListWikiSpacesMockHandler,
  getDeleteWikiFolderMockHandler,
  getSearchWikiDocumentsMockHandler,
} from '@/shared/api/generated/wiki/wiki.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

const IT = '00000000-0000-4000-8000-0000000000a1';
const HR = '00000000-0000-4000-8000-0000000000a2';
const FOLDER = '00000000-0000-4000-8000-0000000000f1';
const CHILD = '00000000-0000-4000-8000-0000000000f2';
const spaces: WikiSpace[] = [
  { spaceId: HR, name: 'HR Space', departmentName: 'HR', accessLevel: 'VIEW' },
  { spaceId: IT, name: 'IT Space', departmentName: 'IT', accessLevel: 'KNOWLEDGE_MANAGER' },
];
const document: WikiDocumentSummary = {
  documentId: '00000000-0000-4000-8000-000000009001',
  title: 'VPN guide',
  spaceId: IT,
  spaceName: 'IT Space',
  folderId: FOLDER,
  folderName: 'Guides',
  visibility: 'DEPARTMENT',
  owner: 'Dang Nhat Minh',
  updatedAt: '2026-11-13T08:00:00Z',
};

beforeEach(() => {
  server.use(
    getListWikiSpacesMockHandler(spaces),
    getListCompanyWideDocumentsMockHandler([
      { ...document, visibility: 'COMPANY_WIDE' },
      {
        ...document,
        documentId: HR,
        title: 'Employee handbook',
        spaceId: HR,
        spaceName: 'HR Space',
        visibility: 'COMPANY_WIDE',
      },
    ]),
    getListWikiFoldersMockHandler([
      { folderId: FOLDER, parentId: null, name: 'Guides', level: 1 },
      { folderId: CHILD, parentId: FOLDER, name: 'Network', level: 2 },
    ]),
    getListWikiDocumentsMockHandler(({ request }) =>
      new URL(request.url).searchParams.get('folderId') === FOLDER ? [document] : [],
    ),
    getSearchWikiDocumentsMockHandler([document]),
  );
});

async function openHome(query = `?space=${IT}&folder=${FOLDER}`) {
  const view = renderApp(`${paths.wikiHome}${query}`, { role: 'KNOWLEDGE_MANAGER' });
  const box = await screen.findByRole('searchbox', { name: 'Search' }, { timeout: 5000 });
  await waitFor(() => {
    expect(screen.queryAllByText('Loading ...')).toHaveLength(0);
    expect(view.queryClient.isFetching()).toBe(0);
  });
  return { ...view, box, user: userEvent.setup() };
}

describe('WikiHomePage', () => {
  it('AC-002.14 groups Company-wide documents before Spaces and renders access levels, folders and columns (BUG-254, BUG-255)', async () => {
    const { box } = await openHome();
    const company = screen.getByRole('region', { name: 'Company-wide' });
    expect(
      within(company)
        .getAllByRole('heading')
        .map((h) => h.textContent),
    ).toEqual(['Company-wide', 'HR Space', 'IT Space']);
    const spaceList = screen.getByRole('region', { name: 'Spaces' });
    expect(
      company.compareDocumentPosition(spaceList) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(spaceList.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(spaceList).getByText('View')).toBeInTheDocument();
    expect(within(spaceList).getByText('Knowledge Manager')).toBeInTheDocument();
    expect(await screen.findByRole('tree', { name: 'Folders' })).toHaveTextContent('Network');
    const table = screen.getByRole('table', { name: 'Documents' });
    expect(box.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((h) => h.textContent),
    ).toEqual(['Title', 'Folder', 'Visibility', 'Owner', 'Updated']);
    expect(await within(table).findByRole('link', { name: 'VPN guide' })).toHaveAttribute(
      'href',
      paths.wikiDocument(document.documentId),
    );
    expect(table).toHaveTextContent('Department');
    expect(table).toHaveTextContent('Dang Nhat Minh');
    expect(table).toHaveTextContent('13/11/2026 15:00');
  });

  it('AC-002.15 shows only Company-wide and Search when no Space is viewable, even with a stale URL', async () => {
    server.use(getListWikiSpacesMockHandler([]));
    const requests = vi.fn();
    server.events.on('request:start', ({ request }) => {
      if (/\/spaces\/[^/]+\/(folders|documents)/.test(request.url)) requests();
    });
    await openHome();
    expect(screen.getByRole('region', { name: 'Company-wide' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Spaces' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Documents' })).not.toBeInTheDocument();
    expect(requests).not.toHaveBeenCalled();
  });

  it('AC-002.16 shows MSG04 for empty Company-wide, folder documents and search results', async () => {
    server.use(getListCompanyWideDocumentsMockHandler([]), getSearchWikiDocumentsMockHandler([]));
    const { user, box, queryClient } = await openHome(`?space=${IT}`);
    expect(
      within(screen.getByRole('region', { name: 'Company-wide' })).getByText(msg('MSG04')),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('table', { name: 'Documents' })).getByText(msg('MSG04')),
    ).toBeInTheDocument();
    await user.click(box);
    await user.paste('no match');
    await user.keyboard('{Enter}');
    expect(
      await within(screen.getByRole('region', { name: 'Search results' })).findByText(msg('MSG04')),
    ).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.17 stores Space and folder in the URL, clears the old folder on Space change and supports Back', async () => {
    const { user, router, queryClient } = await openHome('');
    await user.click(screen.getByRole('link', { name: 'IT Space' }));
    await user.click(
      await within(screen.getByRole('tree', { name: 'Folders' })).findByText('Guides'),
    );
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('folder')).toBe(FOLDER),
    );
    const table = await screen.findByRole('table', { name: 'Documents' });
    expect(await within(table).findByRole('link', { name: 'VPN guide' })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'HR Space' }));
    expect(new URLSearchParams(router.state.location.search).get('space')).toBe(HR);
    expect(new URLSearchParams(router.state.location.search).has('folder')).toBe(false);
    await act(() => router.navigate(-1));
    expect(new URLSearchParams(router.state.location.search).get('space')).toBe(IT);
    expect(new URLSearchParams(router.state.location.search).get('folder')).toBe(FOLDER);
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(screen.getByRole('tree', { name: 'Folders' })).toHaveTextContent('Guides');
  });

  it('AC-002.17 clears a foreign or missing folder on 404 and shows the selected Space root (BUG-259)', async () => {
    const requestedFolders: (string | null)[] = [];
    server.use(
      http.get('*/api/v1/wiki/spaces/:spaceId/documents', ({ request }) => {
        const folder = new URL(request.url).searchParams.get('folderId');
        requestedFolders.push(folder);
        if (folder) return problem(404, 'MSG04');
      }),
      getListWikiDocumentsMockHandler([
        { ...document, title: 'Root guide', folderId: null, folderName: null },
      ]),
    );
    const { router, queryClient } = await openHome(`?space=${IT}&folder=${HR}&q=VPN`);
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).has('folder')).toBe(false),
    );
    const params = new URLSearchParams(router.state.location.search);
    expect(params.get('space')).toBe(IT);
    expect(params.get('q')).toBe('VPN');
    const table = await screen.findByRole('table', { name: 'Documents' });
    expect(await within(table).findByRole('link', { name: 'Root guide' })).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(requestedFolders).toEqual([HR, null]);
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    await act(() => router.navigate(`${paths.wikiHome}?space=${IT}&q=previous`));
    await act(() => router.navigate(`${paths.wikiHome}?space=${IT}&folder=${HR}&q=VPN`));
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).has('folder')).toBe(false),
    );
    await act(() => router.navigate(-1));
    expect(new URLSearchParams(router.state.location.search).get('q')).toBe('previous');
    expect(new URLSearchParams(router.state.location.search).has('folder')).toBe(false);
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.18 blocks overlong and blank searches, validates below Search, and sends valid text unchanged', async () => {
    const queries: string[] = [];
    server.use(
      getSearchWikiDocumentsMockHandler(({ request }) => {
        queries.push(new URL(request.url).searchParams.get('q') ?? '');
        return [document];
      }),
    );
    const { user, box, queryClient } = await openHome();
    await user.click(box);
    await user.paste('x'.repeat(201));
    await user.keyboard('{Enter}');
    expect(
      screen.getByText(msg('MSG02', { field_name: 'Search', max_length: 200 })),
    ).toBeInTheDocument();
    expect(queries).toEqual([]);
    await user.clear(box);
    await user.paste('   ');
    await user.keyboard('{Enter}');
    expect(queries).toEqual([]);
    await user.clear(box);
    const q = ` ${'x'.repeat(198)} `;
    await user.paste(q);
    await user.keyboard('{Enter}');
    const results = await screen.findByRole('region', { name: 'Search results' });
    expect(await within(results).findByRole('link', { name: 'VPN guide' })).toBeInTheDocument();
    expect(queries).toEqual([q]);
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.21 shows the three buttons for the selected manager Space and navigates Upload with folder context (BUG-254)', async () => {
    const { user, router } = await openHome();
    expect(screen.getByRole('button', { name: 'New folder' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Space Settings' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Upload documents' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.wikiUpload));
    expect(new URLSearchParams(router.state.location.search).get('space')).toBe(IT);
    expect(new URLSearchParams(router.state.location.search).get('folder')).toBe(FOLDER);
  });

  it('AC-002.21 shows no manager buttons until the user selects a Space they manage', async () => {
    const { user, queryClient } = await openHome('');
    expect(screen.queryByRole('button', { name: 'Upload documents' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New folder' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Space Settings' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'IT Space' }));
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(screen.getByRole('link', { name: 'IT Space' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: 'Upload documents' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New folder' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Space Settings' })).toBeInTheDocument();
  });

  it('AC-002.21 navigates Space Settings and hides manager buttons on a different Space (BUG-254)', async () => {
    const { user, router, queryClient } = await openHome();
    await user.click(screen.getByRole('button', { name: 'Space Settings' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.spaceSettings(IT)));
    await act(() => router.navigate(`${paths.wikiHome}?space=${HR}`));
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(screen.queryByRole('button', { name: 'Upload documents' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Space Settings' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New folder' })).not.toBeInTheDocument();
    expect(screen.queryByText('Rename')).not.toBeInTheDocument();
    expect(screen.queryByText('Move')).not.toBeInTheDocument();
    expect(screen.queryByText('Delete')).not.toBeInTheDocument();
  });

  it.each(['Rename', 'Move'])(
    'AC-002.21 opens the Folder popup from %s without changing the selected folder',
    async (action) => {
      const { user, router } = await openHome();
      const tree = screen.getByRole('tree', { name: 'Folders' });
      const row = within(tree).getByText('Network').closest('[role="treeitem"]');
      if (!(row instanceof HTMLElement)) throw new Error('Folder row missing');
      await user.click(within(row).getByRole('button', { name: `${action} Network` }));
      const dialog = await screen.findByRole('dialog', { name: 'Folder' });
      expect(within(dialog).getByRole('textbox', { name: 'Name' })).toHaveValue('Network');
      expect(new URLSearchParams(router.state.location.search).get('folder')).toBe(FOLDER);
    },
  );

  it('AC-003.16 cancels Delete without a request, then confirms, shows MSG05, refreshes and clears a deleted selection', async () => {
    const deleted = vi.fn();
    let folders = [{ folderId: CHILD, parentId: null, name: 'Network', level: 1 }];
    server.use(
      getListWikiFoldersMockHandler(() => folders),
      getDeleteWikiFolderMockHandler(({ params }) => {
        deleted(params.folderId);
        folders = [];
      }),
    );
    const { user, router } = await openHome(`?space=${IT}&folder=${CHILD}`);
    await user.click(screen.getByRole('button', { name: 'Delete Network' }));
    let dialog = await screen.findByRole('dialog', { name: 'Delete' });
    expect(dialog).toHaveTextContent('Network');
    expect(deleted).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(deleted).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete Network' }));
    dialog = await screen.findByRole('dialog', { name: 'Delete' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('tree', { name: 'Folders' })).not.toHaveTextContent('Network'),
    );
    expect(deleted).toHaveBeenCalledExactlyOnceWith(CHILD);
    expect(new URLSearchParams(router.state.location.search).has('folder')).toBe(false);
  });

  it('AC-003.16 keeps a nonempty folder and shows MSG87 with the server counts', async () => {
    server.use(
      http.delete('*/api/v1/wiki/folders/:folderId', () =>
        problem(409, 'MSG87', {
          params: { folder_count: 2, document_count: 3 },
        }),
      ),
    );
    const { user } = await openHome();
    const row = screen.getByText('Network').closest('[role="treeitem"]');
    if (!(row instanceof HTMLElement)) throw new Error('Folder row missing');
    await user.click(within(row).getByRole('button', { name: 'Delete Network' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));
    expect(
      await within(dialog).findByText(msg('MSG87', { folder_count: 2, document_count: 3 })),
    ).toBeInTheDocument();
    expect(screen.getByRole('tree', { name: 'Folders' })).toHaveTextContent('Network');
  });

  it('AC-002.14 shows loading then an inline error and recovers on retry', async () => {
    server.use(http.get('*/api/v1/wiki/spaces', () => problem(500)));
    const { user } = await openHome();
    expect(screen.getByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(getListWikiSpacesMockHandler(spaces));
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('region', { name: 'Spaces' })).toBeInTheDocument();
  });

  it('AC-002.14 browses all three folder levels and reads documents through the feature mocks', async () => {
    server.resetHandlers();
    const { user, queryClient } = await openHome(`?space=${IT}`);
    const tree = await screen.findByRole('tree', { name: 'Folders' });
    expect(tree).toHaveTextContent('Guides');
    expect(tree).toHaveTextContent('Network');
    await user.click(within(tree).getByText('VPN'));
    const table = screen.getByRole('table', { name: 'Documents' });
    await user.click(await within(table).findByRole('link', { name: 'VPN connection guide' }));
    expect(await screen.findByRole('region', { name: 'Content' })).toHaveTextContent(
      'Connect to the company VPN',
    );
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.18 blocks an overlong shared search URL without truncating it', async () => {
    const requests = vi.fn();
    server.use(
      getSearchWikiDocumentsMockHandler(() => {
        requests();
        return [];
      }),
    );
    const q = 'x'.repeat(201);
    const { box } = await openHome(`?q=${q}`);
    expect(box).toHaveValue(q);
    expect(
      screen.getByText(msg('MSG02', { field_name: 'Search', max_length: 200 })),
    ).toBeInTheDocument();
    expect(requests).not.toHaveBeenCalled();
  });

  it.each([
    ['Company-wide', '*/api/v1/wiki/company-wide', getListCompanyWideDocumentsMockHandler([])],
    ['folders', '*/api/v1/wiki/spaces/:spaceId/folders', getListWikiFoldersMockHandler([])],
    ['documents', '*/api/v1/wiki/spaces/:spaceId/documents', getListWikiDocumentsMockHandler([])],
    ['search', '*/api/v1/wiki/documents/search', getSearchWikiDocumentsMockHandler([])],
  ])('AC-002.14 shows an inline error and retries failed %s data', async (_name, url, success) => {
    server.use(http.get(url, () => problem(500)));
    const { user, queryClient } = await openHome(`?space=${IT}&q=VPN`);
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(success);
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.14 keeps the Company-wide loading state until its response arrives', async () => {
    let release = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      getListCompanyWideDocumentsMockHandler(async () => {
        await pending;
        return [document];
      }),
    );
    const { queryClient } = renderApp(paths.wikiHome, { role: 'EMPLOYEE' });
    await screen.findByRole('searchbox', { name: 'Search' }, { timeout: 5000 });
    const company = screen.getByRole('region', { name: 'Company-wide' });
    expect(within(company).getByText('Loading ...')).toBeInTheDocument();
    expect(within(company).queryByText(msg('MSG04'))).not.toBeInTheDocument();
    release();
    expect(await within(company).findByRole('link', { name: 'VPN guide' })).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });
});
