// "Ask T-Solve" — the Jira project page that asks the Knowledge Chatbot before an issue is created (STORY-010).
import React, { useEffect, useReducer, useState } from 'react';
import {
  Box,
  Button,
  ErrorMessage,
  Icon,
  Inline,
  SectionMessage,
  Spinner,
  Stack,
  Text,
  TextArea,
  useProductContext,
  xcss,
} from '@forge/react';
import { CreateIssueModal } from '@forge/jira-bridge';
import { askQuestion, getEligibility } from '../api/tsolveClient';
import { message } from '../messages';
import { canSend, conversationReducer, history, initialConversation, lastAnswer, questions } from './conversation';
import { createIssueContext, sourceLabel } from './prefill';
import { validateQuestion } from './validation';

// The page's own labels (spec AC-010.1, AC-010.6 to AC-010.10; "Gửi": user, 2026-11-08). The title "Ask T-Solve" is
// the manifest's: Jira prints it above the page, so the page shows no heading of its own (BUG-182).
const NOTE = 'Trả lời từ Solution và Wiki của T-Solve';
// The card heading and the box placeholder come from the team's page design (user, 2026-11-11).
const CARD_TITLE = 'Hỏi T-Solve trước khi tạo ticket';
const PLACEHOLDER = 'Mô tả vấn đề của bạn';
const SOURCES = 'Nguồn:';
const SEND = 'Gửi';
const SOLVED = 'Đã giải quyết';
const CREATE = 'Chưa được, tạo ticket';
const SOLVED_TEXT = 'Vấn đề đã được giải quyết. Cảm ơn bạn!';

const FIELD_CODES = ['MSG01', 'MSG02'];

// The Solution of a ticket source, with the web chatbot's section names (GBR-36: no ticket id, link or Project).
const SOLUTION_SECTIONS = [
  ['Symptoms', 'symptoms'],
  ['Root cause', 'rootCause'],
  ['Resolution steps', 'resolutionSteps'],
  ['Workaround', 'workaround'],
  ['Scope of applicability', 'scopeOfApplicability'],
];

// Design tokens only, so the page follows Jira's light and dark themes.
const cardStyle = xcss({
  backgroundColor: 'elevation.surface.raised',
  borderColor: 'color.border',
  borderStyle: 'solid',
  borderWidth: 'border.width',
  borderRadius: 'radius.large',
  padding: 'space.200',
});
const questionStyle = xcss({
  backgroundColor: 'color.background.selected',
  borderRadius: 'radius.large',
  paddingBlock: 'space.100',
  paddingInline: 'space.150',
  maxWidth: '75%',
});
const answerStyle = xcss({
  backgroundColor: 'elevation.surface.sunken',
  borderRadius: 'radius.large',
  padding: 'space.150',
  maxWidth: '85%',
});
const avatarStyle = xcss({
  backgroundColor: 'color.background.discovery',
  borderRadius: 'radius.full',
  padding: 'space.050',
});
const growStyle = xcss({ flexGrow: 1 });

