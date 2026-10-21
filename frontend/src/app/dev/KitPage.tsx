/** DEV ONLY — every Figma kit component on one page (guideline 07 §3), to compare with the Figma section. */
import { Button, Flex, Form, Input, Select } from 'antd';
import { useState } from 'react';
import { rules } from '@/shared/forms';
import {
  AppForm,
  cells,
  ConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  FormModal,
  LoadingState,
  SectionCard,
  StatCard,
  STATUS_TAGS,
  StatusTag,
  type DataTableColumn,
  type StatusTagStatus,
} from '@/shared/ui';

type SampleRow = { id: number; key: string; title: string; status: StatusTagStatus; at: string };
const SAMPLE_ROWS: SampleRow[] = [
  {
    id: 1,
    key: 'ITSUP-1031',
    title: 'Outlook keeps asking for password after M365 migration',
    status: 'PENDING_REVIEW',
    at: '2026-10-01T11:35:00Z',
  },
  {
    id: 2,
    key: 'ITNET-0415',
    title: 'Meeting room 7A display does not detect laptops',
    status: 'PUBLISHED',
    at: '2026-10-02T10:40:00Z',
  },
  {
    id: 3,
    key: 'HRHELP-0318',
    title: 'Payslip PDF shows wrong overtime hours',
    status: 'REJECTED',
    at: '2026-10-03T01:15:00Z',
  },
];
const SAMPLE_COLUMNS: DataTableColumn<SampleRow>[] = [
  {
    key: 'key',
    title: 'Ticket ID',
    width: 140,
    sortable: true,
    render: (_, r) => cells.link('#', r.key),
  },
  { key: 'title', title: 'Title', render: (_, r) => cells.text(r.title) },
  { key: 'status', title: 'Status', width: 170, render: (_, r) => cells.tag(r.status) },
  { key: 'at', title: 'Updated', width: 160, render: (_, r) => cells.dateTime(r.at) },
];

type DemoForm = { reason: string; department?: string };

export function KitPage() {
  const [dialog, setDialog] = useState<'confirm' | 'danger' | 'form' | 'formDanger' | null>(null);
  const [form] = Form.useForm<DemoForm>();
  const close = () => setDialog(null);

  return (
    <Flex vertical gap="large">
      <SectionCard title="Web/Status Tag">
        <Flex wrap gap="small">
          {(Object.keys(STATUS_TAGS) as StatusTagStatus[]).map((status) => (
            <StatusTag key={status} status={status} />
          ))}
        </Flex>
      </SectionCard>

      <SectionCard title="Web/Button (antd Button)">
        <Flex wrap gap="middle">
          <Button type="primary">Primary</Button>
          <Button>Default</Button>
          <Button type="text">Text</Button>
          <Button type="link">Link</Button>
          <Button type="primary" danger>
            Danger
          </Button>
          <Button type="primary" disabled>
            Disabled
          </Button>
          <Button type="primary" loading>
            Loading
          </Button>
        </Flex>
      </SectionCard>

      <SectionCard title="Web/Table + Web/Pagination (DataTable)">
        <DataTable<SampleRow>
          label="Sample tickets"
          columns={SAMPLE_COLUMNS}
          rows={SAMPLE_ROWS}
          rowKey={(r) => r.id}
          pagination={{ page: 1, total: SAMPLE_ROWS.length, onChange: () => undefined }}
          sort={{ field: 'key', order: 'asc' }}
          rowSelection={{}}
        />
      </SectionCard>

      <SectionCard title="Web/Empty & Loading (EmptyState, LoadingState, ErrorState)">
        <Flex gap="large">
          <EmptyState />
          <EmptyState code="MSG13" />
          <LoadingState />
          <ErrorState onRetry={() => undefined} />
        </Flex>
      </SectionCard>

      <SectionCard title="Web/Card (SectionCard, StatCard)">
        <Flex gap="middle">
          <StatCard label="Pending review" value={128} />
          <StatCard label="Published" value={342} />
        </Flex>
      </SectionCard>

      <SectionCard title="Web/Form Field (AppForm, FormField, shared/forms rules)">
        <AppForm<DemoForm> form={form}>
          <FormField<DemoForm> name="department" label="Department">
            <Select placeholder="Select…" options={[{ value: 'IT', label: 'IT' }]} />
          </FormField>
          <FormField<DemoForm>
            name="reason"
            label="Reason"
            rules={[rules.required('Reason'), rules.maxLength('Reason', 1000)]}
          >
            <Input.TextArea rows={3} count={{ show: true, max: 1000 }} />
          </FormField>
          <Button onClick={() => void form.validateFields().catch(() => undefined)}>
            Validate
          </Button>
        </AppForm>
      </SectionCard>

      <SectionCard title="Web/Modal (ConfirmDialog, FormModal)">
        <Flex wrap gap="middle">
          <Button onClick={() => setDialog('confirm')}>Confirm (MSG90)</Button>
          <Button onClick={() => setDialog('danger')}>Danger confirm (MSG29)</Button>
          <Button onClick={() => setDialog('form')}>Form popup</Button>
          <Button onClick={() => setDialog('formDanger')}>Danger form popup</Button>
        </Flex>
      </SectionCard>

      <ConfirmDialog
        isOpen={dialog === 'confirm'}
        code="MSG90"
        params={{ count: 2 }}
        onConfirm={close}
        onCancel={close}
      />
      <ConfirmDialog
        isOpen={dialog === 'danger'}
        code="MSG29"
        isDanger
        okText="Unpublish"
        onConfirm={close}
        onCancel={close}
      />
      <FormModal<DemoForm>
        isOpen={dialog === 'form' || dialog === 'formDanger'}
        title={dialog === 'formDanger' ? 'Reject Ticket' : 'Request Changes'}
        kind={dialog === 'formDanger' ? 'danger' : 'form'}
        width={600}
        form={form}
        okText={dialog === 'formDanger' ? 'Reject' : 'Send'}
        isOkDanger={dialog === 'formDanger'}
        onSubmit={close}
        onCancel={close}
      >
        <FormField<DemoForm> name="reason" label="Reason" rules={[rules.required('Reason')]}>
          <Input.TextArea rows={3} />
        </FormField>
      </FormModal>
    </Flex>
  );
}

export default KitPage;
