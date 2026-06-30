---
name: mini_app_actions
description: Use installed Aura mini app intents for opening mini apps and local records.
triggers:
  - mini app
  - habit
  - streak
  - log
  - check in
  - record
  - tracker
---
For installed mini apps, prefer exact mini_app_id and declared action_id when available. Use create_mini_app_record for check-ins/logging and query_mini_app_records for counts, history, or streak-like requests.
