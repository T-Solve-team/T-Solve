/** 5.1 Import Tickets — SRS III.5.1 */
import { Flex, Tabs } from 'antd';
import { useSearchParams } from 'react-router';
import { SectionCard } from '@/shared/ui';
import { CsvImportForm } from '../components/CsvImportForm';
import { DraftsList } from '../components/DraftsList';
import { JiraPullForm } from '../components/JiraPullForm';
import { ManualEntryForm } from '../components/ManualEntryForm';

const TABS = [
  { key: 'jira', label: 'Jira', children: <JiraPullForm /> },
  { key: 'csv', label: 'CSV', children: <CsvImportForm /> },
  { key: 'manual', label: 'Manual entry', children: <ManualEntryForm /> },
];

/** The tab comes from `?tab=` (a Manual Draft opens `?tab=manual&draftId=N`, AC-006.11); CSV by default. */
export function ImportTicketsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab');

  return (
    <Flex vertical gap="middle">
      <SectionCard>
        <Tabs
          activeKey={TABS.find(({ key }) => key === tab)?.key ?? 'csv'}
          onChange={(tab) =>
            setSearchParams(
              (params) => {
                params.set('tab', tab);
                return params;
              },
              { replace: true },
            )
          }
          items={TABS}
        />
      </SectionCard>
      <SectionCard>
        <DraftsList />
      </SectionCard>
    </Flex>
  );
}

export default ImportTicketsPage;
