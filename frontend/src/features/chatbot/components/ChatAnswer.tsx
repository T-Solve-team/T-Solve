import { Typography } from 'antd';
import type { ChatbotAnswer } from '@/shared/api';
import { msg } from '@/shared/messages';
import styles from './Conversation.module.css';

type ChatAnswerProps = { answer: ChatbotAnswer };

/**
 * 12.1 answer (callout 3): the AI module's text as plain text — never HTML — with its [n] markers as written; MSG109
 * when no source was found (GBR-36).
 */
export function ChatAnswer({ answer }: ChatAnswerProps) {
  return (
    <div className={styles.answer}>
      <Typography.Paragraph className={styles.text}>
        {answer.outcome === 'ANSWERED' ? answer.text : msg('MSG109')}
      </Typography.Paragraph>
    </div>
  );
}
