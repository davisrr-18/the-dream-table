# Durable Task Records

Use a task record when work is multi-step, crosses packages, carries meaningful compatibility or release risk, or may continue in another model or session. Do not create one for simple answers, read-only checks, or one-step mechanical edits.

1. Copy `TEMPLATE.md` to `YYYY-MM-DD-<slug>.md` before implementation.
2. Keep one record per demand and update it in place; link files and commits instead of copying logs.
3. Checkpoint after a meaningful phase, before a pause or model/session switch, and near two-thirds context consumption.
4. On resume, read the record and repository instructions, then verify Git state before continuing.
5. When verified, set the status to `Complete`, record final evidence, and retain the file as the handoff.

Task records must never contain credentials, tokens, private user data, chat transcripts, or large command output.

