import { useQueryClient } from '@tanstack/react-query';
import { Alert, Button, DatePicker, Flex, Form, Input, Select, type FormInstance } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import {
  getGetManualTicketDraftQueryKey,
  getListImportDraftsQueryKey,
  useCreateManualTicket,
  useGetManualTicketDraft,
  useSaveManualTicketDraft,
  type ApiError,
  type ManualTicketDraft,
  type ManualTicketRequest,
} from '@/shared/api';
import { rules } from '@/shared/forms';
import { APP_TIME_ZONE } from '@/shared/lib';
import { msg, showMessage } from '@/shared/messages';
import { AppForm, ErrorState, FormField, LoadingState } from '@/shared/ui';
import { useManagedProjectOptions } from '../hooks/useManagedProjects';
import { SolutionFields } from './SolutionFormModal';

type ManualValues = Omit<ManualTicketRequest, 'resolvedDate' | 'draftId'> & {
  resolvedDate?: Dayjs | null;
};

const DATE_FORMAT = 'DD/MM/YYYY';

const isFuture = (date: Dayjs) =>
  date.format('YYYY-MM-DD') > dayjs().tz(APP_TIME_ZONE).format('YYYY-MM-DD');

/**
 * Manual entry tab of 5.1 Import Tickets — UC-22, STORY-006. With `?draftId=N` it first loads that Manual Draft
 * (AC-006.11); the form is mounted again for each Draft so it starts from the Draft's fields.
 */
export function ManualEntryForm() {
  const [searchParams] = useSearchParams();
  const draftId = Number(searchParams.get('draftId')) || null;
  const draft = useGetManualTicketDraft(draftId ?? 0, { query: { enabled: draftId !== null } });

  if (draftId === null) return <ManualEntryFields />;
  if (draft.isPending) return <LoadingState />;
  if (draft.isError && !draft.data)
    return <ErrorState error={draft.error} onRetry={() => void draft.refetch()} />;
  return <ManualEntryFields key={draftId} draft={draft.data} />;
}

function toValues(draft: ManualTicketDraft): ManualValues {
  const resolved = draft.resolvedDate ? dayjs(draft.resolvedDate) : null;
  return {
    projectId: draft.projectId,
    ticketId: draft.ticketId,
    title: draft.title,
    description: draft.description,
    resolvedDate: resolved?.isValid() ? resolved : null,
  };
}

/** Server field errors under their fields; `solution` (the total Solution length) under the first section. */
function showFieldErrors(form: FormInstance<ManualValues>, error: ApiError): string[] {
  const byField = new Map<string, string[]>();
  const unhandled: string[] = [];
  const fields = form.getFieldsValue();
  for (const fieldError of error.fieldErrors) {
    const name = fieldError.field === 'solution' ? 'symptoms' : fieldError.field;
    const message = msg(fieldError.code, fieldError.params);
    if (Object.hasOwn(fields, name)) {
      byField.set(name, [...(byField.get(name) ?? []), message]);
    } else {
      unhandled.push(message);
    }
  }
  form.setFields(
    [...byField].map(([name, errors]) => ({ name: name as keyof ManualValues, errors })),
  );
  return unhandled;
}

/**
 * Project *, Ticket ID *, Title *, Description *, Resolved date and the Solution sections; Save publishes the
 * Ticket (MSG32, form cleared), Save as draft keeps it as a Manual Draft (MSG41). A rejection shows each message
 * at its field and keeps every value (AC-006.14).
 */
