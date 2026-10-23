import type { App } from 'antd';
import { MESSAGES, type MsgCode } from './catalog';
import { msg, type MsgParams } from './msg';

type AppApi = ReturnType<typeof App.useApp>;

/**
 * Toasts and acknowledgement dialogs need antd's App context. <MessageHost /> (rendered once by
 * AppProviders) hands it over here, so code outside React — the global error handler — can show
 * messages too (guideline 06 §6).
 */
let host: AppApi | null = null;

/** Called by MessageHost only. */
export function setMessageHost(next: AppApi | null): void {
  host = next;
}

function withHost(action: (app: AppApi) => void): void {
  if (host) action(host);
  // Before the app has mounted (or in a bare unit test) there is nowhere to show a message.
  else console.warn('[messages] MessageHost is not mounted; message dropped.');
}

/**
 * Shows a system message as a toast. Toast codes use their catalog level (success / error / info);
 * any other code shown as a toast is an error.
 * `showMessage('MSG14', { count: 3 })`
 */
export function showMessage(code: MsgCode, params: MsgParams = {}): void {
  const definition = MESSAGES[code];
  const level = 'level' in definition ? definition.level : 'error';
  withHost((app) => {
    void app.message.open({ type: level, content: msg(code, params) });
  });
}

/**
 * A dialog the user must acknowledge (no Cancel), e.g. MSG07 "Your session has expired…".
 * Resolves when the user clicks OK.
 */
export function showAcknowledgement(code: MsgCode, params: MsgParams = {}): Promise<void> {
  return new Promise((resolve) => {
    if (!host) {
      console.warn('[messages] MessageHost is not mounted; dialog skipped.');
      resolve();
      return;
    }
    host.modal.warning({
      title: msg(code, params),
      okText: 'OK',
      onOk: () => resolve(),
    });
  });
}
