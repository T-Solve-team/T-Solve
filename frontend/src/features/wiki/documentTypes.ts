import { WikiDocumentType } from '@/shared/api';

export const documentTypeLabels: Record<WikiDocumentType, string> = {
  POLICY: 'Policy',
  RUNBOOK: 'Runbook',
  GUIDE: 'Guide',
  FAQ: 'FAQ',
  REFERENCE: 'Reference',
  SPEC: 'Spec',
  RECORD: 'Record',
  ANNOUNCEMENT: 'Announcement',
  FORM: 'Form',
  OTHER: 'Other',
};

export const documentTypeOptions = Object.values(WikiDocumentType).map((value) => ({
  value,
  label: documentTypeLabels[value],
}));
