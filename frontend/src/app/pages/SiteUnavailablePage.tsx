import { Result } from 'antd';
import { msg } from '@/shared/messages';

/**
 * The whole page when the site does not exist (MSG119) or its Workspace is suspended (MSG120): in place of every
 * screen, with no menu, no user menu and no Try again — nothing the user does here can help (STORY-011).
 */
export function SiteUnavailablePage({ site }: { site: 'unknown' | 'suspended' }) {
  return (
    <Result
      status={site === 'unknown' ? '404' : '403'}
      title={msg(site === 'unknown' ? 'MSG119' : 'MSG120')}
    />
  );
}
