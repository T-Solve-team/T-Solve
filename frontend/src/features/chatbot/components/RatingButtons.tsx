import { Button, Flex } from 'antd';
import type { ChatTurn } from '../model';

type Rating = NonNullable<ChatTurn['rating']>;

type RatingButtonsProps = {
  rating: ChatTurn['rating'];
  /** True while a rating request runs: both buttons wait (AC-003.2). */
  disabled: boolean;
  /** `undefined` removes the rating. */
  onRate: (rating: ChatTurn['rating']) => void;
};

const BUTTONS: { rating: Rating; label: string }[] = [
  { rating: 'HELPFUL', label: 'Helpful' },
  { rating: 'NOT_HELPFUL', label: 'Not helpful' },
];

/** 12.1 (c) callout 5: Helpful / Not helpful; the rated one is highlighted and a click on it removes the rating. */
export function RatingButtons({ rating, disabled, onRate }: RatingButtonsProps) {
  return (
    <Flex gap="small">
      {BUTTONS.map((button) => {
        const isRated = rating === button.rating;
        return (
          <Button
            key={button.rating}
            size="small"
            type={isRated ? 'primary' : 'default'}
            aria-pressed={isRated}
            disabled={disabled}
            onClick={() => onRate(isRated ? undefined : button.rating)}
          >
            {button.label}
          </Button>
        );
      })}
    </Flex>
  );
}
