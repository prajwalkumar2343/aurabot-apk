---
name: aura-mini-app-builder
description: Prompt skill for creating professional Aura mini apps that can be installed, opened, and run locally by the assistant.
---

# Aura Mini App Builder Skill

Create one safe Aura mini app bundle as JSON only. Aura supports native declarative bundles and React runtime bundles; prefer the React runtime for user-requested "real apps", custom workflows, polished tools, or any mini app created from the assistant chat flow unless the caller explicitly asks for the native declarative runtime.

## Hard Rules

- Return only a JSON object matching the Aura mini app schema.
- For React runtime apps, set runtime to react and include codeBundle with entry App.jsx, appJsx source, css, allowedApis, and no compiledJs; Aura compiles the source.
- React appJsx must declare `export default function App(props)` and use only React plus provided Aura APIs from props such as records.list, records.create, records.update, and records.delete.
- For native runtime apps, keep behavior declarative through screens, components, actions, assistantIntents, dataSchema, and capabilities.
- Do not include APKs, webviews, plugins, remote URLs, iframes, HTML pages, unsupported capabilities, imported packages, fetch/network calls, cookies, localStorage, sessionStorage, indexedDB, WebSocket, eval, new Function, script tags, or global message listeners.
- Include a schema-driven form for apps where the user should enter their own data, not only canned quick actions.
- Use camelCase fields exactly: id, version, runtime, metadata, theme, icon, dataSchema, screens, actions, assistantIntents, capabilities, codeBundle.
- Every bundle must include a `widget` with `type`, `title`, `description`, `metric`, optional `goal`, and `actionIds`. It is the at-a-glance expression of the app's main purpose and tapping it opens the full mini app.
- Widget type must be `summary`, `counter`, `progress`, or `quick_actions`; metric must be `today_count`, `weekly_count`, `total_count`, or `streak`; actionIds may contain at most three actions declared by the bundle.
- Progress widgets require a positive `goal`; all other widget types must omit it or set it to null. Quick-action widgets should use safe, idempotent local record actions.
- Every component actionId and assistant intent actionId must reference an action declared in actions.
- Prefer local_storage and assistant_actions capabilities. Add react_runtime and scoped_storage for React runtime apps.

## Runtime Choice

- Use React runtime when the user asks Aura to build, create, make, or generate a mini app from chat and the request sounds like an actual custom app or workflow.
- Use native declarative runtime only when the user explicitly asks for a native/declarative mini app or when the requested app is a simple local tracker well served by schema-bound components.
- React runtime apps may have screens and actions for metadata/assistant integration, but their primary UI lives in codeBundle.appJsx and codeBundle.css.
- Native runtime apps must include screens and supported components because they do not have codeBundle UI.

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
- widget gives immediate value on Aura's home screen even before the full mini app is opened.
