# KAN-474 decisions

| # | Question | Options | Chosen | Why |
|---|----------|---------|--------|-----|
| 1 | Where are Symptoms and Scope optional? | Everywhere / only the Jira App form | Everywhere (user, 2026-11-27) | One rule for every Solution |
| 2 | How is an empty optional section stored and shown? | null + hidden / "" + "—" | null, not shown | Same as Workaround already |
| 3 | When does AI fill the Solution form? | On Done (background) / on form open / never | On Done, background job after the Draft is created (user) | Nobody waits; KAN-371 removed AI from opening the form for speed |
| 4 | What does AI fill? | Symptoms only / all five | All five from title, description and comments; unknown sections empty (user) | User request |
| 5 | AI for web manual entry and CSV? | No / yes | No (user) | Large imports would queue many AI jobs |
| 6 | Which comments go to the AI? | All / without T-Solve's own | Newest 100, without empty ones and the service account's own (suggestion comments) | The suggestion comment is T-Solve output, not the issue conversation |
| 7 | When may the job replace the Draft? | Always / only if not saved since created | Only if the Draft was not saved since it was created | Never overwrite what the user wrote |
| 8 | Which thread pool and time limit? | New / the rewrite's | The rewrite's pool and `JIRA_APP_REWRITE_TIMEOUT` | No new setting |
