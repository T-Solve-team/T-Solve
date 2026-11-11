// 3.4 Published Ticket Preview (jira-app STORY-009), opened from a search result of the Issue Panel.
import React, { useEffect, useState } from 'react';
import {
  Link,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  ModalTitle,
  ModalTransition,
  Button,
  SectionMessage,
  Spinner,
  Stack,
  Text,
} from '@forge/react';
import { getTicket } from '../api/tsolveClient';
import { SECTIONS } from '../form/sections';
import { message } from '../messages';

export const PREVIEW_LABELS = { openInTsolve: 'Open in T-Solve', close: 'Close', department: 'Department' };

/** The ticket `ticketId` (its UUID); `onClose` removes the Modal. */
export function TicketPreviewModal({ ticketId, onClose }) {
  const [ticket, setTicket] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    getTicket(ticketId).then(
      (found) => active && setTicket(found),
      (failure) => active && setError(failure.code === 'MSG08' ? 'MSG08' : 'MSG06'),
    );
    return () => {
      active = false;
    };
  }, [ticketId]);

  return (
    <ModalTransition>
      <Modal onClose={onClose} width="large">
        <ModalHeader>
          <ModalTitle>{ticket ? ticket.title : ' '}</ModalTitle>
        </ModalHeader>
        <ModalBody>
          {!ticket && !error && <Spinner label="loading" />}
          {error && (
            <SectionMessage appearance="error">
              <Text>{message(error)}</Text>
            </SectionMessage>
          )}
          {ticket && (
            <Stack space="space.200">
              <Text>{`${ticket.ticketId} · ${PREVIEW_LABELS.department}: ${ticket.departmentName}`}</Text>
              {SECTIONS.map(({ field, label }) => (
                <Stack key={field} space="space.050">
                  <Text weight="semibold">{label}</Text>
                  <Text>{ticket.solution?.[field] || '—'}</Text>
                </Stack>
              ))}
            </Stack>
          )}
        </ModalBody>
        <ModalFooter>
          {ticket?.webUrl && (
            <Link href={ticket.webUrl} openNewTab>
              {PREVIEW_LABELS.openInTsolve}
            </Link>
          )}
          <Button onClick={onClose}>{PREVIEW_LABELS.close}</Button>
        </ModalFooter>
      </Modal>
    </ModalTransition>
  );
}
