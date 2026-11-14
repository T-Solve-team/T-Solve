import { Flex, Progress, Typography } from 'antd';

type ShareProps = { label: string; value: number | null };

/** A rate 0..1 as a progress bar; a null rate (nothing to divide) shows a dash instead of 0 %. */
export function Share({ label, value }: ShareProps) {
  return (
    <Flex vertical>
      <Typography.Text type="secondary">{label}</Typography.Text>
      {value === null ? (
        <Typography.Text>—</Typography.Text>
      ) : (
        <Progress aria-label={label} percent={Math.round(value * 100)} />
      )}
    </Flex>
  );
}
