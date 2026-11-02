import { Form, Typography } from 'antd';
import { Link } from 'react-router';
import { isApiError } from '@/shared/api';
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
        <div className={styles.detail}>
          <Typography.Text strong>Issue</Typography.Text>
          <Typography.Text>{result.issueKey}</Typography.Text>
          <Typography.Text strong>Summary</Typography.Text>
          <Typography.Text>{result.issueSummary}</Typography.Text>
          <Typography.Text strong>Project</Typography.Text>
          <Typography.Text>{result.project.name}</Typography.Text>
          <Typography.Text strong>Date</Typography.Text>
          <Typography.Text>
            {new Intl.DateTimeFormat('en-GB', {
              timeZone: 'Asia/Ho_Chi_Minh',
              dateStyle: 'medium',
              timeStyle: 'short',
            }).format(new Date(result.createdAt))}
          </Typography.Text>
          <Typography.Text strong>Suggested solution</Typography.Text>
          <Typography.Text className={styles.solution}>
            {result.suggestedSolution ?? '—'}
          </Typography.Text>
          <Typography.Text strong>Sources</Typography.Text>
          <div>
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
          </div>
          <Typography.Text strong>Reporter's answer</Typography.Text>
          <Typography.Text>
            {result.reporterAnswer === 'YES'
              ? 'Solved'
              : result.reporterAnswer === 'NO'
                ? 'Not solved'
                : 'No answer'}
          </Typography.Text>
        </div>
      ) : null}
    </FormModal>
  );
}
