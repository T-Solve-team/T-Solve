import * as chatbot from '@/features/chatbot/mocks/handlers';
import * as triage from '@/features/triage/mocks/handlers';
import * as wiki from '@/features/wiki/mocks/handlers';
import { FEATURE_MOCKS } from './handlers';

describe('mock handlers', () => {
  it('AC-005.4 registers the triage, wiki and chatbot mocks like every other feature', () => {
    for (const mocks of [triage, wiki, chatbot]) expect(FEATURE_MOCKS).toContain(mocks);
  });
});
