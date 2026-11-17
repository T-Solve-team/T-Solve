// Where the "T-Solve account" page lives: the app's page in the Apps section of Jira's navigation (jira-app AC-008.1,
// AC-014.1).

/** The module key of the page in manifest.yml. */
export const SETTINGS_MODULE_KEY = 'tsolve-account-settings';

/**
 * The Jira path of the page, `/jira/apps/{appId}/{envId}`, from the Forge context of any module of this app; null
 * when the context does not name the app. `localId` is `ari:cloud:ecosystem::extension/{appId}/{envId}/static/{key}`.
 */
export function settingsPagePath(context) {
  const match = /extension\/([0-9a-f-]{36})\/([0-9a-f-]{36})\//.exec(context?.localId ?? '');
  if (!match) {
    return null;
  }
  const environmentId = context?.environmentId || match[2];
  return `/jira/apps/${match[1]}/${environmentId}`;
}
