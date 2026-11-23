import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { WikiDocumentDetail, WikiDocumentSummary, WikiSpace } from '@/shared/api';
import {
  getDeleteWikiFolderMockHandler,
  getGetWikiDocumentMockHandler,
  getListCompanyWideDocumentsMockHandler,
  getListWikiDocumentsMockHandler,
  getListWikiFoldersMockHandler,
  getListWikiSpacesMockHandler,
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
  documentType: 'GUIDE',
  owner: 'Dang Nhat Minh',
  updatedAt: '2026-11-13T08:00:00Z',
};
const handbook: WikiDocumentSummary = {
  ...document,
  documentId: '00000000-0000-4000-8000-000000009002',
  title: 'Employee handbook',
  spaceId: HR,
  spaceName: 'HR Space',
  folderId: null,
  folderName: null,
  visibility: 'COMPANY_WIDE',
  documentType: 'POLICY',
  owner: 'Tran Hai Anh',
};
const detail = (summary: WikiDocumentSummary): WikiDocumentDetail => ({
  ...summary,
  content: `${summary.title} ${'y'.repeat(600)}`,
  fileName: 'guide.pdf',
  fileSize: 24680,
  canDelete: false,
  inTrash: false,
});

const renderDocx = vi.hoisted(() => vi.fn());
vi.mock('docx-preview', () => ({ renderAsync: renderDocx }));

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:file');
  URL.revokeObjectURL = vi.fn();
  renderDocx.mockResolvedValue(undefined);
  server.use(
    // The preview pane loads the original file (AC-002.24); msw cannot stream a jsdom Blob.
    http.get(
      '*/api/v1/wiki/documents/:documentId/file',
      () => new HttpResponse(new TextEncoder().encode('%PDF-1.1')),
    ),
    getListWikiSpacesMockHandler(spaces),
    getListCompanyWideDocumentsMockHandler([{ ...document, visibility: 'COMPANY_WIDE' }, handbook]),
    getListWikiFoldersMockHandler([
      { folderId: FOLDER, parentId: null, name: 'Guides', level: 1 },
      { folderId: CHILD, parentId: FOLDER, name: 'Network', level: 2 },
    ]),
    getListWikiDocumentsMockHandler(({ request }) =>
      new URL(request.url).searchParams.get('folderId') === FOLDER ? [document, handbook] : [],
    ),
    getSearchWikiDocumentsMockHandler([document]),
    getGetWikiDocumentMockHandler(({ params }) =>
      detail(params.documentId === handbook.documentId ? handbook : document),
    ),
  );
});

async function openHome(query = `?space=${IT}&folder=${FOLDER}`) {
  const view = renderApp(`${paths.wikiHome}${query}`, { role: 'KNOWLEDGE_MANAGER' });
  const box = await screen.findByRole('searchbox', { name: 'Search' }, { timeout: 5000 });
  await waitFor(() => {
    expect(screen.queryAllByText('Loading ...')).toHaveLength(0);
    expect(view.queryClient.isFetching()).toBe(0);
  });
  const search = () => new URLSearchParams(view.router.state.location.search);
  return { ...view, box, search, user: userEvent.setup() };
}

const rowLink = (title: string) => screen.getByRole('link', { name: new RegExp(`^${title}`) });
const preview = () => screen.getByRole('complementary', { name: 'Preview' });

async function folderAction(
  user: ReturnType<typeof userEvent.setup>,
  folder: string,
  action: 'Rename' | 'Move' | 'Delete',
) {
  await user.click(screen.getByRole('button', { name: `Actions for ${folder}` }));
  await user.click(await screen.findByRole('menuitem', { name: action }));
}

