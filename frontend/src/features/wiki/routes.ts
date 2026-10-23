import { paths, type FeatureRoute } from '@/shared/routing';

// Popups of this feature (no route): 11.5 Folder (popup).

/** Screens of the "wiki" feature (guideline 08 §2). */
export const wikiRoutes: FeatureRoute[] = [
  {
    path: paths.wikiHome,
    title: 'Wiki Home', // 11.1
    roles: ['EMPLOYEE'],
    lazy: () => import('./pages/WikiHomePage'),
  },
  {
    path: paths.wikiDocument(),
    title: 'Wiki Document', // 11.2
    roles: ['EMPLOYEE'],
    lazy: () => import('./pages/WikiDocumentPage'),
  },
  {
    path: paths.wikiUpload,
    title: 'Upload Documents', // 11.4
    roles: ['KNOWLEDGE_MANAGER'],
    lazy: () => import('./pages/UploadDocumentsPage'),
  },
  {
    path: paths.spaceSettings(),
    title: 'Space Settings', // 11.7
    roles: ['KNOWLEDGE_MANAGER'],
    lazy: () => import('./pages/SpaceSettingsPage'),
  },
];
