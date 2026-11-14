import { delay, getResponse, http, type HttpHandler } from 'msw';
import {
  getMe,
  type CreateWikiFolderRequest,
  type UpdateWikiFolderRequest,
  type WikiDocumentDetail,
  type WikiFolder,
  type WikiSpace,
  type WikiPublishMetadata,
  type WikiUploadPreview,
  type WikiTrashItem,
} from '@/shared/api';
import {
  getGetWikiDocumentMockHandler,
  getListCompanyWideDocumentsMockHandler,
  getListWikiDocumentsMockHandler,
  getListWikiFoldersMockHandler,
  getListWikiSpacesMockHandler,
  getSearchWikiDocumentsMockHandler,
  getCreateWikiFolderMockHandler,
  getUpdateWikiFolderMockHandler,
  getDeleteWikiFolderMockHandler,
  getExtractWikiUploadsMockHandler,
  getPublishWikiDocumentsMockHandler,
  getTrashWikiDocumentMockHandler,
  getListWikiTrashMockHandler,
  getRestoreWikiDocumentMockHandler,
  getDeleteWikiDocumentPermanentlyMockHandler,
} from '@/shared/api/generated/wiki/wiki.msw';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser } from '@/shared/auth';

const IT = '00000000-0000-4000-8000-0000000000a1';
const HR = '00000000-0000-4000-8000-0000000000a2';
const GUIDES = '00000000-0000-4000-8000-0000000000f1';
const NETWORK = '00000000-0000-4000-8000-0000000000f2';
const VPN = '00000000-0000-4000-8000-0000000000f3';
const spaces = [
  { spaceId: HR, name: 'HR Space', departmentName: 'HR' },
  { spaceId: IT, name: 'IT Space', departmentName: 'IT' },
];
const folders: WikiFolder[] = [
  { folderId: GUIDES, parentId: null, name: 'Guides', level: 1 },
  { folderId: NETWORK, parentId: GUIDES, name: 'Network', level: 2 },
  { folderId: VPN, parentId: NETWORK, name: 'VPN', level: 3 },
];
const folderState = new Map<string, WikiFolder[]>();
const initialDocuments: WikiDocumentDetail[] = [
  {
    documentId: '00000000-0000-4000-8000-000000009001',
    title: 'VPN connection guide',
    spaceId: IT,
    spaceName: 'IT Space',
    folderId: VPN,
    folderName: 'VPN',
    visibility: 'COMPANY_WIDE',
    owner: 'Lê Thu Hương',
    updatedAt: '2026-11-13T08:00:00Z',
    content:
      'Connect to the company VPN before opening internal tools.\nContact IT Support if the connection fails.',
    fileName: 'vpn-guide.pdf',
    fileSize: 24680,
    canDelete: false,
  },
  {
    documentId: '00000000-0000-4000-8000-000000009002',
    title: 'Employee handbook',
    spaceId: HR,
    spaceName: 'HR Space',
    folderId: null,
    folderName: null,
    visibility: 'COMPANY_WIDE',
    owner: 'Võ Thanh Tâm',
    updatedAt: '2026-11-12T09:00:00Z',
    content: 'Welcome to the company.\nThis handbook describes working hours and leave requests.',
    fileName: 'employee-handbook.docx',
    fileSize: 18200,
    canDelete: false,
  },
  {
    documentId: '00000000-0000-4000-8000-000000009003',
    title: 'IT support procedures',
    spaceId: IT,
    spaceName: 'IT Space',
    folderId: null,
    folderName: null,
    visibility: 'DEPARTMENT',
    owner: 'Lê Thu Hương',
    updatedAt: '2026-11-11T07:30:00Z',
    content:
      'Record each support request in Jira.\nCheck the VPN connection before investigating network issues.',
    fileName: 'support-procedures.pdf',
    fileSize: 12900,
    canDelete: false,
  },
];
let documents = initialDocuments.map((document) => ({ ...document }));
const trashState = new Map<string, Pick<WikiTrashItem, 'deletedBy' | 'deletedAt'>>();

function publishedDocuments() {
  return documents.filter((document) => !trashState.has(document.documentId));
}

async function documentManagerError(document: WikiDocumentDetail) {
  const space = (await viewableSpaces()).find((space) => space.spaceId === document.spaceId);
  if (space?.accessLevel === 'KNOWLEDGE_MANAGER') return null;
  return !trashState.has(document.documentId) && (document.visibility === 'COMPANY_WIDE' || space)
    ? problem(403, 'MSG08')
    : problem(404, 'MSG110');
}