describe('WikiHomePage', () => {
  it('AC-002.14 shows the navigator, the list pane with document rows and the preview pane (BUG-254, BUG-255)', async () => {
    const { box } = await openHome();
    const navigator = screen.getByRole('navigation', { name: 'Wiki' });
    expect(within(navigator).getByRole('link', { name: 'Company-wide' })).toBeInTheDocument();
    const spaceList = within(navigator).getByRole('region', { name: 'Spaces' });
    expect(within(spaceList).getByRole('link', { name: 'HR Space' })).toHaveAccessibleDescription(
      'View',
    );
    expect(within(spaceList).getByRole('link', { name: 'IT Space' })).toHaveAccessibleDescription(
      'Knowledge Manager',
    );
    expect(within(spaceList).getByRole('tree', { name: 'Folders' })).toHaveTextContent('Network');
    expect(navigator.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'IT Space / Guides' })).toBeInTheDocument();
    const row = rowLink('VPN guide');
    expect(box.compareDocumentPosition(row) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(row).toHaveTextContent('Guides');
    expect(within(row).getByText('Guide')).toBeInTheDocument();
    expect(within(rowLink('Employee handbook')).getByText('Policy')).toBeInTheDocument();
    expect(row).toHaveTextContent('Dang Nhat Minh');
    expect(row).toHaveTextContent('Department');
    expect(row).toHaveTextContent('13/11/2026 15:00');
    // A row from another Space names that Space instead of a folder.
    expect(rowLink('Employee handbook')).toHaveTextContent('HR Space');
    expect(screen.getByText('2 documents')).toBeInTheDocument();
    expect(
      await within(preview()).findByRole('heading', { name: 'VPN guide' }),
    ).toBeInTheDocument();
  });

  it('AC-002.14 shows loading then an inline error and recovers on retry', async () => {
    server.use(http.get('*/api/v1/wiki/spaces', () => problem(500)));
    const { user } = await openHome();
    expect(screen.getByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(getListWikiSpacesMockHandler(spaces));
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('region', { name: 'Spaces' })).toBeInTheDocument();
  });

  it.each([
    ['Company-wide', '', '*/api/v1/wiki/company-wide', getListCompanyWideDocumentsMockHandler([])],
    [
      'folders',
      `?space=${IT}`,
      '*/api/v1/wiki/spaces/:spaceId/folders',
      getListWikiFoldersMockHandler([]),
    ],
    [
      'documents',
      `?space=${IT}`,
      '*/api/v1/wiki/spaces/:spaceId/documents',
      getListWikiDocumentsMockHandler([]),
    ],
    ['search', '?q=VPN', '*/api/v1/wiki/documents/search', getSearchWikiDocumentsMockHandler([])],
  ])(
    'AC-002.14 shows an inline error and retries failed %s data',
    async (_name, query, url, success) => {
      server.use(http.get(url, () => problem(500)));
      const { user, queryClient } = await openHome(query);
      expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
      server.use(success);
      await user.click(screen.getByRole('button', { name: 'Try again' }));
      await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
      await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    },
  );

  it('AC-002.14 AC-002.19 browses all three folder levels and opens a document through the feature mocks (BUG-358)', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:vpn');
    URL.revokeObjectURL = vi.fn();
    server.resetHandlers();
    // The generated file mock answers a jsdom Blob, which msw cannot stream.
    server.use(
      http.get(
        '*/api/v1/wiki/documents/:documentId/file',
        () => new HttpResponse(new TextEncoder().encode('%PDF-1.1')),
      ),
    );
    const { user, router, queryClient } = await openHome(`?space=${IT}`);
    const tree = await screen.findByRole('tree', { name: 'Folders' });
    expect(tree).toHaveTextContent('Guides');
    expect(tree).toHaveTextContent('Network');
    await user.click(within(tree).getByText('VPN'));
    await user.click(await screen.findByRole('link', { name: /^VPN connection guide/ }));
    await user.click(await within(preview()).findByRole('button', { name: 'Open' }));
    await waitFor(
      () => expect(screen.getByTitle('VPN connection guide')).toHaveAttribute('src', 'blob:vpn'),
      { timeout: 5000 },
    );
    expect(router.state.location.pathname).toBe(
      paths.wikiDocument('00000000-0000-4000-8000-000000009001'),
    );
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.14 keeps the list loading state until the Company-wide response arrives', async () => {
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
    expect(screen.getAllByText('Loading ...').length).toBeGreaterThan(0);
    expect(screen.queryByText(msg('MSG04'))).not.toBeInTheDocument();
    release();
    expect(await screen.findByRole('link', { name: /^VPN guide/ })).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.15 shows only Company-wide, Search, the documents and the preview when no Space is viewable, even with a stale URL', async () => {
    server.use(getListWikiSpacesMockHandler([]));
    const requests = vi.fn();
    server.events.on('request:start', ({ request }) => {
      if (/\/spaces\/[^/]+\/(folders|documents)/.test(request.url)) requests();
    });
    await openHome();
    const navigator = screen.getByRole('navigation', { name: 'Wiki' });
    expect(
      within(navigator)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Company-wide']);
    expect(within(navigator).getByRole('link', { name: 'Company-wide' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.queryByRole('region', { name: 'Spaces' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tree')).not.toBeInTheDocument();
    expect(rowLink('VPN guide')).toBeInTheDocument();
    expect(preview()).toBeInTheDocument();
    for (const name of ['Upload documents', 'New folder', 'Space Settings']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
    expect(requests).not.toHaveBeenCalled();
  });

  it('AC-002.16 shows MSG04 in the list and the preview for an empty Company-wide list, folder and search result', async () => {
    server.use(getListCompanyWideDocumentsMockHandler([]), getSearchWikiDocumentsMockHandler([]));
    const { user, box, queryClient } = await openHome('');
    const listPane = () => screen.getByRole('heading', { name: 'Company-wide' }).parentElement;
    expect(listPane()?.parentElement).toHaveTextContent(msg('MSG04'));
    expect(within(preview()).getByText(msg('MSG04'))).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'IT Space' }));
    expect(await screen.findByRole('heading', { name: 'IT Space' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText(msg('MSG04'))).toHaveLength(2));
    await user.click(box);
    await user.paste('no match');
    await user.keyboard('{Enter}');
    expect(
      await screen.findByRole('heading', { name: 'Search results for “no match”' }),
    ).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(screen.getAllByText(msg('MSG04'))).toHaveLength(2);
  });

  it('AC-002.17 keeps Space and folder in the URL, removes q and doc, clears the old folder on Space change and supports Back', async () => {
    const { user, router, queryClient, search } = await openHome(
      `?q=VPN&doc=${document.documentId}`,
    );
    await user.click(screen.getByRole('link', { name: 'IT Space' }));
    expect(search().has('q')).toBe(false);
    expect(search().has('doc')).toBe(false);
    await user.click(
      await within(screen.getByRole('tree', { name: 'Folders' })).findByText('Guides'),
    );
    await waitFor(() => expect(search().get('folder')).toBe(FOLDER));
    expect(await screen.findByRole('link', { name: /^VPN guide/ })).toBeInTheDocument();
    await user.click(rowLink('Employee handbook'));
    expect(search().get('doc')).toBe(handbook.documentId);
    await user.click(screen.getByRole('link', { name: 'HR Space' }));
    expect(search().get('space')).toBe(HR);
    expect(search().has('folder')).toBe(false);
    expect(search().has('doc')).toBe(false);
    await act(() => router.navigate(-1));
    expect(search().get('space')).toBe(IT);
    expect(search().get('folder')).toBe(FOLDER);
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(screen.getByRole('tree', { name: 'Folders' })).toHaveTextContent('Guides');
  });

  it('AC-002.17 a folder selection in the tree removes q and doc', async () => {
    const { user, search } = await openHome(`?space=${IT}&q=VPN&doc=${document.documentId}`);
    await user.click(within(screen.getByRole('tree', { name: 'Folders' })).getByText('Guides'));
    await waitFor(() => expect(search().get('folder')).toBe(FOLDER));
    expect(search().has('q')).toBe(false);
    expect(search().has('doc')).toBe(false);
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
    const { router, queryClient, search, user } = await openHome(`?space=${IT}&folder=${HR}&q=VPN`);
    await waitFor(() => expect(search().has('folder')).toBe(false));
    expect(search().get('space')).toBe(IT);
    expect(search().get('q')).toBe('VPN');
    // The root request starts only after the stale folder leaves the URL; wait for it.
    await waitFor(() => expect(requestedFolders).toEqual([HR, null]));
    await user.click(screen.getByRole('link', { name: 'Clear search' }));
    expect(await screen.findByRole('link', { name: 'Root guide' })).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    await act(() => router.navigate(`${paths.wikiHome}?space=${IT}&q=previous`));
    await act(() => router.navigate(`${paths.wikiHome}?space=${IT}&folder=${HR}&q=VPN`));
    await waitFor(() => expect(search().has('folder')).toBe(false));
    await act(() => router.navigate(-1));
    expect(search().get('q')).toBe('previous');
    expect(search().has('folder')).toBe(false);
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
    expect(
      await screen.findByRole('heading', { name: /^Search results for “\s*x{198}\s*”$/ }),
    ).toBeInTheDocument();
    expect(queries).toEqual([q]);
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

  it('AC-002.21 shows the three buttons for the selected manager Space and navigates Upload with folder context (BUG-254)', async () => {
    const { user, router, search } = await openHome();
    expect(screen.getByRole('button', { name: 'New folder' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Space Settings' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Upload documents' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.wikiUpload));
    expect(search().get('space')).toBe(IT);
    expect(search().get('folder')).toBe(FOLDER);
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

  it('AC-002.21 navigates Space Settings and hides manager buttons and folder actions on a different Space (BUG-254)', async () => {
    const { user, router, queryClient } = await openHome();
    await user.click(screen.getByRole('button', { name: 'Space Settings' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(paths.spaceSettings(IT)));
    await act(() => router.navigate(`${paths.wikiHome}?space=${HR}`));
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(screen.queryByRole('button', { name: 'Upload documents' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Space Settings' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New folder' })).not.toBeInTheDocument();
    expect(screen.getByRole('tree', { name: 'Folders' })).toHaveTextContent('Guides');
    expect(screen.queryByRole('button', { name: /^Actions for/ })).not.toBeInTheDocument();
  });

  it('AC-002.21 puts Rename, Move and Delete in a "⋯" menu per folder', async () => {
    const { user, search } = await openHome();
    await user.click(screen.getByRole('button', { name: 'Actions for Network' }));
    const menu = (await screen.findByRole('menuitem', { name: 'Rename' })).closest('[role="menu"]');
    if (!(menu instanceof HTMLElement)) throw new Error('Folder menu missing');
    expect(
      within(menu)
        .getAllByRole('menuitem')
        .map((item) => item.textContent),
    ).toEqual(['Rename', 'Move', 'Delete']);
    // Opening the menu does not select the folder.
    expect(search().get('folder')).toBe(FOLDER);
  });

  it.each(['Rename', 'Move'] as const)(
    'AC-002.21 opens the Folder popup from %s without changing the selected folder',
    async (action) => {
      const { user, search } = await openHome();
      await folderAction(user, 'Network', action);
      const dialog = await screen.findByRole('dialog', { name: 'Folder' });
      expect(within(dialog).getByRole('textbox', { name: 'Name' })).toHaveValue('Network');
      expect(search().get('folder')).toBe(FOLDER);
    },
  );

  it('AC-002.22 marks Company-wide current and lists its documents in server order with the Space name on each row', async () => {
    await openHome(`?space=${'00000000-0000-4000-8000-0000000000ff'}`);
    expect(screen.getByRole('link', { name: 'Company-wide' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('heading', { name: 'Company-wide' })).toBeInTheDocument();
    const rows = screen.getAllByRole('listitem');
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringMatching(/^VPN guideGuideIT Space/),
      expect.stringMatching(/^Employee handbookPolicyHR Space/),
    ]);
  });

  it('AC-002.23 shows the search results in the list pane and Clear search removes q', async () => {
    const { user, box, search, queryClient } = await openHome(`?space=${IT}`);
    await user.click(box);
    await user.paste('VPN');
    await user.keyboard('{Enter}');
    expect(
      await screen.findByRole('heading', { name: 'Search results for “VPN”' }),
    ).toBeInTheDocument();
    expect(search().get('q')).toBe('VPN');
    expect(await screen.findByRole('link', { name: /^VPN guide/ })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'Clear search' }));
    expect(search().has('q')).toBe(false);
    expect(search().get('space')).toBe(IT);
    expect(await screen.findByRole('heading', { name: 'IT Space' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Clear search' })).not.toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  });

  it('AC-002.24 previews the first row, then the selected row with its details, Open and Download', async () => {
    const { user, search, router } = await openHome(`?space=${IT}&folder=${FOLDER}&doc=${HR}`);
    expect(rowLink('VPN guide')).toHaveAttribute('aria-current', 'true');
    expect(
      await within(preview()).findByRole('heading', { name: 'VPN guide' }),
    ).toBeInTheDocument();
    await user.click(rowLink('Employee handbook'));
    expect(search().get('doc')).toBe(handbook.documentId);
    expect(rowLink('Employee handbook')).toHaveAttribute('aria-current', 'true');
    const pane = preview();
    expect(
      await within(pane).findByRole('heading', { name: 'Employee handbook' }),
    ).toBeInTheDocument();
    expect(pane).toHaveTextContent('Company-wide');
    const terms = [...pane.querySelectorAll('dt')].map((dt) => [
      dt.textContent,
      dt.nextElementSibling?.textContent,
    ]);
    expect(terms).toEqual([
      ['Type', 'Policy'],
      ['Space', 'HR Space'],
      ['Folder', '—'],
      ['Owner', 'Tran Hai Anh'],
      ['Updated', '13/11/2026 15:00'],
      ['File', 'guide.pdf'],
      ['Size', '24 KB'],
    ]);
    expect(await within(pane).findByTitle('Employee handbook')).toHaveAttribute(
      'src',
      'blob:file#page=1&view=FitH&toolbar=0',
    );
    expect(pane).not.toHaveTextContent(detail(handbook).content.slice(0, 40));
    expect(within(pane).getByRole('link', { name: /Download/ })).toHaveAttribute(
      'href',
      expect.stringMatching(new RegExp(`/wiki/documents/${handbook.documentId}/file$`)),
    );
    await user.click(within(pane).getByRole('button', { name: 'Open' }));
    await waitFor(() =>
      expect(router.state.location.pathname).toBe(paths.wikiDocument(handbook.documentId)),
    );
  });

  it('AC-002.24 previews only the first page of a DOCX', async () => {
    renderDocx.mockImplementation((_blob: Blob, container: HTMLElement) => {
      container.innerHTML =
        '<div class="docx-wrapper"><section>Page one</section><section>Page two</section></div>';
      return Promise.resolve();
    });
    server.use(getGetWikiDocumentMockHandler({ ...detail(document), fileName: 'guide.docx' }));
    await openHome();
    const page = await within(preview()).findByLabelText('VPN guide');
    await waitFor(() => expect(page).toHaveTextContent('Page one'));
    expect(page).not.toHaveTextContent('Page two');
  });

  it('AC-002.27 shows the first 420 characters when the file cannot be loaded', async () => {
    server.use(http.get('*/api/v1/wiki/documents/:documentId/file', () => problem(404, 'MSG110')));
    await openHome();
    const pane = preview();
    const content = detail(document).content;
    expect(await within(pane).findByText(content.slice(0, 420))).toBeInTheDocument();
    expect(pane).not.toHaveTextContent(content.slice(0, 421));
    expect(within(pane).queryByTitle('VPN guide')).not.toBeInTheDocument();
  });

  it('AC-002.27 shows the first 420 characters when the DOCX cannot be rendered', async () => {
    renderDocx.mockRejectedValue(new Error('bad docx'));
    server.use(getGetWikiDocumentMockHandler({ ...detail(document), fileName: 'guide.docx' }));
    await openHome();
    const pane = preview();
    const content = detail(document).content;
    expect(await within(pane).findByText(content.slice(0, 420))).toBeInTheDocument();
    expect(pane).not.toHaveTextContent(content.slice(0, 421));
    expect(within(pane).queryByLabelText('VPN guide')).not.toBeInTheDocument();
  });

  it('AC-002.25 shows "This document is no longer available." when the preview answers 404 MSG110', async () => {
    server.use(http.get('*/api/v1/wiki/documents/:documentId', () => problem(404, 'MSG110')));
    await openHome();
    expect(await within(preview()).findByText(msg('MSG110'))).toBeInTheDocument();
    expect(msg('MSG110')).toBe('This document is no longer available.');
  });

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
    const { user, search } = await openHome(`?space=${IT}&folder=${CHILD}`);
    await folderAction(user, 'Network', 'Delete');
    let dialog = await screen.findByRole('dialog', { name: 'Delete' });
    expect(dialog).toHaveTextContent('Network');
    expect(deleted).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(deleted).not.toHaveBeenCalled();
    await folderAction(user, 'Network', 'Delete');
    dialog = await screen.findByRole('dialog', { name: 'Delete' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('tree', { name: 'Folders' })).not.toHaveTextContent('Network'),
    );
    expect(deleted).toHaveBeenCalledExactlyOnceWith(CHILD);
    expect(search().has('folder')).toBe(false);
  });

  it('AC-003.16 keeps a nonempty folder, shows MSG87 with the server counts and disables Delete', async () => {
    const deleted = vi.fn(() =>
      problem(409, 'MSG87', { params: { folder_count: 2, document_count: 3 } }),
    );
    server.use(http.delete('*/api/v1/wiki/folders/:folderId', deleted));
    const { user } = await openHome();
    await folderAction(user, 'Network', 'Delete');
    const dialog = await screen.findByRole('dialog', { name: 'Delete' });
    const confirm = within(dialog).getByRole('button', { name: 'Delete' });
    await user.click(confirm);
    expect(
      await within(dialog).findByText(msg('MSG87', { folder_count: 2, document_count: 3 })),
    ).toBeInTheDocument();
    expect(confirm).toBeDisabled();
    expect(deleted).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('tree', { name: 'Folders' })).toHaveTextContent('Network');
  });
});
