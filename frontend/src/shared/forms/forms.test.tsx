import { act, cleanup, screen } from '@testing-library/react';
import { Form, Input } from 'antd';
import { ApiError } from '@/shared/api';
import { msg } from '@/shared/messages';
import { AppForm, FormField } from '@/shared/ui';
import { renderWithProviders } from '@/test/render';
import { applyApiErrors } from './apply-api-errors';
import { rules } from './rules';

type Values = { reason: string };

function setup() {
  let formRef: ReturnType<typeof Form.useForm<Values>>[0] | undefined;
  function Harness() {
    const [form] = Form.useForm<Values>();
    formRef = form;
    return (
      <AppForm<Values> form={form}>
        <FormField<Values>
          name="reason"
          label="Reason"
          rules={[rules.required('Reason'), rules.maxLength('Reason', 5)]}
        >
          <Input.TextArea />
        </FormField>
      </AppForm>
    );
  }
  renderWithProviders(<Harness />);
  if (!formRef) throw new Error('form not created');
  return formRef;
}

describe('form rules and server errors', () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));

  afterEach(async () => {
    cleanup();
    try {
      // Ant Design's delayed form state can outlive unmount; settle it before JSDOM closes.
      await act(() => vi.runOnlyPendingTimersAsync());
      expect(vi.getTimerCount()).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it('treats whitespace-only input as empty (MSG01)', async () => {
    const form = setup();
    await act(async () => {
      form.setFieldsValue({ reason: '   ' });
      await form.validateFields().catch(() => undefined);
    });
    expect(await screen.findByText(msg('MSG01', { field_name: 'Reason' }))).toBeInTheDocument();
  });

  it('rejects text over the limit (MSG02)', async () => {
    const form = setup();
    await act(async () => {
      form.setFieldsValue({ reason: 'too long' });
      await form.validateFields().catch(() => undefined);
    });
    expect(
      await screen.findByText(msg('MSG02', { field_name: 'Reason', max_length: 5 })),
    ).toBeInTheDocument();
  });

  it('puts server field errors under their field', async () => {
    const form = setup();
    const error = new ApiError({
      status: 400,
      code: 'MSG01',
      fieldErrors: [{ field: 'reason', code: 'MSG01', params: { field_name: 'Reason' } }],
    });
    let applied = false;
    act(() => {
      applied = applyApiErrors(form, error);
    });
    expect(applied).toBe(true);
    expect(await screen.findByText(msg('MSG01', { field_name: 'Reason' }))).toBeInTheDocument();
  });

  it('returns false when the error has no field errors without leaking form timers (BUG-072)', () => {
    const form = setup();
    expect(applyApiErrors(form, new ApiError({ status: 409, code: 'MSG06' }))).toBe(false);
  });
});