async function uploadFiles(request: Request): Promise<File[] | Response> {
  const parts = (await request.clone().formData()).getAll('files');
  if (!parts.length)
    return problem(400, 'MSG01', {
      fieldErrors: [{ field: 'files', code: 'MSG01', params: { field_name: 'Files' } }],
    });
  if (parts.length > 10) return problem(400, 'MSG88');
  const files: File[] = [];
  for (const part of parts) {
    if (typeof part === 'string') return problem(400, 'MSG89', { params: { file_name: part } });
    if (part.size > 10 * 1024 * 1024) return problem(413, 'MSG28');
    const start = await part.slice(0, 5).text();
    const isPdf = /\.pdf$/i.test(part.name) && start.startsWith('%PDF-');
    const isDocx = /\.docx$/i.test(part.name) && start.startsWith('PK\u0003\u0004');
    if (!isPdf && !isDocx) return problem(400, 'MSG89', { params: { file_name: part.name } });
    files.push(part);
  }
  return files;
}

/** Mock mode uses a fixed excerpt; named scanned/encrypted fixtures exercise UNREADABLE without a browser PDF parser. */
function uploadPreview(file: File): WikiUploadPreview {
  const isUnreadable = /scanned|encrypted|unreadable/i.test(file.name);
  const excerpt = isUnreadable ? '' : 'Connect to the company VPN before opening internal tools.';
  return {
    fileName: file.name,
    title: file.name.replace(/\.(pdf|docx)$/i, '').slice(0, 200),
    excerpt,
    wordCount: isUnreadable ? 0 : excerpt.split(/\s+/).length,
    result: isUnreadable ? 'UNREADABLE' : 'READY',
  };
}

async function uploadMetadata(request: Request): Promise<WikiPublishMetadata | Response> {
  const part = (await request.clone().formData()).get('metadata');
  const missing = () =>
    problem(400, 'MSG01', {
      fieldErrors: [{ field: 'metadata', code: 'MSG01', params: { field_name: 'Metadata' } }],
    });
  if (!part) return missing();
  let value: unknown;
  try {
    value = JSON.parse(typeof part === 'string' ? part : await part.text());
  } catch {
    return missing();
  }
  if (
    typeof value !== 'object' ||
    value === null ||
    !('documents' in value) ||
    !Array.isArray(value.documents)
  )
    return missing();
  const rows: WikiPublishMetadata['documents'] = [];
  const entries: unknown[] = value.documents;
  for (const row of entries) {
    if (
      typeof row !== 'object' ||
      row === null ||
      !('title' in row) ||
      typeof row.title !== 'string' ||
      !('visibility' in row) ||
      (row.visibility !== 'DEPARTMENT' && row.visibility !== 'COMPANY_WIDE')
    )
      return missing();
    rows.push({ title: row.title, visibility: row.visibility });
  }
  const folderId = 'folderId' in value ? value.folderId : null;
  if (folderId !== null && typeof folderId !== 'string') return missing();
  return { folderId, documents: rows };
}

/** The same /me projection used by the guards, including role and manager changes in mock mode. */
async function viewableSpaces(): Promise<WikiSpace[]> {
  const me = await getMe();
  return spaces.flatMap((space) => {
    const isManager =
      me.roles.includes('KNOWLEDGE_MANAGER') &&
      me.managedSpaces.some((managed) => managed.spaceId === space.spaceId);
    const isDepartment = me.departments.some(
      (department) => department.name === space.departmentName,
    );
    return isManager || isDepartment
      ? [{ ...space, accessLevel: isManager ? 'KNOWLEDGE_MANAGER' : 'VIEW' }]
      : [];
  });
}

async function spaceError(spaceId: string) {
  if (!spaces.some((space) => space.spaceId === spaceId)) return problem(404, 'MSG04');
  return (await viewableSpaces()).some((space) => space.spaceId === spaceId)
    ? null
    : problem(403, 'MSG08');
}

async function managerError(spaceId: string) {
  const error = await spaceError(spaceId);
  if (error) return error;
  return (await viewableSpaces()).some(
    (space) => space.spaceId === spaceId && space.accessLevel === 'KNOWLEDGE_MANAGER',
  )
    ? null
    : problem(403, 'MSG08');
}

