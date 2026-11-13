# Runbook: check the chatbot session conversations on the real backend

For whoever signs off KAN-183 (chatbot spec STORY-002, UC-52). There is no conversation API: the conversations of a
session live only in the browser's `sessionStorage` (AC-002.7), and the backend answers each question with the
earlier turns the web app sends (STORY-007). This check proves the screen works against the real backend, not the
MSW mocks.

## Before you start

1. Start the stack as the README says: `docker compose up -d db redis keycloak fake-google`, then the backend.
2. The backend needs the AI server: set `OLLAMA_BASE_URL` and `OLLAMA_CHAT_MODEL` in `.env` (the T-Solve AI server, or
   a local Ollama with `bge-m3` and a chat model). Without it every question answers MSG06.
3. The Workspace needs at least one Published ticket with a Solution, so a question can get an `ANSWERED` reply.
4. Start the frontend with `VITE_API_MOCKING=false`.
5. Sign in through Keycloak at the Workspace site (for example `http://acme.localhost:5173`). The backend profile
   `identity-mock` also signs you in, but then step 7 below checks only the browser side of logout.

## Steps and expected results

| # | Do | Expect | AC |
|---|----|--------|----|
| 1 | Open `/chatbot` | An empty conversation "New conversation", selected in the list | AC-002.1 |
| 2 | Ask a question about the Published ticket | An answer with citations; the conversation is titled with the question | AC-002.2 |
| 3 | Ask a follow-up ("and on Windows?") | The answer uses the first turn; DevTools > Network: the request `history` holds the first turn | AC-002.5 |
| 4 | Click New conversation, ask another question | A second conversation at the top of the list, highlighted | AC-002.3, AC-002.6 |
| 5 | Open the first conversation from the list | Its questions and answers in order, with their citations | AC-002.4 |
| 6 | Reload the page | Both conversations are still listed | AC-002.7 |
| 7 | Log out, sign in again, open `/chatbot` | No earlier conversation; DevTools > Application > Session Storage has no `tsolve.session.chatbot.` key | AC-002.7 |

Record the result (pass, or the step that failed with a screenshot) on KAN-183. Log a failure in `docs/bug-log.md`.
