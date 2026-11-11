// Canned answers for looking at the Ask T-Solve page without a backend (USE_MOCK in mode.js). The shapes are the
// contract's: `{answerId, outcome, text, citations}` with Wiki and ticket Solution citations (AC-010.6, AC-010.7).
// Keywords in the question pick the case, so every state of the page can be shown in a demo.

/** How long the mock "thinks", so the waiting state is visible. */
export const MOCK_DELAY_MS = 1200;

const VPN = {
  outcome: 'ANSWERED',
  text:
    'Lỗi này đã được giải quyết trước đây. Bạn thử:\n' +
    '1. Cập nhật VPN client lên bản 5.2 [1].\n' +
    '2. Tắt Wi-Fi power saving trong Device Manager [2].',
  citations: [
    {
      number: 1,
      type: 'TICKET_SOLUTION',
      solution: {
        symptoms: 'VPN rớt mỗi tiếng, nhất là sau khi máy sleep',
        rootCause: 'VPN client bản cũ không kết nối lại sau sleep',
        resolutionSteps: 'Cập nhật VPN client lên bản 5.2 rồi khởi động lại máy',
        workaround: 'Kết nối lại VPN bằng tay sau khi mở máy',
        scopeOfApplicability: 'Laptop Windows của công ty',
      },
    },
    { number: 2, type: 'WIKI_DOCUMENT', wikiDocumentId: 'mock-wiki-vpn', title: 'Cài VPN client' },
  ],
};

const PASSWORD = {
  outcome: 'ANSWERED',
  text: 'Bạn đổi mật khẩu tại trang tài khoản công ty, mục "Bảo mật", rồi đăng nhập lại Outlook và VPN [1].',
  citations: [{ number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId: 'mock-wiki-password', title: 'Đổi mật khẩu' }],
};

const NO_SOURCE = { outcome: 'NO_SOURCE', text: '', citations: [] };

let next = 0;

/**
 * The mock answer for `question`: "vpn" → an answer with a ticket Solution and a Wiki document, "mật khẩu" or
 * "password" → an answer with one Wiki document, "lỗi mạng" → `null` (the caller fails with MSG06), anything else →
 * NO_SOURCE (MSG109).
 */
export function mockAnswer(question) {
  const text = question.toLowerCase();
  if (text.includes('lỗi mạng')) {
    return null;
  }
  next += 1;
  const answerId = `mock-${next}`;
  if (text.includes('vpn')) {
    return { answerId, ...VPN };
  }
  if (text.includes('mật khẩu') || text.includes('password')) {
    return { answerId, ...PASSWORD };
  }
  return { answerId, ...NO_SOURCE };
}
