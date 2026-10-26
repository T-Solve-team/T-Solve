// The public face of the data layer (guideline 06 §1). Pages and features import from '@/shared/api' only.
export * from './generated'; // request functions, use… hooks and get…QueryKey functions (orval)
export * from './generated/model'; // API types and enum constants (orval)
export { ApiError, isApiError, type ApiFieldError } from './errors';
export { createQueryClient, type GlobalErrorHandlers } from './query-client';
export { markBackground } from './background';
export { getSiteStatus, resetSiteState, useSiteStatus, type SiteStatus } from './site-state';
