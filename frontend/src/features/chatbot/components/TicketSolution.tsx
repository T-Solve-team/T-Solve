import type { CitedSolution } from '@/shared/api';
import styles from './Citations.module.css';

type TicketSolutionProps = { solution: CitedSolution };

/** Figma 12.1 (b): the Solution of a cited ticket, section by section; Workaround only when it has one. */
export function TicketSolution({ solution }: TicketSolutionProps) {
  const sections: [string, string | null][] = [
    ['Symptoms', solution.symptoms],
    ['Root cause', solution.rootCause],
    ['Resolution steps', solution.resolutionSteps],
    ['Workaround', solution.workaround],
    ['Scope of applicability', solution.scopeOfApplicability],
  ];
  return (
    <dl className={styles.solution}>
      {sections.map(
        ([label, text]) =>
          text !== null && (
            <div key={label}>
              <dt className={styles.sectionLabel}>{label}</dt>
              <dd className={styles.sectionText}>{text}</dd>
            </div>
          ),
      )}
    </dl>
  );
}
