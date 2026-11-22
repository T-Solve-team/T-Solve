import { DatePicker, Flex, Input, Select } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { TicketSource, type DepartmentRef } from '@/shared/api';
import { TICKET_SOURCE_LABELS } from '@/shared/lib';
import { QUERY_MAX } from '../hooks/useTicketSearch';
import styles from './SearchResults.module.css';

/** The filters of the Search; each is a URL search param. */
export type SearchFilter = 'q' | 'departmentId' | 'source';

type SearchFiltersProps = {
  departments: DepartmentRef[];
  q: string;
  departmentId: string | undefined;
  source: TicketSource | undefined;
  resolvedFrom: string | undefined;
  resolvedTo: string | undefined;
  /** null = the filter's default ("All …"), or no query. */
  onChange: (filter: SearchFilter, value: string | null) => void;
  /** `YYYY-MM-DD` days, both included; null = no range. */
  onResolvedChange: (range: [from: string, to: string] | null) => void;
};

const ALL = 'ALL';
const API_DAY = 'YYYY-MM-DD';

const SOURCE_OPTIONS = [
  { value: ALL, label: 'All' },
  ...Object.values(TicketSource).map((s) => ({ value: s, label: TICKET_SOURCE_LABELS[s] })),
];

function fromOption(value: string): string | null {
  return value === ALL ? null : value;
}

/** Figma 6.1: the Search box (searched on Enter), Department, Source and Resolved date. */
export function SearchFilters({
  departments,
  q,
  departmentId,
  source,
  resolvedFrom,
  resolvedTo,
  onChange,
  onResolvedChange,
}: SearchFiltersProps) {
  // What is typed; a new applied query (search, back/forward) replaces it. Reset during render, not by a `key`
  // remount, so the box keeps the focus after Enter (BUG-059).
  const [text, setText] = useState(q);
  const [shownQ, setShownQ] = useState(q);
  if (q !== shownQ) {
    setShownQ(q);
    setText(q);
  }

  return (
    <Flex vertical gap="middle">
      <Input.Search
        aria-label="Search"
        className={styles.query}
        size="large"
        placeholder="For example: VPN drops every few minutes"
        enterButton="Search"
        value={text}
        onChange={(event) => setText(event.target.value)}
        maxLength={QUERY_MAX}
        allowClear
        // Enter on a blank box does nothing (AC-002.3); the clear icon removes the query.
        onSearch={(value, _event, info) => {
          const text = value.trim();
          if (text || info?.source === 'clear') onChange('q', text || null);
        }}
      />
      <div className={styles.filters}>
        <div className={styles.filter}>
          <label htmlFor="search-department">Department</label>
          <Select<string>
            id="search-department"
            className={styles.department}
            size="small"
            variant="borderless"
            value={departmentId ?? ALL}
            onChange={(value) => onChange('departmentId', fromOption(value))}
            options={[
              { value: ALL, label: 'All Departments' },
              ...departments.map((d) => ({ value: d.departmentId, label: d.name })),
            ]}
          />
        </div>
        <div className={styles.filter}>
          <label htmlFor="search-source">Source</label>
          <Select<string>
            id="search-source"
            className={styles.source}
            size="small"
            variant="borderless"
            value={source ?? ALL}
            onChange={(value) => onChange('source', fromOption(value))}
            options={SOURCE_OPTIONS}
          />
        </div>
        <div className={styles.filter}>
          <label htmlFor="search-resolved">Resolved date</label>
          <DatePicker.RangePicker
            id={{ start: 'search-resolved' }}
            className={styles.resolved}
            size="small"
            variant="borderless"
            format="DD/MM/YYYY"
            value={[
              resolvedFrom ? dayjs(resolvedFrom) : null,
              resolvedTo ? dayjs(resolvedTo) : null,
            ]}
            onChange={(dates) => {
              const [from, to] = dates ?? [];
              onResolvedChange(from && to ? [from.format(API_DAY), to.format(API_DAY)] : null);
            }}
          />
        </div>
      </div>
    </Flex>
  );
}
