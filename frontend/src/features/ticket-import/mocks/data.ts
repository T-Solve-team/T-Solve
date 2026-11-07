import type {
  ImportPreviewResponse,
  ImportPreviewRowPage,
  ImportPreviewRowResponse,
  ImportPreviewRowSummary,
} from '@/shared/api';
import { PAGE_SIZE } from '@/shared/lib';

/** The template the backend returns (CsvColumns.TEMPLATE): the header row only. */
export const CSV_TEMPLATE =
  'ticket_id,title,description,author,resolved_date,symptoms,root_cause,resolution_steps,workaround,scope\n';

/** Shown only by the Import Ticket Preview popup: the rows list has no Description. */
const DESCRIPTION = 'Users report the problem after the latest Windows update.';

const SOLUTION = {
  symptoms: 'Outlook asks for the password again every few minutes after the M365 migration.',
  rootCause: 'The old Basic authentication profile was kept after the mailbox moved to M365.',
  resolutionSteps:
    '1. Remove the Outlook profile. 2. Clear the cached credentials. 3. Create the profile again.',
  workaround: 'Use Outlook on the web until the profile is recreated.',
  scope: 'Windows laptops with Outlook 2019 or later.',
};

/**
 * 25 rows: Valid, Error (missing section, duplicate id with MSG_NEW_3, existing id) and Removed rows first, then
 * Valid ones, so the list has two pages.
 */
function seedRows(): ImportPreviewRowResponse[] {
  const valid = Array.from({ length: 19 }, (_, i): ImportPreviewRowResponse => {
    const rowNo = 7 + i;
    return {
      rowNo,
      ticketId: `ITSUP-09${String(rowNo).padStart(2, '0')}`,
      title: `Teams meeting audio issue on laptop batch ${rowNo}`,
      author: 'Phạm Quốc Bảo',
      resolvedDate: '2026-09-28',
      description: DESCRIPTION,
      ...SOLUTION,
      status: 'VALID',
      errors: [],
    };
  });
  return [
    {
      rowNo: 1,
      ticketId: 'ITSUP-0871',
      title: 'Outlook keeps asking for password after M365 migration',
      author: 'Nguyễn Thị Thu Hà',
      resolvedDate: '2026-09-12',
      description: DESCRIPTION,
      ...SOLUTION,
      status: 'VALID',
      errors: [],
    },
    {
      rowNo: 2,
      ticketId: 'ITSUP-0872',
      title: 'VPN drops during Teams calls',
      author: 'Phạm Quốc Bảo',
      resolvedDate: '2026-09-14',
      description: DESCRIPTION,
      ...SOLUTION,
      rootCause: null,
      status: 'ERROR',
      errors: [{ code: 'MSG01', field: 'root_cause', params: { field_name: 'Root cause' } }],
    },
    {
      rowNo: 3,
      ticketId: 'ITSUP-0880',
      title: 'Printer on floor 7 shows offline',
      author: 'Lê Văn Tùng',
      resolvedDate: '2026-09-20',
      description: DESCRIPTION,
      ...SOLUTION,
      status: 'ERROR',
      errors: [{ code: 'MSG_NEW_3', field: 'ticket_id', params: { ticket_id: 'ITSUP-0880' } }],
    },
    {
      rowNo: 4,
      ticketId: 'ITSUP-0880',
      title: 'Printer on floor 7 shows offline (again)',
      author: 'Lê Văn Tùng',
      resolvedDate: '2026-09-21',
      description: DESCRIPTION,
      ...SOLUTION,
      status: 'ERROR',
      errors: [{ code: 'MSG_NEW_3', field: 'ticket_id', params: { ticket_id: 'ITSUP-0880' } }],
    },
    {
      rowNo: 5,
      ticketId: 'ITSUP-0715',
      title: 'Shared drive mapping lost after password change',
      author: 'Trần Thị Lan',
      resolvedDate: '2026-08-30',
      description: DESCRIPTION,
      ...SOLUTION,
      status: 'ERROR',
      errors: [{ code: 'MSG31', field: 'ticket_id', params: { ticket_id: 'ITSUP-0715' } }],
    },
    {
      rowNo: 6,
      ticketId: 'ITSUP-0890',
      title: 'Laptop camera not detected in Teams',
      author: 'Ngô Thị Mai',
      resolvedDate: '2026-09-25',
      description: DESCRIPTION,
      ...SOLUTION,
      status: 'REMOVED',
      errors: [],
    },
    ...valid,
  ];
}

/** One unconfirmed preview of the mock backend with every row (the API returns them page by page). */
export type MockPreview = Pick<
  ImportPreviewResponse,
  'previewId' | 'projectId' | 'fileName' | 'importType'
> & {
  rows: ImportPreviewRowResponse[];
};

export function seedImport(
  previewId = 1,
  projectId = '00000000-0000-4000-8000-000000000101',
  fileName = 'itsup-resolved-2026-09.csv',
): MockPreview {
  return { previewId, projectId, fileName, importType: 'WITH_SOLUTION', rows: seedRows() };
}

/** One page of rows (`page` from 0), as `GET /imports/previews/{id}/rows` returns it. */
export function rowPage(
  rows: ImportPreviewRowSummary[],
  page = 0,
  size = PAGE_SIZE,
): ImportPreviewRowPage {
  return {
    content: rows.slice(page * size, page * size + size),
    page: {
      size,
      number: page,
      totalElements: rows.length,
      totalPages: Math.ceil(rows.length / size),
    },
  };
}

/** Counts, MSG30 and the first page, as upload and `GET /imports/previews/{id}` return them. */
export function previewResponse({ rows, ...preview }: MockPreview): ImportPreviewResponse {
  const count = (status: ImportPreviewRowSummary['status']) =>
    rows.filter((row) => row.status === status).length;
  const errorCount = count('ERROR');
  return {
    ...preview,
    validCount: count('VALID'),
    errorCount,
    removedCount: count('REMOVED'),
    needsSolutionCount: count('NEEDS_SOLUTION'),
    message: errorCount > 0 ? { code: 'MSG30', params: { count: errorCount } } : null,
    rows: rowPage(rows),
  };
}

/** The seed preview as the API returns it. */
export function seedPreview(): ImportPreviewResponse {
  return previewResponse(seedImport());
}
