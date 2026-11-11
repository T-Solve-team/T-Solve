/** 5.1 Import Tickets — SRS III.5.1 */
import { Flex, Tabs } from 'antd';
import { SectionCard } from '@/shared/ui';
import { CsvImportForm } from '../components/CsvImportForm';
import { DraftsList } from '../components/DraftsList';
import { JiraPullForm } from '../components/JiraPullForm';

/** Jira (KAN-45) and CSV work; Manual entry is shown disabled until UC-22 (spec D13 of import-ticket-list). */
const TABS = [
  { key: 'jira', label: 'Jira', children: <JiraPullForm /> },
  { key: 'csv', label: 'CSV', children: <CsvImportForm /> },
  { key: 'manual', label: 'Manual entry', disabled: true },
];

export function ImportTicketsPage() {
  return (
    <Flex vertical gap="middle">
      <SectionCard>
        <Tabs defaultActiveKey="csv" items={TABS} />
      </SectionCard>
      <SectionCard>
        <DraftsList />
      </SectionCard>
    </Flex>
  );
}

export default ImportTicketsPage;
