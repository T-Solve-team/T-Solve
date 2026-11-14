// 4.1 Solution Form and 4.2 AI Rewrite Preview (jira-app STORY-002, STORY-004, STORY-005, STORY-007), in one UI Kit
// Modal. Closing it keeps a Draft and never submits (AC-005.1, AC-005.5).
import React, { useRef, useState } from 'react';
import {
  Box,
  Button,
  ButtonGroup,
  ErrorMessage,
  Inline,
  Label,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  ModalTitle,
  ModalTransition,
  SectionMessage,
  Spinner,
  Stack,
  Text,
  TextArea,
  xcss,
} from '@forge/react';
import { rewriteSolution, saveSolutionDraft, submitSolution } from '../api/tsolveClient';
import { message } from '../messages';
import { EMPTY_SECTIONS, normalize, SECTIONS, validateSections } from './sections';

// Screen and button names of SRS screens 4.1 and 4.2 (jira-app spec, English labels — user, 2026-11-11).
export const LABELS = {
  formTitle: 'Solution Form',
  rewriteTitle: 'AI Rewrite Preview',
  finish: 'Finish',
  close: 'Close',
  rewrite: 'Rewrite with AI',
  accept: 'Accept',
  discard: 'Discard',
  current: 'Current',
  rewritten: 'Rewritten',
  // Shown after Close; a Jira App label, not an SRS message (AC-005.1 — user, 2026-11-14).
  savedAsDraft: 'Saved as draft',
};

const columnStyle = xcss({ flexGrow: 1 });

/** The Jira flag shown once the form is gone: MSG66 after Finish (AC-004.2), "Saved as draft" after Close (AC-005.1). */
export function doneFlag(issueKey, outcome) {
  const closed = outcome === 'closed';
  return {
    id: `tsolve-${closed ? 'draft' : 'submitted'}-${issueKey}`,
    title: closed ? LABELS.savedAsDraft : message('MSG66'),
    type: closed ? 'info' : 'success',
    isAutoDismiss: true,
  };
}

/**
 * The form for `issueKey`. `form` is the answer of openSolutionForm (`sections`, `message`); `onDone(outcome)` is
 * called with 'submitted' after Finish (the caller shows MSG66) or 'closed' once Close has kept the Draft (the
 * caller shows "Saved as draft"); either way the caller removes the Modal.
 */
