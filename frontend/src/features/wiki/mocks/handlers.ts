import { delay, getResponse, http, type HttpHandler } from 'msw';
import { getMe, type WikiDocumentDetail, type WikiFolder, type WikiSpace } from '@/shared/api';
import {
  getGetWikiDocumentMockHandler,
  getListCompanyWideDocumentsMockHandler,
  getListWikiDocumentsMockHandler,
  getListWikiFoldersMockHandler,
  getListWikiSpacesMockHandler,
  getSearchWikiDocumentsMockHandler,
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
const documents: WikiDocumentDetail[] = [
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
    return documents
      .filter((document) => document.visibility === 'COMPANY_WIDE')
      .sort((a, b) => a.spaceName.localeCompare(b.spaceName) || a.title.localeCompare(b.title));
  }),
  http.get('*/api/v1/wiki/spaces/:spaceId/folders', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const spaceId = String(params.spaceId);
    const error = await spaceError(spaceId);
    if (error) return error;
    return getResponse([getListWikiFoldersMockHandler(spaceId === IT ? folders : [])], request);
  }),
  http.get('*/api/v1/wiki/spaces/:spaceId/documents', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const spaceId = String(params.spaceId);
    const error = await spaceError(spaceId);
    if (error) return error;
    const folderId = new URL(request.url).searchParams.get('folderId');
    if (folderId && (spaceId !== IT || !folders.some((folder) => folder.folderId === folderId)))
      return problem(404, 'MSG04');
    return getResponse(
      [
        getListWikiDocumentsMockHandler(
          documents.filter(
            (document) => document.spaceId === spaceId && document.folderId === folderId,
          ),
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
    const results = documents.filter(
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
    if (document.visibility !== 'COMPANY_WIDE' && !space) return problem(404, 'MSG110');
    return getResponse(
      [
        getGetWikiDocumentMockHandler({
          ...document,
          canDelete: space?.accessLevel === 'KNOWLEDGE_MANAGER',
        }),
      ],
      request,
    );
  }),
];

/** Task 3 has no mutations or mutable mock state. */
export function resetMockData(): void {}
