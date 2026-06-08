# Aura Crazy Feature Proposals

This proposal follows the `feature-proposal-research` workflow: inspect the existing project, compare implementation paths, recommend one, and stop before feature code until a direction is chosen.

## Feature Goal

Find surprising, high-impact Aura features that make the Android launcher feel alive, personal, and capable of acting on the user's behalf.

## Relevant Project Context

- Aura is a native Android launcher with Compose UI, app search/opening, app blocking, model settings, local/cloud memory, todos, always-listening service plumbing, and generated mini-apps.
- The backend already supports structured assistant actions: `block_app`, `create_mini_app`, `open_mini_app`, `create_mini_app_record`, and `query_mini_app_records`.
- Generated React mini-apps are compiled and validated server-side, then run in the Android app with scoped record APIs.
- The best "wow" features should extend Aura's context and action layers rather than replace the app architecture.

## External Research Notes

- Android `UsageStatsManager` can query app usage stats and events when the user grants the special usage access permission. This supports context such as recently used apps, distraction patterns, and time-in-app summaries. Source: https://developer.android.com/reference/android/app/usage/UsageStatsManager.html
- Android `NotificationListenerService` lets an app observe posted and removed notifications after the user explicitly grants notification listener access. This supports urgent-context surfaces, notification summaries, and triage workflows. Source: https://developer.android.com/reference/android/service/notification/NotificationListenerService.html
- Jetpack AppSearch is a maintained on-device search/indexing library for structured local data and full-text retrieval. This fits Aura's local memory, todos, mini-app records, and assistant history. Source: https://developer.android.com/jetpack/androidx/releases/appsearch
- Android assistant context APIs show that launcher/assistant experiences can benefit from contextual overlays, but privacy and permission clarity must be central. Source: https://developer.android.com/training/articles/assistant

## Options

### 1. Aura Situation Room

Aura creates a live command-center home screen from notifications, app usage, todos, memories, mini-app records, and app block state.

Example user moment: the phone unlocks and Aura shows, "You picked up your phone because Slack pinged twice. You also have 2 overdue tasks, Instagram is blocked for 18 minutes, and your Gym Tracker streak is at risk."

Implementation shape:
- Add an Android notification listener service with explicit onboarding.
- Add usage access onboarding and a local usage summarizer.
- Add a `ContextSignalRepository` that emits typed signals such as urgent notification, overdue task, blocked app active, repeated app loop, and mini-app streak risk.
- Add a new Situation Room screen or Home band in `AuraLauncherApp.kt`.
- Extend assistant chat context with the top signals.

Benefits:
- Very visible wow factor on the first screen.
- Reuses the current launcher, tasks, mini-apps, app blocks, and assistant action architecture.
- Becomes the foundation for smarter automations later.

Tradeoffs:
- Requires sensitive permissions and careful privacy copy.
- Notification content handling needs redaction controls and local-first defaults.

Estimated complexity: Medium-high.

### 2. Phone Autopilot Scripts

Users create natural-language rules that combine app usage, time, notifications, memory, todos, and assistant actions.

Example user moment: "When I open YouTube after 10 PM, ask if it is for learning. If I say no, block it for 45 minutes and open my Sleep Prep mini-app."

Implementation shape:
- Add an automation rule model with triggers, conditions, actions, enabled state, and audit history.
- Extend assistant tools with `create_rule`, `toggle_rule`, `list_rules`, and `run_rule_preview`.
- Start with local triggers: time window, app launched, app blocked, notification from package, todo overdue.
- Require confirmation for every generated rule before enabling it.

Benefits:
- Feels like the user can program their phone by speaking.
- Builds naturally on `block_app`, `open_mini_app`, and mini-app records.
- Can produce dramatic retention if reliable.

Tradeoffs:
- Needs strong guardrails to avoid surprising behavior.
- More testing surface: scheduling, permission state, app foreground detection, and action idempotency.

Estimated complexity: High.

### 3. Memory Constellation

Aura indexes local memories, todos, assistant chats, mini-app records, and app labels into an on-device searchable knowledge map.

Example user moment: "Show me everything around health lately" reveals gym logs, sleep tasks, notes, blocked late-night apps, and a generated summary with next actions.

Implementation shape:
- Add AppSearch dependencies and local schemas for memory, todo, chat, mini-app record, and app entity.
- Create indexing adapters around existing stores.
- Add a search screen with clusters by topic, date, and entity.
- Extend assistant chat with retrieval snippets from the local index.

Benefits:
- Makes Aura feel deeply personal without needing cloud sync.
- Improves assistant answers and mini-app discovery.
- Strong privacy story because the index can stay on device.

Tradeoffs:
- Requires indexing lifecycle and migration care.
- Semantic clustering may need an LLM or embedding layer later; first version can use full-text and tags.

Estimated complexity: Medium.

### 4. Mini-App Forge 2.0

Aura can revise existing generated mini-apps, migrate records, add screens, and explain changes before installing a new version.

Example user moment: "Make my expense tracker split needs/wants/regrets and add a weekly shame-free recap." Aura previews the diff, migrates old entries, and opens the upgraded app.

