import { Button, DatePicker, Flex, Select, Typography } from 'antd';
import dayjs from 'dayjs';
import { useSearchParams } from 'react-router';
import { useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { historyDateRange } from '../hooks/history-filters';
import styles from './TriageHistory.module.css';

export function HistoryFilters() {
  const { session } = useSession();
  const [params, setParams] = useSearchParams();
  const from = params.get('from');
  const to = params.get('to');
  const range = historyDateRange(from, to);
  const managed =
    session?.projects.filter((project) => project.roleInProject === 'PROJECT_MANAGER') ?? [];

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
      <Flex vertical gap="small">
        <label htmlFor="triage-project">Project</label>
        <Select
          id="triage-project"
          virtual={false}
          className={styles.select}
          value={params.get('projectId') ?? ''}
          onChange={(value) => update({ projectId: value })}
          options={[
            { value: '', label: 'All managed Projects' },
            ...managed.map((project) => ({
              value: project.projectId,
              label: project.jiraProjectKey,
            })),
          ]}
        />
      </Flex>
      <Flex vertical gap="small">
        <label htmlFor="triage-answer">Answer</label>
        <Select
          id="triage-answer"
          virtual={false}
          className={styles.select}
          value={params.get('answer') ?? ''}
          onChange={(value) => update({ answer: value })}
          options={[
            { value: '', label: 'All answers' },
            { value: 'YES', label: 'Solved' },
            { value: 'NO', label: 'Not solved' },
            { value: 'NONE', label: 'No answer' },
          ]}
        />
      </Flex>
      <Flex vertical gap="small" className={styles.dates}>
        <label htmlFor="triage-from">Date range</label>
        <label htmlFor="triage-to" className={styles.visuallyHidden}>
          To date
        </label>
        <DatePicker.RangePicker
          id={{ start: 'triage-from', end: 'triage-to' }}
          format="DD/MM/YYYY"
          placeholder={['From date', 'To date']}
          value={range.valid && from && to ? [dayjs(from), dayjs(to)] : null}
          status={range.valid ? undefined : 'error'}
          aria-describedby={range.valid ? undefined : 'triage-date-error'}
          onChange={(dates) =>
            update({
              from: dates?.[0]?.format('YYYY-MM-DD') ?? null,
              to: dates?.[1]?.format('YYYY-MM-DD') ?? null,
            })
          }
        />
        {!range.valid && (
          <>
            <Typography.Text id="triage-date-error" type="danger" role="alert">
              {msg('MSG39')}
            </Typography.Text>
            <Button onClick={() => update({ from: null, to: null })}>Clear date range</Button>
          </>
        )}
      </Flex>
    </Flex>
  );
}
