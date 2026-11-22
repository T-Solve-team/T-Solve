import { DislikeOutlined, LikeOutlined } from '@ant-design/icons';
import { Button, Flex } from 'antd';
import type { ChatTurn } from '../model';
import styles from './Conversation.module.css';

type Rating = NonNullable<ChatTurn['rating']>;

type RatingButtonsProps = {
  rating: ChatTurn['rating'];
  /** True while a rating request runs: both buttons wait (AC-003.2). */
  disabled: boolean;
  /** `undefined` removes the rating. */
  onRate: (rating: ChatTurn['rating']) => void;
};

const BUTTONS: { rating: Rating; label: string; Icon: typeof LikeOutlined }[] = [
  { rating: 'HELPFUL', label: 'Helpful', Icon: LikeOutlined },
  { rating: 'NOT_HELPFUL', label: 'Not helpful', Icon: DislikeOutlined },
];

/** 12.1 (c) callout 5: Helpful / Not helpful; the rated one is highlighted and a click on it removes the rating. */
export function RatingButtons({ rating, disabled, onRate }: RatingButtonsProps) {
  return (
    <Flex className={styles.ratings}>
      {BUTTONS.map(({ rating: value, label, Icon }) => {
        const isRated = rating === value;
        return (
          <Button
            key={value}
            type="text"
            className={styles.ratingButton}
            icon={<Icon aria-hidden />}
            aria-pressed={isRated}
            disabled={disabled}
            onClick={() => onRate(isRated ? undefined : value)}
          >
            {label}
          </Button>
        );
      })}
    </Flex>
  );
}
