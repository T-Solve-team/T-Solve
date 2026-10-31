import { paths, type FeatureRoute } from '@/shared/routing';

/** Screens of the "chatbot" feature (guideline 08 §2). */
export const chatbotRoutes: FeatureRoute[] = [
  {
    path: paths.chatbot,
    title: 'Knowledge Chatbot', // 12.1
    roles: ['EMPLOYEE'],
    lazy: () => import('./pages/KnowledgeChatbotPage'),
  },
];