export function AskTsolvePage() {
  const context = useProductContext();
  const projectId = context?.extension?.project?.id;
  const [access, setAccess] = useState('checking');
  const [conversation, dispatch] = useReducer(conversationReducer, initialConversation);
  const [draft, setDraft] = useState('');
  const [fieldError, setFieldError] = useState(null);

  useEffect(() => {
    let active = true;
    // A failed check shows the box: the question endpoint still refuses an unlinked user (AC-010.11).
    getEligibility().then(
      ({ linked }) => active && setAccess(linked ? 'linked' : 'unlinked'),
      () => active && setAccess('linked'),
    );
    return () => {
      active = false;
    };
  }, []);

  const send = async () => {
    if (!canSend(conversation)) {
      return;
    }
    const invalid = validateQuestion(draft);
    setFieldError(invalid);
    if (invalid) {
      return;
    }
    const question = draft;
    const earlier = history(conversation);
    dispatch({ type: 'send', question });
    try {
      const answer = await askQuestion(question, earlier);
      dispatch({ type: 'answer', answer });
      setDraft('');
    } catch (failure) {
      const code = failure?.code ?? 'MSG06';
      if (code === 'MSG62') {
        setAccess('unlinked');
      }
      if (FIELD_CODES.includes(code)) {
        setFieldError(code);
      }
      dispatch({ type: 'fail', code });
    }
  };

  const openCreate = () => {
    const linked = access === 'linked';
    const modal = new CreateIssueModal({
      context: createIssueContext(
        projectId,
        linked ? questions(conversation) : [],
        linked ? lastAnswer(conversation) : null,
      ),
    });
    // Jira shows its own errors and "Created …" toast; nothing about the issue comes back to the page.
    Promise.resolve(modal.open()).catch(() => {});
  };

  const createButton = (
    <Button iconBefore="issue-raise" onClick={openCreate} isDisabled={!projectId}>
      {CREATE}
    </Button>
  );
  const answered = conversation.status === 'answered';
  const lastIndex = conversation.turns.length - 1;

  return (
    <Box xcss={cardStyle}>
      <Stack space="space.200">
        <Inline spread="space-between" alignBlock="baseline" shouldWrap>
          <Inline space="space.100" alignBlock="center">
            <Icon glyph="ai-chat" label="" />
            <Text weight="semibold">{CARD_TITLE}</Text>
          </Inline>
          <Text size="small" color="color.text.subtlest">
            {NOTE}
          </Text>
        </Inline>
        {access === 'checking' && <Spinner label="loading" />}
        {access === 'unlinked' && (
          <Stack space="space.200">
            <SectionMessage appearance="warning">
              <Text>{message('MSG62')}</Text>
            </SectionMessage>
            <Inline space="space.100">{createButton}</Inline>
          </Stack>
        )}
        {access === 'linked' && conversation.status === 'solved' && (
          <SectionMessage appearance="success">
            <Text>{SOLVED_TEXT}</Text>
          </SectionMessage>
        )}
        {access === 'linked' && conversation.status !== 'solved' && (
          <Stack space="space.200">
            {conversation.turns.map((turn, index) => (
              <Turn key={index} turn={turn}>
                {answered && index === lastIndex && (
                  <Inline space="space.100" shouldWrap>
                    <Button iconBefore="check-mark" onClick={() => dispatch({ type: 'solve' })}>
                      {SOLVED}
                    </Button>
                    {createButton}
                  </Inline>
                )}
              </Turn>
            ))}
            {conversation.status === 'asking' && (
              <>
                <Question text={conversation.pending} />
                <Answer>
                  <Spinner label="loading" />
                </Answer>
              </>
            )}
            {conversation.status === 'failed' && !FIELD_CODES.includes(conversation.error) && (
              <SectionMessage appearance="error">
                <Text>{message('MSG06')}</Text>
              </SectionMessage>
            )}
            {conversation.status === 'failed' && <Inline space="space.100">{createButton}</Inline>}
            <Stack space="space.050">
              <Inline space="space.100" alignBlock="end">
                <Box xcss={growStyle}>
                  <TextArea
                    id="tsolve-question"
                    value={draft}
                    placeholder={PLACEHOLDER}
                    minimumRows={1}
                    resize="smart"
                    onChange={(event) => {
                      setDraft(event.target.value);
                      setFieldError(null);
                    }}
                    isDisabled={conversation.status === 'asking'}
                    isInvalid={fieldError != null}
                  />
                </Box>
                <Button appearance="primary" iconBefore="send" onClick={send} isDisabled={!canSend(conversation)}>
                  {SEND}
                </Button>
              </Inline>
              {fieldError && <ErrorMessage>{message(fieldError)}</ErrorMessage>}
            </Stack>
          </Stack>
        )}
      </Stack>
    </Box>
  );
}

/** The user's question, right-aligned like a chat bubble. */
function Question({ text }) {
  return (
    <Inline alignInline="end">
      <Box xcss={questionStyle}>
        <Text>{text}</Text>
      </Box>
    </Inline>
  );
}

/** T-Solve's side of a turn, with its avatar. */
function Answer({ children }) {
  return (
    <Inline space="space.100" alignBlock="start">
      <Box xcss={avatarStyle}>
        <Icon glyph="ai-agent" label="T-Solve" color="color.icon.discovery" />
      </Box>
      <Box xcss={answerStyle}>
        <Stack space="space.100">{children}</Stack>
      </Box>
    </Inline>
  );
}

function Turn({ turn, children }) {
  const { question, answer } = turn;
  if (answer.outcome !== 'ANSWERED') {
    return (
      <Stack space="space.150">
        <Question text={question} />
        <Answer>
          <Text>{message('MSG109')}</Text>
          {children}
        </Answer>
      </Stack>
    );
  }
  const sources = [...answer.citations].sort((a, b) => a.number - b.number);
  const tickets = sources.filter((citation) => citation.type === 'TICKET_SOLUTION');
  return (
    <Stack space="space.150">
      <Question text={question} />
      <Answer>
        <Text>{answer.text}</Text>
        {sources.length > 0 && (
          <Inline space="space.050" alignBlock="center" shouldWrap>
            <Text size="small" color="color.text.subtle">
              {SOURCES}
            </Text>
            <Inline space="space.050" shouldWrap separator="·">
              {sources.map((citation) => (
                <Text key={citation.number} size="small" color="color.link">
                  {sourceLabel(citation)}
                </Text>
              ))}
            </Inline>
          </Inline>
        )}
        {tickets.map((citation) => (
          <Stack key={citation.number} space="space.025">
            {SOLUTION_SECTIONS.filter(([, field]) => citation.solution[field]).map(([label, field]) => (
              <Text key={field} size="small" color="color.text.subtle">
                {`${label}: ${citation.solution[field]}`}
              </Text>
            ))}
          </Stack>
        ))}
        {children}
      </Answer>
    </Stack>
  );
}
