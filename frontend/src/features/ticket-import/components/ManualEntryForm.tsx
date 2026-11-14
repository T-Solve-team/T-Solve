import { useQueryClient } from '@tanstack/react-query';
import { Button, DatePicker, Flex, Form, Input, Select, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useRef, useState, type ChangeEvent } from 'react';
import {
  getGetManualDraftQueryKey,
  getListImportDraftsQueryKey,
  useGetManualDraft,
  useSaveManualDraft,
  useSaveManualEntry,
  type ApiError,
  type DraftEvidenceResponse,
  type ManualDraftResponse,
  type ManualEntryRequest,
} from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { APP_TIME_ZONE } from '@/shared/lib';
import { msg, showMessage } from '@/shared/messages';
import { AppForm, ErrorState, FormField, LoadingState } from '@/shared/ui';
import { useManagedProjectOptions } from '../hooks/useManagedProjects';
import { SolutionFields } from './SolutionFields';

type ManualValues = {
  projectId?: string;
  ticketId?: string;
  title?: string;
  description?: string;
  resolvedDate?: Dayjs | null;
  symptoms?: string;
  rootCause?: string;
  resolutionSteps?: string;
  workaround?: string;
  scope?: string;
  /** The new Evidence files; the Draft's kept files are listed apart. */
  files?: File[];
};

const DATE_FORMAT = 'DD/MM/YYYY';
const MAX_FILES = 5;
const MAX_BYTES = 10 * 1024 * 1024;
/** Spec D18: the extension after the last dot, in any letter case. */
const TYPES = ['png', 'jpg', 'jpeg', 'pdf', 'docx', 'xlsx', 'txt', 'log'];
const ACCEPT = TYPES.map((type) => `.${type}`).join(',');

function isAllowed(file: File): boolean {
  const dot = file.name.lastIndexOf('.');
  const type = dot < 0 ? '' : file.name.slice(dot + 1).toLowerCase();
  return TYPES.includes(type) && file.size <= MAX_BYTES;
}

function vietnamToday(): string {
  return dayjs().tz(APP_TIME_ZONE).format('YYYY-MM-DD');
}

type ManualEntryTabProps = {
  /** The Manual Draft open on the tab, or null for a new entry. */
  draftId: number | null;
  onDraftChange: (draftId: number | null) => void;
};

/**
 * Manual entry tab of 5.1 Import Tickets — UC-22, spec STORY-006 and STORY-007: one resolved ticket with its
 * Solution and up to 5 Evidence files, saved as a Published Ticket or as a Draft. A Draft opened from the Drafts
 * list is read first, then the form starts from it (AC-007.4).
 */
export function ManualEntryTab({ draftId, onDraftChange }: ManualEntryTabProps) {
  const draft = useGetManualDraft(draftId ?? 0, { query: { enabled: draftId !== null } });
  if (draftId === null) return <ManualEntryForm key="new" onDraftChange={onDraftChange} />;
  if (draft.isPending) return <LoadingState />;
  if (draft.isError) return <ErrorState error={draft.error} onRetry={() => void draft.refetch()} />;
  return <ManualEntryForm key={draftId} draft={draft.data} onDraftChange={onDraftChange} />;
}

type ManualEntryFormProps = {
  draft?: ManualDraftResponse;
  onDraftChange: (draftId: number | null) => void;
};

function initialValues(draft: ManualDraftResponse | undefined): ManualValues {
  if (!draft) return { files: [] };
  const resolved = draft.resolvedDate ? dayjs(draft.resolvedDate, 'YYYY-MM-DD', true) : null;
  return {
    projectId: draft.projectId,
    ticketId: draft.ticketId ?? undefined,
    title: draft.title ?? undefined,
    description: draft.description ?? undefined,
    // a Draft keeps any text (edge 25); one that is not a date starts empty here
    resolvedDate: resolved?.isValid() ? resolved : null,
    files: [],
  };
}

