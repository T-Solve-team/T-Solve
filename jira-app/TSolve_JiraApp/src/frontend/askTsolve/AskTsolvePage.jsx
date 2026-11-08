// "Hỏi T-Solve" — the Jira project page that asks the Knowledge Chatbot before an issue is created (STORY-010).
import React, { useEffect, useReducer, useState } from 'react';
import {
  Button,
  ButtonGroup,
  ErrorMessage,
  Heading,
  List,
  ListItem,
  SectionMessage,
  Spinner,
  Stack,
  Text,
  TextArea,
  useProductContext,
} from '@forge/react';
import { CreateIssueModal } from '@forge/jira-bridge';
import { askQuestion, getEligibility } from '../api/tsolveClient';
import { message } from '../messages';
import { canSend, conversationReducer, history, initialConversation, lastAnswer, questions } from './conversation';
import { createIssueContext, sourceLabel } from './prefill';
import { validateQuestion } from './validation';

// The page's own labels (spec AC-010.1, AC-010.6 to AC-010.10; "Gửi": user, 2026-11-08).
const TITLE = 'Hỏi T-Solve';
const NOTE = 'Trả lời từ Solution và Wiki của T-Solve';
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
    <Button onClick={openCreate} isDisabled={!projectId}>
      {CREATE}
    </Button>
  );

  return (
    <Stack space="space.200">
      <Heading as="h2">{TITLE}</Heading>
      {access === 'checking' && <Spinner label="loading" />}
      {access === 'unlinked' && (
        <Stack space="space.200">
          <SectionMessage appearance="warning">
            <Text>{message('MSG62')}</Text>
          </SectionMessage>
          <ButtonGroup>{createButton}</ButtonGroup>
        </Stack>
      )}
      {access === 'linked' && conversation.status === 'solved' && (
        <SectionMessage appearance="success">
          <Text>{SOLVED_TEXT}</Text>
        </SectionMessage>
      )}
      {access === 'linked' && conversation.status !== 'solved' && (
        <Stack space="space.200">
          <Text>{NOTE}</Text>
          {conversation.turns.map((turn, index) => (
            <Turn key={index} turn={turn} />
          ))}
          {conversation.status === 'asking' && (
            <Stack space="space.100">
              <Text>{conversation.pending}</Text>
              <Spinner label="loading" />
            </Stack>
          )}
          {conversation.status === 'failed' && !FIELD_CODES.includes(conversation.error) && (
            <SectionMessage appearance="error">
              <Text>{message('MSG06')}</Text>
            </SectionMessage>
          )}
          <TextArea
            id="tsolve-question"
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
              setFieldError(null);
            }}
            isDisabled={conversation.status === 'asking'}
            isInvalid={fieldError != null}
          />
          {fieldError && <ErrorMessage>{message(fieldError)}</ErrorMessage>}
          <ButtonGroup>
            <Button appearance="primary" onClick={send} isDisabled={!canSend(conversation)}>
              {SEND}
            </Button>
            {conversation.status === 'answered' && (
              <Button onClick={() => dispatch({ type: 'solve' })}>{SOLVED}</Button>
            )}
            {(conversation.status === 'answered' || conversation.status === 'failed') && createButton}
          </ButtonGroup>
        </Stack>
      )}
    </Stack>
  );
}

function Turn({ turn }) {
  const { question, answer } = turn;
  if (answer.outcome !== 'ANSWERED') {
    return (
      <Stack space="space.100">
        <Text>{question}</Text>
        <Text>{message('MSG109')}</Text>
      </Stack>
    );
  }
  const sources = [...answer.citations].sort((a, b) => a.number - b.number);
  return (
    <Stack space="space.100">
      <Text>{question}</Text>
      <Text>{answer.text}</Text>
      {sources.length > 0 && (
        <List type="unordered">
          {sources.map((citation) => (
            <ListItem key={citation.number}>
              <Text>{sourceLabel(citation)}</Text>
              {citation.type === 'TICKET_SOLUTION' &&
                SOLUTION_SECTIONS.filter(([, field]) => citation.solution[field]).map(([label, field]) => (
                  <Text key={field}>{`${label}: ${citation.solution[field]}`}</Text>
                ))}
            </ListItem>
          ))}
        </List>
      )}
    </Stack>
  );
}
