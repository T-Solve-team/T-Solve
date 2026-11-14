import { Card } from 'antd';
import type { ReactNode } from 'react';
import styles from './Cards.module.css';

type SectionCardProps = { title?: string; children: ReactNode };

/** Figma "Web/Card" Kind=Section: a titled block on dashboards and detail screens. */
export function SectionCard({ title, children }: SectionCardProps) {
  return (
    <Card
      // The title is the section heading, so screen readers can jump between sections.
      title={
        title && (
          <span role="heading" aria-level={2}>
            {title}
          </span>
        )
      }
      variant="borderless"
      className={styles.card}
    >
      {children}
    </Card>
  );
}
