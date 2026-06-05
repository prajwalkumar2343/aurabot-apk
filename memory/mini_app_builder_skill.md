---
name: aura-mini-app-builder
description: Prompt skill for creating professional Aura mini apps that can be installed, opened, and run locally by the assistant.
---

# Aura Mini App Builder Skill

Create one safe declarative Aura mini app bundle as JSON only.

## Hard Rules

- Return only a JSON object matching the Aura mini app schema.
- Do not include executable code, scripts, webviews, APKs, plugins, remote URLs, iframes, HTML, or unsupported capabilities.
- Keep all behavior declarative through screens, components, actions, assistantIntents, dataSchema, and capabilities.
- Include a schema-driven form for apps where the user should enter their own data, not only canned quick actions.
- Use camelCase fields exactly: id, version, metadata, theme, icon, dataSchema, screens, actions, assistantIntents, capabilities.
- Every component actionId and assistant intent actionId must reference an action declared in actions.
- Prefer local_storage and assistant_actions capabilities.

## Professional App Shape

- Build mini apps like focused local tools, not toy demos.
- Include at least two screens when useful: Dashboard plus Details, Plan, Categories, Settings, or History.
- The first screen should give immediate value with stats, quick actions, trend, and recent activity.
- Secondary screens should expose forms, lists, direct buttons, settings, guidance, categories, or workflows.
- Use polished names, realistic categories, clear descriptions, and distinctive theme colors.
- Make quick actions specific to the user's requested job.
- Include assistant intents for opening the app, running common actions, logging entries, and querying history.

## Supported Components

- dashboard_block: compact KPI or total.
- streak_view: consecutive day or momentum metric.
- progress_ring: circular progress-style metric.
- quick_action_grid: 2-4 high-frequency actions.
- chart: recent trend, usually weekly_count.
- form: render dataSchema fields and create a custom local record from user-entered values.
- timeline: local history from records.
- list: menu, categories, routines, or saved records.
- button: single prominent action.
- slider: progress toward a small local goal.
- settings: local storage, assistant, and schema setup surface.
- bottom_sheet: concise note, coach tip, or app guidance.

## Supported Actions

- create_record: create a local record with values.
- query_records: query local app records.
- open_screen: navigate to a declared screen.
- update_record: update a local record.
- delete_record: delete a local record.

## Output Checklist

- id is stable, lowercase, and namespaced with generated.
- metadata.name is short and app-like.
- metadata.description explains the user value in one sentence.
- dataSchema fields match the records the app stores.
- actions cover the most common user actions.
- screens use at least dashboard_block, quick_action_grid, chart or timeline, a form for custom entries, and one richer component such as list, settings, button, slider, or bottom_sheet.
- assistantIntents include natural utterances for creation follow-up and daily use.
