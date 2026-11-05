import { Form, Typography } from 'antd';
import { Link } from 'react-router';
import { isApiError } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { EmptyState, ErrorState, FormModal, LoadingState } from '@/shared/ui';
import { useSuggestionResult } from '../hooks/useSuggestionHistory';
import styles from './TriageHistory.module.css';

type Props = { issueKey: string; onClose: () => void };

export function TriageResultDetailModal({ issueKey, onClose }: Props) {
  const [form] = Form.useForm();
  const detail = useSuggestionResult(issueKey);
  const missing = isApiError(detail.error) && detail.error.status === 404;
  const result = detail.data;

  return (
    <FormModal
      isOpen
      title="Triage Result Detail"
      width={720}
      form={form}
      okText="Close"
      onSubmit={onClose}
      onCancel={onClose}
    >
      {detail.isPending ? (
        <LoadingState />
      ) : missing ? (
        <EmptyState code="MSG04" />
      ) : detail.isError ? (
        <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
      ) : result ? (
        <dl className={styles.detail}>
          <dt>Issue</dt>
          <dd>{result.issueKey}</dd>
          <dt>Jira project key</dt>
          <dd>{result.project.key}</dd>
          <dt>Date</dt>
          <dd>{formatDateTime(result.createdAt)}</dd>
          <dt>Suggested solution</dt>
          <dd className={styles.solution}>{result.suggestedSolution ?? '—'}</dd>
          <dt>Sources</dt>
          <dd>
            <Typography.Text>
              {result.ticketSourceCount} Tickets · {result.wikiSourceCount} Wiki
            </Typography.Text>
            {result.wikiSources.map((source) => (
              <div key={source.documentId}>
                {source.isAvailable ? (
                  <Link to={paths.wikiDocument(source.documentId)}>{source.title}</Link>
                ) : (
                  <Typography.Text>
                    {source.title}: {msg('MSG110')}
                  </Typography.Text>
                )}
              </div>
            ))}
          </dd>
          <dt>Reporter's answer</dt>
          <dd>
            {result.reporterAnswer === 'YES'
              ? 'Solved'
              : result.reporterAnswer === 'NO'
                ? 'Not solved'
                : 'No answer'}
          </dd>
        </dl>
      ) : null}
    </FormModal>
  );
}
