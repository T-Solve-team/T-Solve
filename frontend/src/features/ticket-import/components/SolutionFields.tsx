import { Flex, Input } from 'antd';
import type { FillSolutionRequest } from '@/shared/api';
import { rules } from '@/shared/forms';
import { FormField } from '@/shared/ui';
import { SECTIONS } from '../lib/import-row';

export type SolutionSectionValues = Omit<FillSolutionRequest, 'rowNumbers'>;

/**
 * The five Solution sections as fields (GBR-33): every one required but Workaround. Shared by the Import Ticket
 * Preview popup and the Manual entry tab; `values` are the sections a row or a Draft already has.
 */
export function SolutionFields({
  values,
}: {
  values?: Partial<Record<keyof SolutionSectionValues, string | null>>;
}) {
  return (
    <Flex vertical gap="middle">
      {SECTIONS.map(([label, field]) => (
        <FormField<SolutionSectionValues>
          key={field}
          name={field}
          label={label}
          initialValue={values?.[field] ?? undefined}
          rules={field === 'workaround' ? [] : [rules.required(label)]}
        >
          <Input.TextArea rows={3} />
        </FormField>
      ))}
    </Flex>
  );
}