function ManualEntryForm({ draft, onDraftChange }: ManualEntryFormProps) {
  const [form] = Form.useForm<ManualValues>();
  const queryClient = useQueryClient();
  const projects = useManagedProjectOptions();
  const [removedIds, setRemovedIds] = useState<ReadonlySet<string>>(new Set());
  const files = Form.useWatch('files', form) ?? [];
  const kept = (draft?.evidence ?? []).filter((file) => !removedIds.has(file.id));
  // A double click reaches the handler twice before `isPending` renders: one request only.
  const isSending = useRef(false);

  const save = useSaveManualEntry({ mutation: { meta: { handlesOwnErrors: true } } });
  const saveDraft = useSaveManualDraft({ mutation: { meta: { handlesOwnErrors: true } } });

  function setFilesError(keptCount: number, chosen: File[], rejected?: File) {
    let errors: string[] = [];
    if (keptCount + chosen.length > MAX_FILES) errors = [msg('MSG72')];
    else if (rejected) errors = [msg('MSG34', { file_name: rejected.name })];
    form.setFields([{ name: 'files', errors }]);
  }

  /** A file of a wrong type or over 10 MB is not added (MSG34); more than five in all is MSG72 (AC-006.12). */
  function addFiles(event: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(event.target.files ?? []);
    // the same file can be chosen again after it was removed
    event.target.value = '';
    const rejected = picked.find((file) => !isAllowed(file));
    const next = [...files, ...picked.filter(isAllowed)];
    form.setFieldValue('files', next);
    setFilesError(kept.length, next, rejected);
  }

  function removeFile(file: File) {
    const next = files.filter((chosen) => chosen !== file);
    form.setFieldValue('files', next);
    setFilesError(kept.length, next);
  }

  function removeKept(file: DraftEvidenceResponse) {
    setRemovedIds(new Set([...removedIds, file.id]));
    setFilesError(kept.length - 1, files);
  }

  function entry(values: ManualValues): ManualEntryRequest {
    return {
      projectId: values.projectId,
      ticketId: values.ticketId,
      title: values.title,
      description: values.description,
      resolvedDate: values.resolvedDate ? values.resolvedDate.format('YYYY-MM-DD') : null,
      symptoms: values.symptoms,
      rootCause: values.rootCause,
      resolutionSteps: values.resolutionSteps,
      workaround: values.workaround,
      scope: values.scope,
      // every request on a Draft names the files it keeps (AC-007.5)
      ...(draft ? { draftId: draft.previewId, keepEvidenceIds: kept.map((file) => file.id) } : {}),
    };
  }

  function handleError(error: ApiError) {
    // The global MSG07 dialog is the one message for a 401 (guideline 06 §6).
    if (error.status === 401) return;
    if (applyApiErrors(form, error)) return;
    showMessage(error.code, error.params);
    // the Draft was saved as a Ticket or deleted elsewhere: back to a new entry (edge 23)
    if (error.status === 404 && draft) {
      void queryClient.invalidateQueries({ queryKey: getListImportDraftsQueryKey() });
      onDraftChange(null);
    }
  }

  function send(kind: 'ticket' | 'draft') {
    if (isSending.current) return;
    const values = form.getFieldsValue(true) as ManualValues;
    const body = { entry: entry(values), files: values.files ?? [] };
    isSending.current = true;
    const settled = () => {
      isSending.current = false;
    };
    if (kind === 'ticket') {
      save.mutate(
        { data: body },
        {
          onSettled: settled,
          onError: handleError,
          onSuccess: (response) => {
            showMessage('MSG32', response.message.params);
            void queryClient.invalidateQueries({ queryKey: getListImportDraftsQueryKey() });
            form.resetFields();
            setRemovedIds(new Set());
            onDraftChange(null);
          },
        },
      );
    } else {
      saveDraft.mutate(
        { data: body },
        {
          onSettled: settled,
          onError: handleError,
          onSuccess: (response) => {
            showMessage('MSG41');
            void queryClient.invalidateQueries({ queryKey: getListImportDraftsQueryKey() });
            void queryClient.invalidateQueries({
              queryKey: getGetManualDraftQueryKey(response.previewId),
            });
            // the files sent are the Draft's files now
            form.setFieldValue('files', []);
            setRemovedIds(new Set());
            onDraftChange(response.previewId);
          },
        },
      );
    }
  }

  function handleSaveAsDraft() {
    // a Draft checks only its Project and its files (AC-007.2)
    form
      .validateFields(['projectId', 'files'])
      .then(() => send('draft'))
      .catch(() => undefined);
  }

  const disabledDate = (date: Dayjs) => date.format('YYYY-MM-DD') > vietnamToday();
  const isPending = save.isPending || saveDraft.isPending;

  return (
    <AppForm<ManualValues>
      name="manualEntry"
      form={form}
      initialValues={initialValues(draft)}
      onFinish={() => send('ticket')}
    >
      <FormField<ManualValues> name="projectId" label="Project" rules={[rules.required('Project')]}>
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
        rules={[rules.required('Description'), rules.maxLength('Description', 10_000)]}
      >
        <Input.TextArea rows={4} count={{ show: true, max: 10_000 }} />
      </FormField>
      <FormField<ManualValues> name="resolvedDate" label="Resolved date">
        <DatePicker format={DATE_FORMAT} disabledDate={disabledDate} />
      </FormField>
      <SolutionFields values={draft} />
      {/* checked on submit only: choosing and removing files sets the message itself */}
      <FormField<ManualValues>
        name="files"
        label="Evidence"
        validateTrigger={[]}
        rules={[
          {
            validator: () =>
              kept.length + files.length > MAX_FILES
                ? Promise.reject(new Error(msg('MSG72')))
                : Promise.resolve(),
          },
        ]}
      >
        <EvidencePicker
          kept={kept}
          files={files}
          onAdd={addFiles}
          onRemove={removeFile}
          onRemoveKept={removeKept}
        />
      </FormField>
      <Flex gap="small">
        <Button type="primary" htmlType="submit" loading={save.isPending} disabled={isPending}>
          Save
        </Button>
        <Button onClick={handleSaveAsDraft} loading={saveDraft.isPending} disabled={isPending}>
          Save as draft
        </Button>
      </Flex>
    </AppForm>
  );
}