function saveFolder(
  spaceId: string,
  data: CreateWikiFolderRequest,
  folderId?: string,
): WikiFolder | Response {
  const current = folderState.get(spaceId) ?? [];
  const folder = current.find((item) => item.folderId === folderId);
  if (typeof data.name !== 'string' || !data.name.trim())
    return problem(400, 'MSG01', {
      fieldErrors: [{ field: 'name', code: 'MSG01', params: { field_name: 'Name' } }],
    });
  if (data.name.length > 100)
    return problem(400, 'MSG02', {
      fieldErrors: [
        { field: 'name', code: 'MSG02', params: { field_name: 'Name', max_length: 100 } },
      ],
    });
  const parentId = data.parentId ?? null;
  const parent = current.find((item) => item.folderId === parentId);
  if (parentId && !parent) return problem(404, 'MSG04');
  const name = data.name.trim();
  if (
    current.some(
      (item) =>
        item.folderId !== folderId &&
        item.parentId === parentId &&
        item.name.toLowerCase() === name.toLowerCase(),
    )
  ) {
    return problem(409, 'MSG85', { fieldErrors: [{ field: 'name', code: 'MSG85' }] });
  }
  const subtree = new Set(folderId ? [folderId] : []);
  for (let level = 1; level <= 3; level++) {
    for (const item of current) {
      if (item.parentId && subtree.has(item.parentId)) subtree.add(item.folderId);
    }
  }
  const level = (parent?.level ?? 0) + 1;
  const delta = level - (folder?.level ?? level);
  if (
    level > 3 ||
    (parentId && subtree.has(parentId)) ||
    current.some((item) => subtree.has(item.folderId) && item.level + delta > 3)
  ) {
    return problem(400, 'MSG86', { fieldErrors: [{ field: 'parentId', code: 'MSG86' }] });
  }
  const saved = { folderId: folderId ?? crypto.randomUUID(), name, parentId, level };
  folderState.set(
    spaceId,
    folderId
      ? current.map((item) =>
          item.folderId === folderId
            ? saved
            : subtree.has(item.folderId)
              ? { ...item, level: item.level + delta }
              : item,
        )
      : [...current, saved],
  );
  return saved;
}

function findFolderSpace(folderId: string) {
  return [...folderState].find(([, items]) => items.some((folder) => folder.folderId === folderId));
}

