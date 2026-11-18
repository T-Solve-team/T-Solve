/** 11.1 Wiki Home — SRS III.11.1, Report3 UC-44. Navigator | list | preview (KAN-372). */
import {
  BookOutlined,
  FileTextOutlined,
  PlusOutlined,
  SearchOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Button, Input, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { EmptyState, ErrorState, FormField, LoadingState } from '@/shared/ui';
import { FolderModal } from '../components/FolderModal';
import { FolderTree } from '../components/FolderTree';
import { WikiPreview } from '../components/WikiPreview';
import { useWikiHome, WIKI_QUERY_MAX } from '../hooks/useWikiHome';
import styles from './WikiHomePage.module.css';

export function WikiHomePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const home = useWikiHome();
  const [newFolderSpaceId, setNewFolderSpaceId] = useState<string | null>(null);
  const space = home.selectedSpace;
  const isManager = space?.accessLevel === 'KNOWLEDGE_MANAGER';
  const uploadParams = new URLSearchParams({ space: home.spaceId });
  if (home.folderId) uploadParams.set('folder', home.folderId);
  const folderName = home.folders.data?.find((f) => f.folderId === home.folderId)?.name;
  const title = home.isSearching
    ? `Search results for “${home.q}”`
    : space
      ? folderName
        ? `${space.name} / ${folderName}`
        : space.name
      : 'Company-wide';
  const withParams = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null) next.delete(key);
      else next.set(key, value);
    }
    return { pathname: paths.wikiHome, search: next.toString() };
  };

  return (
    <section className={styles.explorer}>
      <nav aria-label="Wiki" className={styles.navigator}>
        <Link
          to={paths.wikiHome}
          className={styles.entry}
          aria-current={home.isCompanyWide ? 'page' : undefined}
        >
          <BookOutlined aria-hidden />
          Company-wide
        </Link>
        {home.spaces.isPending ? (
          <LoadingState />
        ) : home.spaces.isError ? (
          <ErrorState error={home.spaces.error} onRetry={() => void home.spaces.refetch()} />
        ) : (
          home.spaces.data.length > 0 && (
            <section aria-label="Spaces" className={styles.group}>
              <span role="heading" aria-level={2} className={styles.label}>
                Spaces
              </span>
              {home.spaces.data.map((item) => (
                <div key={item.spaceId} className={styles.group}>
                  <Link
                    to={{
                      pathname: paths.wikiHome,
                      search: new URLSearchParams({ space: item.spaceId }).toString(),
                    }}
                    className={`${styles.entry} ${styles.spaceEntry}`}
                    aria-current={
                      item.spaceId === space?.spaceId && !home.folderId ? 'page' : undefined
                    }
                    aria-label={item.name}
                    aria-describedby={`wiki-access-${item.spaceId}`}
                  >
                    <span className={styles.ellipsis}>{item.name}</span>
                    <span id={`wiki-access-${item.spaceId}`} className={styles.access}>
                      {item.accessLevel === 'KNOWLEDGE_MANAGER' ? 'Knowledge Manager' : 'View'}
                    </span>
                  </Link>
                  {item.spaceId === space?.spaceId &&
                    (home.folders.isPending ? (
                      <LoadingState />
                    ) : home.folders.isError ? (
                      <ErrorState
                        error={home.folders.error}
                        onRetry={() => void home.folders.refetch()}
                      />
                    ) : (
                      <FolderTree
                        key={home.spaceId}
                        folders={home.folders.data}
                        folderId={home.folderId}
                        onSelect={home.selectFolder}
                        managedSpaceId={isManager ? home.spaceId : undefined}
                      />
                    ))}
                </div>
              ))}
            </section>
          )
        )}
      </nav>
      <div className={styles.listPane}>
        <div className={styles.toolbar}>
          <WikiSearch key={home.q} q={home.q} onSearch={home.search} />
          {isManager && (
            <>
              <Tooltip title="New folder">
                <Button
                  aria-label="New folder"
                  icon={<PlusOutlined />}
                  disabled={!home.folders.isSuccess}
                  onClick={() => setNewFolderSpaceId(home.spaceId)}
                />
              </Tooltip>
              <Tooltip title="Space Settings">
                <Button
                  aria-label="Space Settings"
                  icon={<SettingOutlined />}
                  onClick={() => void navigate(paths.spaceSettings(home.spaceId))}
                />
              </Tooltip>
              <Button
                type="primary"
                icon={<UploadOutlined />}
                onClick={() =>
                  void navigate({ pathname: paths.wikiUpload, search: uploadParams.toString() })
                }
              >
                Upload documents
              </Button>
            </>
          )}
        </div>
        <div className={styles.listTitle}>
          <Typography.Title level={2} id="wiki-list-title" className={styles.ellipsis}>
            {title}
          </Typography.Title>
          {home.q ? (
            <Link to={withParams({ q: null, doc: null })} className={styles.nowrap}>
              Clear search
            </Link>
          ) : (
            home.rows && (
              <Typography.Text type="secondary" className={styles.nowrap}>
                {home.rows.length} documents
              </Typography.Text>
            )
          )}
        </div>
        <div className={styles.rows}>
          {home.isListPending ? (
            <LoadingState />
          ) : home.list.isError ? (
            <ErrorState error={home.list.error} onRetry={() => void home.list.refetch()} />
          ) : !home.rows?.length ? (
            <EmptyState />
          ) : (
            <ul aria-labelledby="wiki-list-title" className={styles.list}>
              {home.rows.map((row) => (
                <li key={row.documentId}>
                  <Link
                    to={withParams({ doc: row.documentId })}
                    className={styles.row}
                    aria-current={row.documentId === home.previewId ? 'true' : undefined}
                  >
                    <span className={styles.fileIcon} aria-hidden>
                      <FileTextOutlined />
                    </span>
                    <span className={styles.rowMain}>
                      <span className={styles.rowTitle} title={row.title}>
                        {row.title}
                      </span>
                      <span className={styles.rowSub}>
                        <span>
                          {row.spaceId === space?.spaceId
                            ? (row.folderName ?? 'Top level')
                            : row.spaceName}
                        </span>
                        <span>{row.owner}</span>
                      </span>
                    </span>
                    <span className={styles.rowEnd}>
                      <span>
                        {row.visibility === 'COMPANY_WIDE' ? 'Company-wide' : 'Department'}
                      </span>
                      <span>{formatDateTime(row.updatedAt)}</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
      <aside aria-label="Preview" className={styles.preview}>
        {!home.list.isError && (
          <WikiPreview documentId={home.previewId} isListPending={home.isListPending} />
        )}
      </aside>
      {isManager && newFolderSpaceId === home.spaceId && home.folders.isSuccess && (
        <FolderModal
          spaceId={home.spaceId}
          folders={home.folders.data}
          onClose={() => setNewFolderSpaceId(null)}
        />
      )}
    </section>
  );
}

/** Only the unsubmitted draft is local; submitted Search is URL state. */
function WikiSearch({ q, onSearch }: { q: string; onSearch: (value: string) => void }) {
  const [draft, setDraft] = useState(q);
  const isTooLong = draft.length > WIKI_QUERY_MAX;
  return (
    <FormField
      className={styles.search}
      validateStatus={isTooLong ? 'error' : undefined}
      help={
        isTooLong ? msg('MSG02', { field_name: 'Search', max_length: WIKI_QUERY_MAX }) : undefined
      }
    >
      <Input
        type="search"
        aria-label="Search"
        placeholder="Search"
        prefix={<SearchOutlined aria-hidden />}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onPressEnter={() => onSearch(draft)}
      />
    </FormField>
  );
}

export default WikiHomePage;
