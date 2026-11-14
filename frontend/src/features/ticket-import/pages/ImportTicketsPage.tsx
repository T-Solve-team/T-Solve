/** 5.1 Import Tickets — SRS III.5.1 */
import { Flex, Tabs } from 'antd';
import { useState } from 'react';
import { SectionCard } from '@/shared/ui';
import { CsvImportForm } from '../components/CsvImportForm';
import { DraftsList } from '../components/DraftsList';
import { JiraPullForm } from '../components/JiraPullForm';
import { ManualEntryTab } from '../components/ManualEntryForm';

/** Jira (KAN-45), CSV and Manual entry (KAN-133); Open on a Manual Draft shows it on the Manual entry tab. */
export function ImportTicketsPage() {
  const [activeTab, setActiveTab] = useState('csv');
  const [manualDraftId, setManualDraftId] = useState<number | null>(null);

  const tabs = [
    { key: 'jira', label: 'Jira', children: <JiraPullForm /> },
    { key: 'csv', label: 'CSV', children: <CsvImportForm /> },
    {
      key: 'manual',
      label: 'Manual entry',
      children: <ManualEntryTab draftId={manualDraftId} onDraftChange={setManualDraftId} />,
    },
  ];

  return (
    <Flex vertical gap="middle">
      <SectionCard>
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabs} />
      </SectionCard>
      <SectionCard>
        <DraftsList
          onOpenManual={(previewId) => {
            setManualDraftId(previewId);
            setActiveTab('manual');
          }}
        />
      </SectionCard>
    </Flex>
  );
}

export default ImportTicketsPage;
