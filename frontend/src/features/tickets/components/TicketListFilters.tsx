import { Flex, Input, Select } from 'antd';
import { TicketSource, TicketStatus, type DepartmentRef } from '@/shared/api';
import { TICKET_SOURCE_LABELS } from '@/shared/lib';
import { STATUS_TAGS } from '@/shared/ui';
import styles from './TicketListFilters.module.css';

/** The filters of the Ticket List; each is a URL search param. */
export type TicketListFilter = 'keyword' | 'departmentId' | 'source' | 'status';

type TicketListFiltersProps = {
  departments: DepartmentRef[];
  keyword: string;
  departmentId: string | undefined;
  source: TicketSource | undefined;
  status: TicketStatus | undefined;
  /** null = the filter's default ("All …"). */
  onChange: (filter: TicketListFilter, value: string | null) => void;
};

const ALL = 'ALL';
/** Report3 §III 6.2: the Keyword is free text of at most 100 characters (spec AC-001.5), also from a link. */
export const KEYWORD_MAX = 100;

const SOURCE_OPTIONS = [
  { value: ALL, label: 'All' },
  ...Object.values(TicketSource).map((s) => ({ value: s, label: TICKET_SOURCE_LABELS[s] })),
];

const STATUS_OPTIONS = [
  { value: ALL, label: 'All statuses' },
  ...Object.values(TicketStatus).map((s) => ({ value: s, label: STATUS_TAGS[s].label })),
];

function fromOption(value: string): string | null {
  return value === ALL ? null : value;
}

/** Figma 6.2 callouts 1–2: Keyword (searched on Enter), Department, Source and Status. */
export function TicketListFilters({
  departments,
  keyword,
  departmentId,
  source,
  status,
  onChange,
}: TicketListFiltersProps) {
  return (
    <Flex align="end" gap="middle" wrap>
      <Flex vertical gap="small">
        <label htmlFor="ticket-list-keyword">Keyword</label>
        <Input.Search
          // A new applied keyword (search, back/forward) resets what is typed; typing alone changes nothing.
          key={keyword}
          id="ticket-list-keyword"
          className={styles.keyword}
          defaultValue={keyword}
          maxLength={KEYWORD_MAX}
          placeholder="Search by ticket ID, title or solution"
          allowClear
          onSearch={(value) => onChange('keyword', value.trim() || null)}
        />
      </Flex>
      <Flex vertical gap="small">
        <label htmlFor="ticket-list-department">Department</label>
        <Select<string>
          id="ticket-list-department"
          className={styles.department}
          value={departmentId ?? ALL}
          onChange={(value) => onChange('departmentId', fromOption(value))}
          options={[
            { value: ALL, label: 'All Departments' },
            ...departments.map((d) => ({ value: d.departmentId, label: d.name })),
          ]}
        />
      </Flex>
      <Flex vertical gap="small">
        <label htmlFor="ticket-list-source">Source</label>
        <Select<string>
          id="ticket-list-source"
          className={styles.source}
          value={source ?? ALL}
          onChange={(value) => onChange('source', fromOption(value))}
          options={SOURCE_OPTIONS}
        />
      </Flex>
      <Flex vertical gap="small">
        <label htmlFor="ticket-list-status">Status</label>
        <Select<string>
          id="ticket-list-status"
          className={styles.status}
          value={status ?? ALL}
          onChange={(value) => onChange('status', fromOption(value))}
          options={STATUS_OPTIONS}
        />
      </Flex>
    </Flex>
  );
}