Implementation shape:
- Add mini-app versions and rollback metadata.
- Add backend endpoint to revise a bundle from an existing bundle plus user request.
- Add validator support for migration plans.
- Add UI for preview, accept, rollback, and record migration status.

Benefits:
- Strongest "Aura builds software for me" moment.
- Fits the current React mini-app runtime especially well.
- Can be implemented without extra Android special permissions.

Tradeoffs:
- Migration correctness matters.
- Generated UI quality needs preview and rollback to feel trustworthy.

Estimated complexity: Medium-high.

### 5. Aura Twin Mode

Aura learns the user's routines and generates a daily "likely next actions" surface: apps, mini-app actions, todos, and memories that fit the current time and context.

Example user moment: at 8:45 AM Aura shows "Open Maps to office", "Start Focus block", "Log breakfast", and "Message standup notes" before the user asks.

Implementation shape:
- Start local and heuristic: time buckets, app launch recency, todo due state, mini-app record patterns.
- Add lightweight explanations: "shown because you opened this 4 mornings this week."
- Later enhance with LLM summaries from local pattern snapshots.

Benefits:
- Makes the launcher feel predictive.
- Does not require notification listener for the first version.
- Pairs well with Situation Room.

Tradeoffs:
- Bad predictions feel uncanny; every suggestion needs a clear reason and dismiss action.

Estimated complexity: Medium.

### 6. Reality Check Overlay

When the user opens a distracting app, Aura can interrupt with a full-screen or launcher-level check-in: intent, timer, escape hatch, and alternate action.

Example user moment: opening Instagram triggers "What are you here for?" with buttons for "Post", "Message", "Scroll 5 min", or "Leave". Aura logs the choice and can block after the timer.

Implementation shape:
- Use usage events or launcher-mediated app launches to detect target app starts.
- Add per-app intervention settings.
- Reuse `AppBlockStore` for timed blocks.
- Add a mini-app record type for intent logs.

Benefits:
- Highly tangible behavior change feature.
- Smaller than full Autopilot Scripts.

Tradeoffs:
- Overlay behavior can be intrusive.
- Android background/overlay restrictions vary, so the safest first version is launcher-mediated and block-on-return.

Estimated complexity: Medium.

### 7. Notification Alchemist

Aura converts chaotic notifications into tasks, memories, mini-app records, and short spoken briefs.

Example user moment: "Turn the useful notifications from this morning into tasks" creates todos from delivery, calendar, work, and finance notifications while ignoring noise.

Implementation shape:
- Add notification listener and local notification event store with redaction.
- Add assistant action types for `create_todo`, `create_memory`, and maybe `dismiss_signal`.
- Add a review UI before saving extracted items.

Benefits:
- Excellent assistant utility.
- Makes notifications actionable instead of just summarized.

Tradeoffs:
- Needs privacy controls and conservative extraction.
- OTP and sensitive notification behavior can vary by Android version and app policy.

Estimated complexity: Medium-high.

### 8. Emotional Launcher Weather

Aura's home screen has a minimal living presence that changes with the user's day: calm, overloaded, focused, stale, or recovered. It is driven by actual signals, not decorative animation.

Example user moment: Aura says, "Today is noisy: 21 notifications, 3 app loops, no movement in your top task. Want a 25-minute quiet mode?"

Implementation shape:
- Define a small state machine over context signals.
- Add a compact animated presence component to Home.
- Add one-tap action recommendations per state.

Benefits:
- Polished and emotionally memorable.
- Relatively low risk if built on existing state first.

Tradeoffs:
- Needs restraint; fake emotion without useful action will feel gimmicky.

Estimated complexity: Medium.

## Recommendation

Build **Aura Situation Room** first.

Why:
- It is the best bridge between "impressive" and "realistic" for this codebase.
- It makes the launcher immediately feel smarter without requiring a full automation engine.
- It creates reusable infrastructure for later features: context signals, permission onboarding, notification summaries, usage summaries, and assistant grounding.
- Once Situation Room exists, Phone Autopilot Scripts, Notification Alchemist, Aura Twin Mode, and Emotional Launcher Weather become incremental extensions instead of separate bets.

## Suggested First Milestone

Build a permission-light Situation Room MVP:

1. Start with existing data only: todos, memories, mini-app records, app block rules, recent assistant messages, installed apps.
2. Add a `ContextSignal` model and repository.
3. Add a Home screen section that ranks signals and suggests one action per signal.
4. Feed the top signals into assistant chat context.
5. Add tests for signal ranking and assistant context formatting.

Then add special-permission sources in phase two:

1. Usage access for app loops and time-in-app patterns.
2. Notification listener for urgent notification clusters.
3. Local redaction and permission controls.

## Decision Needed Before Feature Code

Choose one path:

1. **Recommended:** Aura Situation Room.
2. **Wildest:** Phone Autopilot Scripts.
3. **Most personal:** Memory Constellation.
4. **Fastest wow without new Android permissions:** Mini-App Forge 2.0.

