/** 5.1 Import Tickets — SRS III.5.1 */
import { Tabs } from 'antd';
import { SectionCard } from '@/shared/ui';
import { CsvImportForm } from '../components/CsvImportForm';

/** Only the CSV tab works for now; Jira and Manual entry are shown disabled (spec D13). */
const TABS = [
  { key: 'jira', label: 'Jira', disabled: true },
  { key: 'csv', label: 'CSV', children: <CsvImportForm /> },
  { key: 'manual', label: 'Manual entry', disabled: true },
];

export function ImportTicketsPage() {
  return (
    <SectionCard>
      <Tabs activeKey="csv" items={TABS} />
    </SectionCard>
  );
}

export default ImportTicketsPage;
