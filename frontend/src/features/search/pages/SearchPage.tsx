/**
 * 6.1 Search — SRS III.6.1, laid out as design option C "Search first" (search design.md): a start state before a
 * query, then the Search box at the top with the results under it.
 */
import { SearchOutlined } from '@ant-design/icons';
import { Flex, Typography } from 'antd';
import { useSession } from '@/shared/auth';
import { SearchFilters } from '../components/SearchFilters';
import { SearchResultList } from '../components/SearchResultList';
import { useTicketSearch } from '../hooks/useTicketSearch';
import styles from './SearchPage.module.css';

export function SearchPage() {
  const { session } = useSession();
  const search = useTicketSearch();
  const { results } = search;
  const hasQuery = search.q !== '';

  return (
    <Flex vertical gap="large">
      <div className={hasQuery ? styles.top : styles.start}>
        {!hasQuery && (
          <div className={styles.intro}>
            <span className={styles.iconTile} aria-hidden="true">
              <SearchOutlined />
            </span>
            <Typography.Title level={2}>Find a Solution</Typography.Title>
            <Typography.Text type="secondary">
              Describe the problem in your own words. T-Solve searches the Published tickets of your
              Departments.
            </Typography.Text>
          </div>
        )}
        <SearchFilters
          departments={session?.departments ?? []}
          q={search.q}
          departmentId={search.departmentId}
          source={search.source}
          resolvedFrom={search.resolvedFrom}
          resolvedTo={search.resolvedTo}
          onChange={search.setFilter}
          onResolvedChange={search.setResolved}
        />
      </div>
      {hasQuery && (
        <section aria-label="Results" className={styles.results}>
          <SearchResultList
            results={results.data?.content}
            total={results.data?.page.totalElements ?? 0}
            isLoading={results.isPending}
            error={results.isError ? results.error : null}
            onRetry={() => void results.refetch()}
            page={search.page}
            onPageChange={search.setPage}
          />
        </section>
      )}
    </Flex>
  );
}

export default SearchPage;
