import { Menu } from 'antd';
import { Link, useLocation } from 'react-router';
import type { Role } from '@/shared/auth';
import { menuFor, selectedMenuPath } from './menu-config';

type SideMenuProps = { roles: readonly Role[] };

/** Figma "Web/Menu Item" list for the signed-in user: the menus of all their roles (D4). */
export function SideMenu({ roles }: SideMenuProps) {
  const { pathname } = useLocation();
  const entries = menuFor(roles);
  const selected = selectedMenuPath(entries, pathname);

  return (
    <Menu
      mode="inline"
      aria-label="Main menu"
      selectedKeys={selected ? [selected] : []}
      items={entries.map(({ label, path, icon: Icon }) => ({
        key: path,
        icon: <Icon />,
        label: <Link to={path}>{label}</Link>,
      }))}
    />
  );
}