type EvidencePickerProps = {
  /** Set by the form field, so its label names the file input. */
  id?: string;
  kept: DraftEvidenceResponse[];
  files: File[];
  onAdd: (event: ChangeEvent<HTMLInputElement>) => void;
  onRemove: (file: File) => void;
  onRemoveKept: (file: DraftEvidenceResponse) => void;
};

/** The native file input: the kit has no multi-file upload yet, and raw antd Upload is banned (guideline 07). */
function EvidencePicker({ id, kept, files, onAdd, onRemove, onRemoveKept }: EvidencePickerProps) {
  return (
    <Flex vertical gap="small">
      <input id={id} type="file" multiple accept={ACCEPT} onChange={onAdd} />
      {kept.map((file) => (
        <Flex key={file.id} gap="small" align="center">
          <Typography.Text>{file.fileName}</Typography.Text>
          <Button
            type="link"
            aria-label={`Remove ${file.fileName}`}
            onClick={() => onRemoveKept(file)}
          >
            Remove
          </Button>
        </Flex>
      ))}
      {files.map((file, index) => (
        <Flex key={`${file.name}-${index}`} gap="small" align="center">
          <Typography.Text>{file.name}</Typography.Text>
          <Button type="link" aria-label={`Remove ${file.name}`} onClick={() => onRemove(file)}>
            Remove
          </Button>
        </Flex>
      ))}
    </Flex>
  );
}
