# AI review assist (STORY-015) — decisions

Date: 2026-11-25. Request (user): "AI reads the Solution, points out what is not sound, shows it to the PM, so the PM has less work."
Decided by Claude under the user's auto-decide rule; the user approves the spec before any code.

| # | Question | Options | Choice | Why |
|---|----------|---------|--------|-----|
| D1 | When does the AI check run? | A on-demand button on Ticket Detail · B on submit, stored · C both | A | No table, no job, no stale result after a resubmit. The AI server has a GPU, so a click waits seconds. Upgrade to B if PMs find the wait too long. |
| D2 | What does the AI read? | A Solution fields only · B + title/description · C + Evidence files | B | Title and description are needed to judge whether the Solution answers the problem. Evidence files need file parsing; later. |
| D3 | What does it point out? | free text · structured findings | Structured findings: field, severity, issue, suggestion; at most 10 | The PM scans a list per field; the LLM answer is checked against a JSON schema. |
| D4 | Checks | — | Missing or vague information; root cause that does not explain the symptoms; steps that are not actionable, out of order, or do not address the root cause; a workaround presented as the fix; risky actions without a warning (data deletion, restart in production); contradictions between fields | The usual reasons a PM rejects or requests changes. |
| D5 | Does the AI decide? | advisory · auto-reject | Advisory only | GBR-12: AI is optional; the PM keeps the decision. Not stored, not audited, not shown to the writer. |
| D6 | AI fails or times out | — | 503 with a new message (MSG_NEW_AI_REVIEW_1); the decision controls keep working | GBR-12. |
| D7 | Language of findings | English · same as Solution | Same language as the Solution text | Tickets are often Vietnamese. |
| D8 | Who may run it | — | The Project Manager of the ticket's Project, ticket Pending review, Solution Form | Same scope as the decisions (AC-009.3). |
| D9 | Prompt injection | — | Ticket text goes in as data in the user message; the system prompt says so; output is schema-validated; findings render as text | NFR 4. |
| D10 | Help write the Request Changes comment from findings | now · later | Later | Not needed for the first slice; easy add once PMs use it. |

Open, needs the user: a Jira issue for STORY-015 (the branch is `<type>/KAN-<n>-ticket-review-...`); the module owner (Phạm Tùng Dương) should know.
