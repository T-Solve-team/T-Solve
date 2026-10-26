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
  // MSG119 / MSG120 are shown only by the site page, which replaces the whole app (AC-011.5): never as a toast,
  // not even from a screen that handles its own mutation errors.
  if (code === 'MSG119' || code === 'MSG120') return;
  const definition = MESSAGES[code];
  const level = 'level' in definition ? definition.level : 'error';
  withHost((app) => {
    void app.message.open({ type: level, content: msg(code, params) });
  });
}

/** The acknowledgement dialogs that are open, by message code: how to close one and let its promise resolve. */
const openAcknowledgements = new Map<MsgCode, () => void>();

/**
 * A dialog the user must acknowledge (no Cancel), e.g. MSG07 "Your session has expired…".
 * Resolves when the user clicks OK, or when `dismissAcknowledgement` closes it.
 */
export function showAcknowledgement(code: MsgCode, params: MsgParams = {}): Promise<void> {
  return new Promise((resolve) => {
    if (!host) {
      console.warn('[messages] MessageHost is not mounted; dialog skipped.');
      resolve();
      return;
    }
    const dialog = host.modal.warning({
      title: msg(code, params),
      okText: 'OK',
      onOk: () => {
        openAcknowledgements.delete(code);
        resolve();
      },
    });
    openAcknowledgements.set(code, () => {
      openAcknowledgements.delete(code);
      // No leave animation: the app is about to switch to another screen, and the dialog must be gone with it.
      dialog.update({ transitionName: '', maskTransitionName: '' });
      dialog.destroy();
      resolve();
    });
  });
}

/**
 * Closes the acknowledgement dialog of this message code, if it is open, without the user's click. Other dialogs
 * stay. Its `showAcknowledgement` promise resolves.
 */
export function dismissAcknowledgement(code: MsgCode): void {
  openAcknowledgements.get(code)?.();
}