/** Generated factories serialize every successful response; guards supply the contract's error codes. */
export const handlers: HttpHandler[] = [
  http.all('*/api/v1/wiki/*', () => {
    if (!getDevUser()) return problem(401, 'MSG07');
  }),
  getListWikiSpacesMockHandler(async () => {
    await delay(MOCK_DELAY_MS);
    return viewableSpaces();
  }),
  getListCompanyWideDocumentsMockHandler(async () => {
    await delay(MOCK_DELAY_MS);
    return publishedDocuments()
      .filter((document) => document.visibility === 'COMPANY_WIDE')
      .sort((a, b) => a.spaceName.localeCompare(b.spaceName) || a.title.localeCompare(b.title));
  }),
  http.get('*/api/v1/wiki/spaces/:spaceId/folders', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const spaceId = String(params.spaceId);
    const error = await spaceError(spaceId);
    if (error) return error;
    return getResponse([getListWikiFoldersMockHandler(folderState.get(spaceId) ?? [])], request);
  }),
  http.post<{ spaceId: string }, CreateWikiFolderRequest>(
    '*/api/v1/wiki/spaces/:spaceId/folders',
    async ({ params, request }) => {
      await delay(MOCK_DELAY_MS);
      const error = await managerError(params.spaceId);
      if (error) return error;
      const result = saveFolder(params.spaceId, await request.json());
      return result instanceof Response
        ? result
        : getResponse([getCreateWikiFolderMockHandler(result)], request);
    },
  ),
  http.put<{ folderId: string }, UpdateWikiFolderRequest>(
    '*/api/v1/wiki/folders/:folderId',
    async ({ params, request }) => {
      await delay(MOCK_DELAY_MS);
      const found = findFolderSpace(params.folderId);
      if (!found) return problem(404, 'MSG04');
      const error = await managerError(found[0]);
      if (error) return error;
      const result = saveFolder(found[0], await request.json(), params.folderId);
      return result instanceof Response
        ? result
        : getResponse([getUpdateWikiFolderMockHandler(result)], request);
    },
  ),
  http.delete('*/api/v1/wiki/folders/:folderId', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const folderId = String(params.folderId);
    const found = findFolderSpace(folderId);
    if (!found) return problem(404, 'MSG04');
    const [spaceId, items] = found;
    const error = await managerError(spaceId);
    if (error) return error;
    const folderCount = items.filter((item) => item.parentId === folderId).length;
    const documentCount = documents.filter((item) => item.folderId === folderId).length;
    if (folderCount || documentCount)
      return problem(409, 'MSG87', {
        params: { folder_count: folderCount, document_count: documentCount },
      });
    folderState.set(
      spaceId,
      items.filter((item) => item.folderId !== folderId),
    );
    return getResponse([getDeleteWikiFolderMockHandler()], request);
  }),
  http.get('*/api/v1/wiki/spaces/:spaceId/documents', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const spaceId = String(params.spaceId);
    const error = await spaceError(spaceId);
    if (error) return error;
    const folderId = new URL(request.url).searchParams.get('folderId');
    if (folderId && !folderState.get(spaceId)?.some((folder) => folder.folderId === folderId))
      return problem(404, 'MSG04');
    return getResponse(
      [
        getListWikiDocumentsMockHandler(
          publishedDocuments().filter(
            (document) => document.spaceId === spaceId && document.folderId === folderId,
          ),
        ),
      ],
      request,
    );
  }),
  http.post('*/api/v1/wiki/spaces/:spaceId/uploads/extract', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const error = await managerError(String(params.spaceId));
    if (error) return error;
    const files = await uploadFiles(request);
    if (files instanceof Response) return files;
    return getResponse([getExtractWikiUploadsMockHandler(files.map(uploadPreview))], request);
  }),
  http.post('*/api/v1/wiki/spaces/:spaceId/documents', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const spaceId = String(params.spaceId);
    const error = await managerError(spaceId);
    if (error) return error;
    const files = await uploadFiles(request);
    if (files instanceof Response) return files;
    const metadata = await uploadMetadata(request);
    if (metadata instanceof Response) return metadata;
    if (metadata.documents.length !== files.length)
      return problem(400, 'MSG01', {
        fieldErrors: [{ field: 'metadata', code: 'MSG01', params: { field_name: 'Metadata' } }],
      });
    const folderId = metadata.folderId ?? null;
    const folder = folderState.get(spaceId)?.find((item) => item.folderId === folderId);
    if (folderId && !folder) return problem(404, 'MSG04');
    const titles = new Set(
      publishedDocuments()
        .filter((document) => document.spaceId === spaceId && document.folderId === folderId)
        .map((document) => document.title.toLowerCase()),
    );
    for (const [index, row] of metadata.documents.entries()) {
      const title = row.title.trim();
      const code = !title
        ? 'MSG01'
        : title.length > 200
          ? 'MSG02'
          : titles.has(title.toLowerCase())
            ? 'MSG84'
            : null;
      if (code)
        return problem(code === 'MSG84' ? 409 : 400, code, {
          fieldErrors: [
            {
              field: `documents[${index}].title`,
              code,
              params: { field_name: 'Title', max_length: 200 },
            },
          ],
        });
      titles.add(title.toLowerCase());
    }
    const previews = files.map(uploadPreview);
    const unreadable = previews.find((preview) => preview.result === 'UNREADABLE');
    if (unreadable) return problem(400, 'MSG90', { params: { file_name: unreadable.fileName } });
    const me = await getMe();
    const created = files.flatMap((file, index): WikiDocumentDetail[] => {
      const row = metadata.documents[index];
      const preview = previews[index];
      if (!row || !preview) return [];
      return [
        {
          documentId: crypto.randomUUID(),
          title: row.title.trim(),
          visibility: row.visibility,
          spaceId,
          spaceName: spaces.find((space) => space.spaceId === spaceId)?.name ?? '',
          folderId,
          folderName: folder?.name ?? null,
          owner: me.user.fullName,
          updatedAt: new Date().toISOString(),
          content: preview.excerpt,
          fileName: file.name,
          fileSize: file.size,
          canDelete: true,
        },
      ];
    });
    documents.push(...created);
    return getResponse(
      [
        getPublishWikiDocumentsMockHandler(
          created.map(({ documentId, title }) => ({ documentId, title })),
        ),
      ],
      request,
    );
  }),
  http.get('*/api/v1/wiki/documents/search', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const q = new URL(request.url).searchParams.get('q') ?? '';
    if (!q.trim())
      return problem(400, 'MSG01', {
        fieldErrors: [{ field: 'q', code: 'MSG01', params: { field_name: 'Search' } }],
      });
    if (q.length > 200)
      return problem(400, 'MSG02', {
        fieldErrors: [
          { field: 'q', code: 'MSG02', params: { field_name: 'Search', max_length: 200 } },
        ],
      });
    const viewable = await viewableSpaces();
    const results = publishedDocuments().filter(
      (document) =>
        (document.visibility === 'COMPANY_WIDE' ||
          viewable.some((space) => space.spaceId === document.spaceId)) &&
        `${document.title} ${document.content}`.toLowerCase().includes(q.trim().toLowerCase()),
    );
    return getResponse([getSearchWikiDocumentsMockHandler(results)], request);
  }),
  http.get('*/api/v1/wiki/documents/:documentId', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const document = documents.find((item) => item.documentId === params.documentId);
    if (!document) return problem(404, 'MSG110');
    const viewable = await viewableSpaces();
    const space = viewable.find((item) => item.spaceId === document.spaceId);
    const isTrashed = trashState.has(document.documentId);
    if (isTrashed && space?.accessLevel !== 'KNOWLEDGE_MANAGER') return problem(404, 'MSG110');
    if (document.visibility !== 'COMPANY_WIDE' && !space) return problem(404, 'MSG110');
    return getResponse(
      [
        getGetWikiDocumentMockHandler({
          ...document,
          canDelete: !isTrashed && space?.accessLevel === 'KNOWLEDGE_MANAGER',
        }),
      ],
      request,
    );
  }),
  http.delete('*/api/v1/wiki/documents/:documentId', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const document = publishedDocuments().find((item) => item.documentId === params.documentId);
    if (!document) return problem(404, 'MSG110');
    const error = await documentManagerError(document);
    if (error) return error;
    const me = await getMe();
    trashState.set(document.documentId, {
      deletedBy: me.user.fullName,
      deletedAt: new Date().toISOString(),
    });
    return getResponse([getTrashWikiDocumentMockHandler()], request);
  }),
  http.get('*/api/v1/wiki/spaces/:spaceId/trash', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const spaceId = String(params.spaceId);
    const error = await managerError(spaceId);
    if (error) return error;
    const rows: WikiTrashItem[] = documents
      .flatMap((document) => {
        const deleted = trashState.get(document.documentId);
        return document.spaceId === spaceId && deleted
          ? [
              {
                documentId: document.documentId,
                title: document.title,
                folderName: document.folderName,
                owner: document.owner,
                ...deleted,
              },
            ]
          : [];
      })
      .sort((a, b) => b.deletedAt.localeCompare(a.deletedAt));
    return getResponse([getListWikiTrashMockHandler(rows)], request);
  }),
  http.post('*/api/v1/wiki/documents/:documentId/restore', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const document = documents.find(
      (item) => item.documentId === params.documentId && trashState.has(item.documentId),
    );
    if (!document) return problem(404, 'MSG110');
    const error = await documentManagerError(document);
    if (error) return error;
    const titles = new Set(
      publishedDocuments()
        .filter((item) => item.spaceId === document.spaceId && item.folderId === document.folderId)
        .map((item) => item.title.toLowerCase()),
    );
    const original = document.title;
    let title = original;
    for (let number = 1; titles.has(title.toLowerCase()); number++) {
      const suffix = number === 1 ? ' (restored)' : ` (restored ${number})`;
      title = original.slice(0, 200 - suffix.length) + suffix;
    }
    document.title = title;
    document.updatedAt = new Date().toISOString();
    trashState.delete(document.documentId);
    return getResponse(
      [getRestoreWikiDocumentMockHandler({ title, renamed: title !== original })],
      request,
    );
  }),
  http.delete('*/api/v1/wiki/documents/:documentId/permanent', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const document = documents.find(
      (item) => item.documentId === params.documentId && trashState.has(item.documentId),
    );
    if (!document) return problem(404, 'MSG110');
    const error = await documentManagerError(document);
    if (error) return error;
    documents = documents.filter((item) => item.documentId !== document.documentId);
    trashState.delete(document.documentId);
    return getResponse([getDeleteWikiDocumentPermanentlyMockHandler()], request);
  }),
];

export function resetMockData(): void {
  trashState.clear();
  documents = initialDocuments.map((document) => ({ ...document }));
  folderState.clear();
  folderState.set(
    IT,
    folders.map((folder) => ({ ...folder })),
  );
  folderState.set(HR, []);
}
resetMockData();