function ManualEntryFields({ draft }: { draft?: ManualTicketDraft }) {
  const [form] = Form.useForm<ManualValues>();
  const [, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const projects = useManagedProjectOptions();
  const [draftId, setDraftId] = useState<number | null>(draft?.previewId ?? null);
  const [rejection, setRejection] = useState<string | null>(null);
  // A double click reaches the handler twice before `isPending` renders: one request only.
  const isSending = useRef(false);

  const create = useCreateManualTicket({ mutation: { meta: { handlesOwnErrors: true } } });
  const saveDraft = useSaveManualTicketDraft({ mutation: { meta: { handlesOwnErrors: true } } });

  const refreshDrafts = () =>
    queryClient.invalidateQueries({ queryKey: getListImportDraftsQueryKey() });

  function request(values: ManualValues): ManualTicketRequest {
    return {
      ...values,
      resolvedDate: values.resolvedDate?.format('YYYY-MM-DD') ?? null,
      draftId,
    };
  }

  function onError(error: ApiError) {
    // The global MSG07 dialog is the one message for a 401 (guideline 06 §6).
    if (error.status === 401) return;
    const unhandled = showFieldErrors(form, error);
    if (error.fieldErrors.length === 0) unhandled.push(msg(error.code, error.params));
    setRejection(unhandled.join(' ') || null);
  }

  function send(mutate: () => void) {
    if (isSending.current) return;
    isSending.current = true;
    setRejection(null);
    mutate();
  }

  function handleSave(values: ManualValues) {
    send(() =>
      create.mutate(
        { data: { entry: request(values), files: [] } },
        {
          onSettled: () => {
            isSending.current = false;
          },
          onSuccess: ({ created, skipped }) => {
            showMessage('MSG32', { created, skipped });
            if (draftId !== null) {
              queryClient.removeQueries({ queryKey: getGetManualTicketDraftQueryKey(draftId) });
            }
            form.resetFields();
            setDraftId(null);
            // A reopened Draft is deleted with the save (AC-006.12); a changed draftId mounts an empty form.
            setSearchParams(
              (params) => {
                params.delete('draftId');
                return params;
              },
              { replace: true },
            );
            void refreshDrafts();
          },
          onError,
        },
      ),
    );
  }

  async function handleSaveDraft() {
    try {
      await form.validateFields(['projectId']);
    } catch {
      return;
    }
    send(() =>
      saveDraft.mutate(
        { data: { entry: request(form.getFieldsValue()), files: [] } },
        {
          onSettled: () => {
            isSending.current = false;
          },
          onSuccess: (saved) => {
            showMessage('MSG41');
            queryClient.removeQueries({
              queryKey: getGetManualTicketDraftQueryKey(saved.previewId),
            });
            setDraftId(saved.previewId);
            void refreshDrafts();
          },
          onError,
        },
      ),
    );
  }

  return (
    <Flex vertical gap="middle">
      {rejection && <Alert type="error" showIcon title={rejection} />}
      {/* name: field ids differ from the other tabs' (label → input). */}
      <AppForm<ManualValues>
        name="manualEntry"
        form={form}
        initialValues={draft && toValues(draft)}
        onFinish={handleSave}
      >
        <FormField<ManualValues>
          name="projectId"
          label="Project"
          rules={[rules.required('Project')]}
        >
          <Select options={projects} virtual={false} />
        </FormField>
        <FormField<ManualValues>
          name="ticketId"
          label="Ticket ID"
          rules={[rules.required('Ticket ID'), rules.maxLength('Ticket ID', 50)]}
        >
          <Input count={{ show: true, max: 50 }} />
        </FormField>
        <FormField<ManualValues>
          name="title"
          label="Title"
          rules={[rules.required('Title'), rules.maxLength('Title', 200)]}
        >
          <Input count={{ show: true, max: 200 }} />
        </FormField>
        <FormField<ManualValues>
          name="description"
          label="Description"
          rules={[rules.required('Description'), rules.maxLength('Description', 10000)]}
        >
          <Input.TextArea rows={4} count={{ show: true, max: 10000 }} />
        </FormField>
        <FormField<ManualValues> name="resolvedDate" label="Resolved date">
          <DatePicker format={DATE_FORMAT} disabledDate={isFuture} />
        </FormField>
        <SolutionFields values={draft} />
        <Flex gap="small">
          <Button type="primary" htmlType="submit" loading={create.isPending}>
            Save
          </Button>
          <Button loading={saveDraft.isPending} onClick={() => void handleSaveDraft()}>
            Save as draft
          </Button>
        </Flex>
      </AppForm>
    </Flex>
  );
}