export function SolutionFormModal({ issueKey, summary, form, onDone, submit = submitSolution }) {
  const [sections, setSections] = useState(normalize(form?.sections ?? EMPTY_SECTIONS));
  const [step, setStep] = useState('form');
  const [rewritten, setRewritten] = useState(null);
  const [busy, setBusy] = useState(false);
  // Set at once, unlike `busy`, whose re-render reaches Forge later: Finish and Close never both run (BUG-221).
  const leaving = useRef(false);
  const [fieldError, setFieldError] = useState(null);
  const [notice, setNotice] = useState(form?.message ? { appearance: 'warning', code: form.message.code } : null);

  const edit = (field, value) => {
    setSections((current) => ({ ...current, [field]: value }));
    if (fieldError?.field === field || fieldError?.field === null) {
      setFieldError(null);
    }
  };

  const send = async (content) => {
    if (leaving.current) {
      return;
    }
    leaving.current = true;
    setBusy(true);
    try {
      await submit(issueKey, { summary, sections: content });
      onDone('submitted');
    } catch (failure) {
      if (failure.code === 'MSG01' || failure.code === 'MSG02') {
        setFieldError({ field: failure.field, code: failure.code, params: failure.params });
      } else {
        setNotice({ appearance: 'error', code: failure.code === 'MSG69' ? 'MSG69' : 'MSG06' });
      }
      setStep('form');
      leaving.current = false;
    } finally {
      setBusy(false);
    }
  };

  const finish = () => {
    const invalid = validateSections(sections);
    setFieldError(invalid);
    if (!invalid) {
      send(sections);
    }
  };

  // Close and the dialog's own close control keep what was typed as the Draft (AC-005.1); on failure the form stays
  // open with its content (AC-005.4).
  const close = async () => {
    if (busy || leaving.current) {
      return;
    }
    leaving.current = true;
    setBusy(true);
    try {
      await saveSolutionDraft(issueKey, sections);
      onDone('closed');
    } catch {
      setNotice({ appearance: 'error', code: 'MSG06' });
      setStep('form');
      setBusy(false);
      leaving.current = false;
    }
  };

  const rewrite = async () => {
    setBusy(true);
    try {
      setRewritten(normalize(await rewriteSolution(sections)));
      setStep('rewrite');
      setNotice(null);
    } catch {
      setNotice({ appearance: 'error', code: 'MSG68' });
    } finally {
      setBusy(false);
    }
  };

  const accept = () => {
    setSections(rewritten);
    setStep('form');
  };

  return (
    <ModalTransition>
      <Modal onClose={close} width="x-large">
        <ModalHeader>
          <ModalTitle>{step === 'rewrite' ? LABELS.rewriteTitle : `${LABELS.formTitle} · ${issueKey}`}</ModalTitle>
        </ModalHeader>
        <ModalBody>
          <Stack space="space.200">
            {notice && (
              <SectionMessage appearance={notice.appearance}>
                <Text>{message(notice.code)}</Text>
              </SectionMessage>
            )}
            {fieldError && fieldError.field === null && (
              <ErrorMessage>{message(fieldError.code, fieldError.params)}</ErrorMessage>
            )}
            {busy && <Spinner label="loading" />}
            {step === 'rewrite' && <RewritePreview current={sections} rewritten={rewritten} />}
            {step !== 'rewrite' &&
              SECTIONS.map(({ field, label, required }) => (
                <Stack key={field} space="space.050">
                  <Label labelFor={`solution-${field}`}>{required ? `${label} *` : label}</Label>
                  <TextArea
                    id={`solution-${field}`}
                    value={sections[field]}
                    onChange={(event) => edit(field, event.target.value)}
                    isDisabled={busy}
                    isInvalid={fieldError?.field === field}
                    minimumRows={3}
                    resize="smart"
                  />
                  {fieldError?.field === field && (
                    <ErrorMessage>{message(fieldError.code, fieldError.params)}</ErrorMessage>
                  )}
                </Stack>
              ))}
          </Stack>
        </ModalBody>
        <ModalFooter>
          {step === 'form' && (
            <Inline space="space.100" shouldWrap>
              <Button onClick={rewrite} isDisabled={busy}>
                {LABELS.rewrite}
              </Button>
              <Button onClick={close} isDisabled={busy}>
                {LABELS.close}
              </Button>
              <Button appearance="primary" onClick={finish} isDisabled={busy}>
                {LABELS.finish}
              </Button>
            </Inline>
          )}
          {step === 'rewrite' && (
            <ButtonGroup>
              <Button onClick={() => setStep('form')}>{LABELS.discard}</Button>
              <Button appearance="primary" onClick={accept}>
                {LABELS.accept}
              </Button>
            </ButtonGroup>
          )}
        </ModalFooter>
      </Modal>
    </ModalTransition>
  );
}

/** 4.2: the current and the rewritten Solution, section by section (AC-007.1). */
function RewritePreview({ current, rewritten }) {
  return (
    <Stack space="space.200">
      {SECTIONS.map(({ field, label }) => (
        <Stack key={field} space="space.050">
          <Text weight="semibold">{label}</Text>
          <Inline space="space.200" alignBlock="start">
            <Box xcss={columnStyle}>
              <Stack space="space.025">
                <Text size="small" color="color.text.subtlest">
                  {LABELS.current}
                </Text>
                <Text>{current[field] || '—'}</Text>
              </Stack>
            </Box>
            <Box xcss={columnStyle}>
              <Stack space="space.025">
                <Text size="small" color="color.text.subtlest">
                  {LABELS.rewritten}
                </Text>
                <Text>{rewritten[field] || '—'}</Text>
              </Stack>
            </Box>
          </Inline>
        </Stack>
      ))}
    </Stack>
  );
}
