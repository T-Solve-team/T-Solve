import { Form, Input } from 'antd';
import { useState } from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/render';
import { FormModal } from './FormModal';

function PendingForm({ action }: { action: string }) {
  const [form] = Form.useForm();
  const [isSubmitting, setSubmitting] = useState(true);
  return (
    <>
      <button onClick={() => setSubmitting(false)}>Finish request</button>
      <FormModal
        isOpen
        title="Hand Over Responsibilities"
        form={form}
        okText={action}
        isSubmitting={isSubmitting}
        onSubmit={() => undefined}
        onCancel={() => undefined}
      >
        <Input aria-label="Recipient" defaultValue="Selected manager" />
      </FormModal>
    </>
  );
}

describe('FormModal action accessibility', () => {
  it.each(['Save', 'Deactivate'])(
    'keeps %s as its accessible name during submission and spinner exit (BUG-075, BUG-076, BUG-077, BUG-078, BUG-079)',
    async (action) => {
      const user = userEvent.setup();
      renderWithProviders(<PendingForm action={action} />);
      const submit = screen.getByRole('button', { name: action });
      expect(submit).toBeDisabled();
      expect(submit).toHaveAttribute('aria-busy', 'true');
      await user.click(screen.getByRole('button', { name: 'Finish request' }));
      expect(screen.getByRole('button', { name: action })).toBe(submit);
      expect(submit).toBeEnabled();
      expect(submit).toHaveAttribute('aria-busy', 'false');
      expect(screen.getByRole('textbox', { name: 'Recipient' })).toHaveValue('Selected manager');
    },
  );
});
