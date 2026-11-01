import { DatePicker, Flex, Select } from 'antd';
import dayjs from 'dayjs';
import type { ProjectRef } from '@/shared/api';
import { STATUS_TAGS } from '@/shared/ui';
import { SOLUTION_STATUSES, type SolutionFilterValues } from '../hooks/useSolutionFilters';
import styles from './SolutionFilters.module.css';

type SolutionFiltersProps = {
  filters: SolutionFilterValues;
  projects: ProjectRef[];
  /** null = clear that filter. */
  onChange: (
    changes: Partial<Record<'status' | 'projectId' | 'from' | 'to', string | null>>,
  ) => void;
};

const ALL = 'ALL';
const API_DAY = 'YYYY-MM-DD';

const STATUS_OPTIONS = [
  { value: ALL, label: 'All statuses' },
  ...SOLUTION_STATUSES.map((s) => ({ value: s, label: STATUS_TAGS[s].label })),
];

/** 1.2.1 (d): Status, Project and Date range in one row; each is a URL param and clearable (AC-003.22). */
export function SolutionFilters({ filters, projects, onChange }: SolutionFiltersProps) {
  const options = [
    { value: ALL, label: 'All Projects' },
    ...[...projects]
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((p) => ({ value: p.projectId, label: p.name })),
  ];
  const pick = (key: 'status' | 'projectId') => (value: string | undefined) =>
    onChange({ [key]: !value || value === ALL ? null : value });

  return (
    <Flex gap="middle" wrap>
      <Select<string>
        aria-label="Status"
        className={styles.status}
        allowClear
        value={filters.status ?? ALL}
        options={STATUS_OPTIONS}
        onChange={pick('status')}
      />
      <Select<string>
        aria-label="Project"
        className={styles.project}
        allowClear
        value={filters.projectId ?? ALL}
        options={options}
        onChange={pick('projectId')}
      />
      <div role="group" aria-label="Date range" className={styles.dates}>
        <DatePicker.RangePicker
          allowClear
          format="DD/MM/YYYY"
          placeholder={['Start date', 'End date']}
          value={[filters.from ? dayjs(filters.from) : null, filters.to ? dayjs(filters.to) : null]}
          onChange={(dates) => {
            const [from, to] = dates ?? [];
            if (from && to) onChange({ from: from.format(API_DAY), to: to.format(API_DAY) });
            else if (!dates) onChange({ from: null, to: null });
          }}
        />
      </div>
    </Flex>
  );
}
