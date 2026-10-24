/** 6.2 Ticket List — SRS III.6.2, Figma "6.2 Ticket List" (a)–(c). */
import { Flex } from 'antd';
import { TicketSource, TicketStatus } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { useListSearchParams } from '@/shared/routing';
import { TicketListFilters } from '../components/TicketListFilters';
import { TicketListTable } from '../components/TicketListTable';
import { useTicketList } from '../hooks/useTicketList';
import styles from './TicketListPage.module.css';

/** "?departmentId=2" → 2; missing or invalid → all Departments. */
function toDepartmentId(value: string | null): number | undefined {
  const id = Number(value);
  return value && Number.isInteger(id) ? id : undefined;
}

/** A URL value that is one of `allowed`; anything else → undefined ("All"), so the API never gets an invalid value. */
function oneOf<T extends string>(allowed: Record<string, T>, value: string | null): T | undefined {
  return Object.values(allowed).find((v) => v === value);
}

export function TicketListPage() {
  const { session } = useSession();
  const list = useListSearchParams({ field: 'updatedAt', order: 'desc' }); // newest first
  const keyword = list.getFilter('keyword') ?? '';
  const departmentId = toDepartmentId(list.getFilter('departmentId'));
  const source = oneOf(TicketSource, list.getFilter('source'));
  const status = oneOf(TicketStatus, list.getFilter('status'));
  const tickets = useTicketList({
    keyword: keyword || undefined,
    departmentId,
    source,
    status,
    ...list.apiPaging,
  });

  return (
    <Flex vertical gap="large">
      <TicketListFilters
        departments={session?.departments ?? []}
        keyword={keyword}
        departmentId={departmentId}
        source={source}
        status={status}
        onChange={list.setFilter}
      />
      <div className={styles.card}>
        <TicketListTable
          rows={tickets.data?.content}
          total={tickets.data?.page.totalElements ?? 0}
          isLoading={tickets.isPending}
          error={tickets.isError ? tickets.error : null}
          onRetry={() => void tickets.refetch()}
          page={list.page}
          onPageChange={list.setPage}
          sort={list.sort}
          onSortChange={list.setSort}
        />
      </div>
    </Flex>
  );
}

export default TicketListPage;
