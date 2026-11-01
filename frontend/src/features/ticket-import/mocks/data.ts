import type { ImportPreviewResponse, ImportPreviewRowSummary } from '@/shared/api';

/** The template the backend returns (CsvColumns.TEMPLATE): the header row only. */
export const CSV_TEMPLATE =
  'ticket_id,title,description,author,resolved_date,symptoms,root_cause,resolution_steps,workaround,scope\n';

const SOLUTION = {
  symptoms: 'Outlook asks for the password again every few minutes after the M365 migration.',
  rootCause: 'The old Basic authentication profile was kept after the mailbox moved to M365.',
  resolutionSteps:
    '1. Remove the Outlook profile. 2. Clear the cached credentials. 3. Create the profile again.',
  workaround: 'Use Outlook on the web until the profile is recreated.',
  scope: 'Windows laptops with Outlook 2019 or later.',
};

/** Valid, Error (missing section, duplicate id with MSG_NEW_3, existing id) and Removed rows. */
function seedRows(): ImportPreviewRowSummary[] {
  return [
    {
      rowNo: 1,
      ticketId: 'ITSUP-0871',
      title: 'Outlook keeps asking for password after M365 migration',
      author: 'Nguyễn Thị Thu Hà',
      resolvedDate: '2026-09-12',
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
      ...SOLUTION,
      status: 'REMOVED',
      errors: [],
    },
  ];
}

/** The PM's unconfirmed preview of the mock backend; an upload replaces it (spec D9). */
export function seedPreview(
  previewId = 1,
  projectId = '00000000-0000-4000-8000-000000000101',
  fileName = 'itsup-resolved-2026-09.csv',
): ImportPreviewResponse {
  const rows = seedRows();
  const count = (status: ImportPreviewRowSummary['status']) =>
    rows.filter((row) => row.status === status).length;
  const errorCount = count('ERROR');
  return {
    previewId,
    projectId,
    fileName,
    validCount: count('VALID'),
    errorCount,
    removedCount: count('REMOVED'),
    message: errorCount > 0 ? { code: 'MSG30', params: { count: errorCount } } : null,
    rows: {
      content: rows,
      page: { size: 20, number: 0, totalElements: rows.length, totalPages: 1 },
    },
  };
}
