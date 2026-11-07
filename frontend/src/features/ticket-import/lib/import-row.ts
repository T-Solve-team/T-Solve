import type { RowError } from '@/shared/api';
import { isMsgCode, msg } from '@/shared/messages';

/** The Solution sections in template order (GBR-33). */
export const SECTIONS = [
  ['Symptoms', 'symptoms'],
  ['Root cause', 'rootCause'],
  ['Resolution steps', 'resolutionSteps'],
  ['Workaround', 'workaround'],
  ['Scope', 'scope'],
] as const;

/** A code the catalog does not know shows MSG06 (design edge case 38). */
export function errorText(error: RowError): string {
  return isMsgCode(error.code) ? msg(error.code, error.params) : msg('MSG06');
}
