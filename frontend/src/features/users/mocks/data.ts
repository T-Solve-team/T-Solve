import type { DepartmentRef, UserAccount } from '@/shared/api';

export const WORKSPACE_ID = 1;
export const DEPARTMENTS: DepartmentRef[] = [
  { departmentId: 1, name: 'IT' },
  { departmentId: 2, name: 'HR' },
];

const NAMES = [
  'Nguyễn Văn An',
  'Trần Thị Lan',
  'Đỗ Minh Quân',
  'Phạm Quốc Bảo',
  'Ngô Thị Mai',
  'Lê Thu Hương',
  'Võ Thanh Tâm',
  'Hoàng Minh Đức',
  'Bùi Anh Tuấn',
  'Cao Minh Anh',
  'Đặng Hải Yến',
  'Dương Ngọc Hà',
  'Hà Minh Châu',
  'Hồ Thanh Sơn',
  'Huỳnh Bảo Ngọc',
  'Lâm Quốc Huy',
  'Lý Thùy Dung',
  'Mai Thanh Bình',
  'Nguyễn Hoài Nam',
  'Phan Quỳnh Chi',
  'Tạ Minh Khang',
  'Trịnh Ngọc Ánh',
  'Vũ Thanh Phong',
  'Vương Khánh Linh',
];

/** Synthetic records; the foreign Workspace record exercises isolation before paging. */
export function seedUsers(): (UserAccount & { workspaceId: number })[] {
  return [
    ...NAMES.map((fullName, index): UserAccount & { workspaceId: number } => ({
      id: `10000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
      workspaceId: WORKSPACE_ID,
      fullName,
      email: `user${index + 1}@company.example`,
      roles:
        index === 0 || index === 7
          ? ['EMPLOYEE', 'ADMIN']
          : index % 4 === 0
            ? ['EMPLOYEE', 'PROJECT_MANAGER']
            : index % 4 === 1
              ? ['EMPLOYEE']
              : index % 4 === 2
                ? ['EMPLOYEE', 'KNOWLEDGE_MANAGER']
                : ['EMPLOYEE', 'TICKET_RESOLVER'],
      department: DEPARTMENTS[index % 2] ?? null,
      status: index > 7 && index % 5 === 0 ? 'DEACTIVATED' : 'ACTIVE',
      lastSignInAt:
        index % 6 === 0 ? null : `2026-10-${String(index + 1).padStart(2, '0')}T08:00:00Z`,
    })),
    {
      id: '20000000-0000-4000-8000-000000000099',
      workspaceId: 2,
      fullName: 'Other Workspace',
      email: 'foreign@other.example',
      roles: ['EMPLOYEE'],
      department: null,
      status: 'ACTIVE',
      lastSignInAt: null,
    },
  ];
}
