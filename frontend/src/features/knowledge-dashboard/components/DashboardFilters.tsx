import { DatePicker, Flex, Select, Typography } from 'antd';
import dayjs from 'dayjs';
import type { DashboardOptions } from '@/shared/api';
import { msg } from '@/shared/messages';
import {
  dashboardToday,
  type DashboardPeriod,
  type useDashboardFilters,
} from '../hooks/useDashboardFilters';
import styles from './DashboardFilters.module.css';

type DashboardFiltersProps = {
  filters: ReturnType<typeof useDashboardFilters>;
  options?: DashboardOptions;
  isAdmin: boolean;
  departmentId?: string;
};

export function DashboardFilters({
  filters,
  options,
  isAdmin,
  departmentId,
}: DashboardFiltersProps) {
  const projects =
    options?.projects.filter((project) => project.departmentId === departmentId) ?? [];
  const departments = options?.departments ?? [];
  const dateValue = (value: string | null) =>
    value && dayjs(value).isValid() && dayjs(value).format('YYYY-MM-DD') === value
      ? dayjs(value)
      : null;

  return (
    <Flex align="start" gap="middle" wrap>
      <Flex vertical gap="small">
        <label htmlFor="dashboard-period">Period</label>
        <Select<DashboardPeriod>
          id="dashboard-period"
          className={styles.select}
          virtual={false}
          value={filters.period}
          onChange={filters.setPeriod}
          status={filters.isRangeValid ? undefined : 'error'}
          aria-describedby={filters.isRangeValid ? undefined : 'dashboard-date-error'}
          options={[
            { value: '7d', label: 'Last 7 days' },
            { value: '30d', label: 'Last 30 days' },
            { value: '90d', label: 'Last 90 days' },
            { value: 'custom', label: 'Custom range' },
          ]}
        />
        {filters.period === 'custom' && (
          <Flex gap="small">
            <Flex vertical gap="small">
              <label htmlFor="dashboard-from">From date</label>
              <DatePicker
                id="dashboard-from"
                format="DD/MM/YYYY"
                value={dateValue(filters.from)}
                status={filters.isRangeValid ? undefined : 'error'}
                aria-describedby={filters.isRangeValid ? undefined : 'dashboard-date-error'}
                disabledDate={(date) => date.format('YYYY-MM-DD') > dashboardToday()}
                onChange={(date) =>
                  filters.setRange(date?.format('YYYY-MM-DD') ?? null, filters.to)
                }
              />
            </Flex>
            <Flex vertical gap="small">
              <label htmlFor="dashboard-to">To date</label>
              <DatePicker
                id="dashboard-to"
                format="DD/MM/YYYY"
                value={dateValue(filters.to)}
                status={filters.isRangeValid ? undefined : 'error'}
                aria-describedby={filters.isRangeValid ? undefined : 'dashboard-date-error'}
                disabledDate={(date) => date.format('YYYY-MM-DD') > dashboardToday()}
                onChange={(date) =>
                  filters.setRange(filters.from, date?.format('YYYY-MM-DD') ?? null)
                }
              />
            </Flex>
          </Flex>
        )}
        {!filters.isRangeValid && (
          <Typography.Text id="dashboard-date-error" type="danger" role="alert">
            {msg('MSG39')}
          </Typography.Text>
        )}
      </Flex>
      <Flex vertical gap="small">
        <label htmlFor="dashboard-department">Department</label>
        <Select
          id="dashboard-department"
          className={styles.select}
          virtual={false}
          value={departmentId ?? ''}
          disabled={!isAdmin}
          onChange={filters.setDepartment}
          options={[
            ...(isAdmin ? [{ value: '', label: 'All Departments' }] : []),
            ...departments.map((department) => ({
              value: department.departmentId,
              label: department.name,
            })),
          ]}
        />
      </Flex>
      <Flex vertical gap="small">
        <label htmlFor="dashboard-project">Project</label>
        <Select
          id="dashboard-project"
          className={styles.select}
          virtual={false}
          value={filters.projectId ?? ''}
          disabled={!departmentId}
          onChange={filters.setProject}
          options={[
            { value: '', label: 'All Projects' },
            ...projects.map((project) => ({ value: project.projectId, label: project.name })),
          ]}
        />
      </Flex>
    </Flex>
  );
}
