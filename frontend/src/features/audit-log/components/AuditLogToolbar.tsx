import { Button, DatePicker, Flex, Input, Select, Typography } from 'antd';
import dayjs from 'dayjs';
import { useSearchParams } from 'react-router';
import { msg } from '@/shared/messages';
import { ACTION_LABELS, dateRange, isAuditAction, vietnamToday } from '../hooks/audit-filters';
import styles from './AuditLogToolbar.module.css';

/** Applied filters live in the URL; the picker keeps unfinished calendar selection internally. */
export function AuditLogToolbar() {
  const [params, setParams] = useSearchParams();
  const from = params.get('from');
  const to = params.get('to');
  const range = dateRange(from, to);
  const rawAction = params.get('action');
  const action = isAuditAction(rawAction) ? rawAction : undefined;
  const actor = params.get('actor') ?? '';

  function update(changes: Record<string, string | null>) {
    setParams((current) => {
      const next = new URLSearchParams(current);
      next.delete('page');
      for (const [name, value] of Object.entries(changes)) {
        if (value) next.set(name, value);
        else next.delete(name);
      }
      return next;
    });
  }

  return (
    <Flex align="start" gap="middle" wrap>
      <Flex vertical gap="small" className={styles.actor}>
        <label htmlFor="audit-actor">Actor</label>
        <Input.Search
          key={actor}
          id="audit-actor"
          placeholder="Search email"
          defaultValue={actor}
          allowClear
          onSearch={(value) => update({ actor: value.trim() })}
        />
      </Flex>
      <Flex vertical gap="small">
        <label htmlFor="audit-action">Action</label>
        <Select
          id="audit-action"
          virtual={false}
          className={styles.action}
          value={action ?? ''}
          onChange={(value) => update({ action: value })}
          options={[
            { value: '', label: 'All actions' },
            ...Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label })),
          ]}
        />
      </Flex>
      <Flex vertical gap="small" className={styles.dates}>
        <label htmlFor="audit-from">Date range</label>
        <label htmlFor="audit-to" className={styles.visuallyHidden}>
          To date
        </label>
        <DatePicker.RangePicker
          id={{ start: 'audit-from', end: 'audit-to' }}
          format="DD/MM/YYYY"
          placeholder={['From date', 'To date']}
          value={range.valid && from && to ? [dayjs(from), dayjs(to)] : null}
          status={range.valid ? undefined : 'error'}
          aria-describedby={range.valid ? undefined : 'audit-date-error'}
          disabledDate={(date) => date.format('YYYY-MM-DD') > vietnamToday()}
          onChange={(dates) => {
            update({
              from: dates?.[0]?.format('YYYY-MM-DD') ?? null,
              to: dates?.[1]?.format('YYYY-MM-DD') ?? null,
            });
          }}
        />
        {!range.valid && (
          <>
            <Typography.Text id="audit-date-error" type="danger" role="alert">
              {msg('MSG39')}
            </Typography.Text>
            <Button onClick={() => update({ from: null, to: null })}>Clear date range</Button>
          </>
        )}
      </Flex>
    </Flex>
  );
}
