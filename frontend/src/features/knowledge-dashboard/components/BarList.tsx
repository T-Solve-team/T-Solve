import { Typography } from 'antd';
import styles from './BarList.module.css';

const SERIES_CLASSES = [styles.series0, styles.series1, styles.series2];

export type Bar = { key: string; label: string; values: number[] };

type BarListProps = {
  /** Accessible name of the chart. */
  label: string;
  /** Series names, at most three; `values` of each bar follow the same order. */
  series: string[];
  bars: Bar[];
};

/**
 * Horizontal stacked bars drawn with CSS. Each bar is scaled to the largest total of the list, and its
 * values are also written as text so the figures are readable without the colours.
 */
export function BarList({ label, series, bars }: BarListProps) {
  const max = Math.max(1, ...bars.map((bar) => bar.values.reduce((sum, value) => sum + value, 0)));
  return (
    <div>
      <div className={styles.legend} aria-hidden>
        {series.map((name, index) => (
          <Typography.Text key={name} type="secondary">
            <span className={`${styles.swatch} ${SERIES_CLASSES[index]}`} />
            {name}
          </Typography.Text>
        ))}
      </div>
      <ul aria-label={label} className={styles.list}>
        {bars.map((bar) => (
          <li key={bar.key} className={styles.row}>
            <Typography.Text>{bar.label}</Typography.Text>
            <span className={styles.track} aria-hidden>
              {bar.values.map((value, index) => (
                <span
                  key={series[index]}
                  className={SERIES_CLASSES[index]}
                  style={{ width: `${(value / max) * 100}%` }}
                />
              ))}
            </span>
            <Typography.Text type="secondary">
              {bar.values.map((value, index) => `${series[index]} ${value}`).join(' · ')}
            </Typography.Text>
          </li>
        ))}
      </ul>
    </div>
  );
}
