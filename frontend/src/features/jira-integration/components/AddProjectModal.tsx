import { Alert, Flex, Form, Input, InputNumber, Select, type FormRule } from 'antd';
import { useRef } from 'react';
import type { ApiError, TrackerStatus } from '@/shared/api';
import { applyApiErrors, rules } from '@/shared/forms';
import { msg, showMessage } from '@/shared/messages';
import { FormField, FormModal } from '@/shared/ui';
import { useAddProject, useProjectConnectionTest } from '../hooks/useProjectMutations';

/** Field names are the API property names; the settings exist only after a successful test. */
type AddProjectValues = { jiraProjectKey: string; doneStatusIds?: string[]; reviewRange?: number };

const KEY_MAX = 20;
/** The `INT` column's maximum (AC-006.11). */
const RANGE_MAX = 2_147_483_647;

/** MSG02 by UTF-16 code units as typed (AC-006.3); antd's own `max` counts code points. */
const keyLength: FormRule = {
  validator: (_rule, value: unknown) =>
    typeof value === 'string' && value.length > KEY_MAX
      ? Promise.reject(new Error(msg('MSG02', { field_name: 'Project key', max_length: KEY_MAX })))
      : Promise.resolve(),
};

/** MSG44 above the `INT` maximum; `rules.wholeNumberMin(1)` covers the rest of AC-006.11. */
const rangeMax: FormRule = {
  validator: (_rule, value: unknown) =>
    typeof value === 'number' && value > RANGE_MAX
      ? Promise.reject(new Error(msg('MSG44')))
      : Promise.resolve(),
};

/** Value = status id, label = name; statuses that share a name show `name (id)`. */
function statusOptions(statuses: TrackerStatus[]) {
  return statuses.map((s) => ({
    value: s.id,
    label: statuses.filter((o) => o.name === s.name).length > 1 ? `${s.name} (${s.id})` : s.name,
  }));
}

type AddProjectModalProps = { isOpen: boolean; onClose: () => void };

/**
 * 3.1 Add project (popup) — spec STORY-006, D30/D37/D40. The primary action is Test connection until a test
 * succeeds, then Save; editing the key discards the tested result. While a request runs the popup is locked.
 */
export function AddProjectModal({ isOpen, onClose }: AddProjectModalProps) {
  const [form] = Form.useForm<AddProjectValues>();
  const test = useProjectConnectionTest();
  const save = useAddProject();
  const submitting = useRef(false);
  const tested = test.data;
  const isBusy = test.isPending || save.isPending;
  const inlineError = [test.error, save.error].find(
    (e) => e?.code === 'MSG48' && e.fieldErrors.length === 0,
  );

  function discardResults() {
    test.reset();
    save.reset();
  }

  function close() {
    discardResults();
    onClose();
  }

  /** Field errors under fields, MSG48 inline, anything else by code; 401 is the global handler's. */
  function showError(error: ApiError) {
    if (applyApiErrors(form, error) || error.code === 'MSG48' || error.status === 401) return;
    showMessage(error.code, error.params);
  }

  function handleSubmit({ jiraProjectKey, doneStatusIds, reviewRange }: AddProjectValues) {
    if (submitting.current) return;
    const callbacks = {
      onError: showError,
      onSettled: () => {
        submitting.current = false;
      },
    };
    if (!tested) {
      submitting.current = true;
      test.mutate(
        { data: { jiraProjectKey } },
        {
          ...callbacks,
          onSuccess: (result) =>
            form.setFieldsValue({
              doneStatusIds: result.doneStatusIds,
              reviewRange: result.reviewRange,
            }),
        },
      );
      return;
    }
    // The settings rules have passed, so both are set; the check only narrows the types.
    if (!doneStatusIds || reviewRange === undefined) return;
    submitting.current = true;
    save.mutate(
      { data: { jiraProjectKey: tested.jiraProjectKey, doneStatusIds, reviewRange } },
      { ...callbacks, onSuccess: close },
    );
  }

  return (
    <FormModal<AddProjectValues>
      isOpen={isOpen}
      title="Add project"
      form={form}
      okText={tested ? 'Save' : 'Test connection'}
      isSubmitting={isBusy}
      onSubmit={handleSubmit}
      onCancel={() => {
        if (!submitting.current) close();
      }}
    >
      <Flex vertical gap="middle">
        {inlineError && <Alert type="error" showIcon title={msg('MSG48', inlineError.params)} />}
        <FormField<AddProjectValues>
          name="jiraProjectKey"
          label="Project key"
          rules={[rules.required('Project key'), keyLength]}
        >
          <Input count={{ show: true, max: KEY_MAX }} disabled={isBusy} onChange={discardResults} />
        </FormField>
        {tested && (
          <>
            <FormField<AddProjectValues>
              name="doneStatusIds"
              label="Done statuses"
              rules={[{ ...rules.required('Done statuses'), type: 'array' }]}
            >
              <Select
                mode="multiple"
                virtual={false}
                options={statusOptions(tested.statuses)}
                disabled={isBusy}
              />
            </FormField>
            <FormField<AddProjectValues>
              name="reviewRange"
              label="Review range"
              rules={[
                { ...rules.required('Review range'), type: 'number' },
                rules.wholeNumberMin(1),
                rangeMax,
              ]}
            >
              <InputNumber disabled={isBusy} />
            </FormField>
          </>
        )}
      </Flex>
    </FormModal>
  );
}
