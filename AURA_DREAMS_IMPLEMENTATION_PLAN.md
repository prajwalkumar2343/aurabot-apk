# Aura Dreams While You Sleep — Detailed Implementation Plan

## 1. Decision and Scope

Implement **Local Dreams** as a deterministic, local-first nightly improvement workflow with optional on-device model assistance added only after the evidence, validation, approval, and recovery layers are proven.

Aura Dreams is not an autonomous agent that changes the phone overnight. It is a durable evaluator and proposal compiler:

```text
collect evidence
  -> normalize and redact
  -> detect patterns
  -> score opportunities
  -> build typed proposals
  -> validate shadow changes
  -> present a morning report
  -> apply only after explicit approval
```

The first production release will support four proposal families:

1. Automation failure diagnosis and repair candidates.
2. Stale-task rescue proposals.
3. Mini-app evolution proposals.
4. Repeated routine proposals derived from existing Aura activity.

App-usage distraction detection and Gemini Nano wording/generation are later, permission-gated phases.

### Non-goals for the first release

- No automatic activation or mutation of automations.
- No automatic mini-app installation or revision.
- No screenshots or raw accessibility text in the Dream database.
- No continuous location tracking.
- No notification-content collection.
- No cloud upload of behavioral evidence.
- No exact promise that processing begins at a particular clock time.
- No open-ended overnight agent loop.

## 2. Why This Architecture Fits Aura

Aura already has most of the required source data and application boundaries:

- Automation definitions, revision hashes, durable runs, step attempts, failures, and retry state.
- A validated `AutomationRuntime.upsertAndRestore` path for applying automation changes.
- Mini-app records, events, versions, revision previews, validation, and rollback.
- Todos and memories in both guest-local and authenticated modes.
- Existing geofence and schedule events.
- A single application container available to background Android components.

The Dreams feature should read those systems through their repositories. Existing packages must not import the Dreams package. This preserves the dependency direction:

```text
Compose UI -> Dreams application service -> Dreams core decisions
                                      \-> existing Aura repositories
Android/LLM adapters -----------------> Dreams core interfaces
```

## 3. Implementation Alternatives

### Option A — Deterministic Dreams Lite

Use only local repositories and rule-based detectors. This has the smallest privacy and compatibility surface and works on every currently supported device.

Tradeoff: explanations and repair suggestions will be less flexible.

### Option B — Hybrid Local Dreams (recommended target)

Ship Option A first, then add Gemini Nano behind a `DreamProposalModel` interface for naming, summarizing, and producing bounded typed drafts. Deterministic code still detects patterns, validates output, and controls all effects.

Tradeoff: the Prompt API is beta, device availability varies, inputs are limited to fewer than 4,000 tokens, short outputs are recommended, and AICore imposes per-app quotas. The feature must always degrade to Option A. See the [ML Kit Prompt API setup and limitations](https://developers.google.com/ml-kit/genai/prompt/android/get-started).

### Option C — Cloud Deep Dreams

Send redacted aggregates to the existing FastAPI model harness for richer analysis and mini-app generation.

Tradeoff: greater privacy, cost, latency, authentication, retry, and disclosure complexity. This is not part of the initial implementation.

## 4. Runtime Architecture

### 4.1 Chosen control pattern

Use a **fixed durable workflow**, not a free-running agent:

```text
DreamScheduler
  -> DreamWorker
  -> DreamOrchestrator.run(window)
       1. admit run and persist input window
       2. collect bounded evidence snapshot
       3. redact and persist signals
       4. run deterministic detectors
       5. apply suppression and scoring policy
       6. build typed proposal drafts
       7. validate drafts against current state
       8. persist morning report
       9. notify if useful proposals exist
```

Every stage is idempotent for a stable `DreamRun.id`. A worker retry resumes from the last completed stage rather than duplicating signals or proposals.

### 4.2 Package layout

Create a focused Android domain package:

```text
android/app/src/main/java/com/aura/app/dreams/
  DreamModels.kt
  DreamEntities.kt
  DreamDao.kt
  DreamDatabase.kt
  DreamRepository.kt
  DreamSettingsStore.kt
  DreamEvidenceSources.kt
  DreamDetectors.kt
  DreamScorer.kt
  DreamProposalEngine.kt
  DreamProposalValidator.kt
  DreamProposalApplier.kt
  DreamOrchestrator.kt
  DreamScheduler.kt
  DreamWorker.kt
  DreamNotificationPublisher.kt
  DreamPrivacyPolicy.kt

android/app/src/main/java/com/aura/app/ui/
  LauncherDreamsUi.kt
  DreamsViewModel.kt
```

If `DreamDetectors.kt` grows beyond a reviewable size, split it by stable concept rather than creating generic helpers:

```text
AutomationFailureDetector.kt
StaleTaskDetector.kt
MiniAppEvolutionDetector.kt
RepeatedRoutineDetector.kt
DistractionLoopDetector.kt
```

Do not add the feature logic to `LauncherViewModel.kt`; that file is already a large, high-touch UI coordinator.

## 5. Domain Contracts

### 5.1 Signals

`DreamSignal` is a privacy-reduced fact derived from source state.

```kotlin
data class DreamSignal(
    val id: String,
    val kind: DreamSignalKind,
    val source: DreamSignalSource,
    val subjectId: String?,
    val occurredAt: Long,
    val windowStart: Long,
    val windowEnd: Long,
    val fingerprint: String,
    val summary: String,
    val attributes: Map<String, String>,
    val confidence: Float,
    val privacyClass: DreamPrivacyClass,
    val expiresAt: Long
)
```

Initial signal kinds:

- `AUTOMATION_RUN_FAILED`
- `AUTOMATION_STEP_FAILED`
- `AUTOMATION_RUN_SUCCEEDED`
- `TODO_STALE`
- `TODO_COMPLETED`
- `MINI_APP_RECORD_PATTERN`
- `MINI_APP_EVOLUTION_AVAILABLE`
- `MANUAL_ROUTINE_REPEATED`
- `GEOFENCE_ROUTINE_REPEATED`
- `APP_SESSION`
- `APP_REOPEN_LOOP`

Rules:

- `attributes` accepts an allowlisted set per signal kind.
- Never store message bodies, typed text, credentials, phone numbers, raw coordinates, or raw accessibility node dumps.
- Package names may be stored only after `DreamPrivacyPolicy` allows the package.
- Named places use a stable salted fingerprint and user-facing alias; raw latitude/longitude is excluded from Dreams.
- Signal IDs are deterministic from source identity plus event identity where possible, making retries idempotent.

### 5.2 Opportunities

Detectors transform one or more signals into `DreamOpportunity` values.

```kotlin
data class DreamOpportunity(
    val id: String,
    val kind: DreamOpportunityKind,
    val subjectKey: String,
    val titleSeed: String,
    val evidenceIds: List<String>,
    val recurrence: Int,
    val confidence: Float,
    val expectedBenefit: Float,
    val risk: DreamRisk,
    val fingerprint: String
)
```

An opportunity is never directly applied. It must become a validated proposal.

### 5.3 Proposals

Use a typed proposal envelope with a type-specific JSON payload at the persistence boundary:

```kotlin
data class DreamProposal(
    val id: String,
    val runId: String,
    val opportunityId: String,
    val type: DreamProposalType,
    val status: DreamProposalStatus,
    val title: String,
    val summary: String,
    val rationale: String,
    val confidence: Float,
    val risk: DreamRisk,
    val baseRevision: String?,
    val payload: DreamProposalPayload,
    val validation: DreamValidationResult,
    val createdAt: Long,
    val updatedAt: Long
)
```

Proposal payload variants:

- `AutomationRepairPayload`
- `AutomationDraftPayload`
- `TodoRescuePayload`
- `MiniAppEvolutionPayload`
- `AttentionInterventionPayload` (later phase)

Proposal states:

```text
DRAFTED -> VALIDATED -> PENDING_REVIEW
PENDING_REVIEW -> APPLYING -> APPLIED
PENDING_REVIEW -> DISMISSED
PENDING_REVIEW -> SNOOZED
PENDING_REVIEW -> SUPPRESSED
APPLYING -> FAILED
APPLYING -> RECONCILIATION_REQUIRED
```

Invalid transitions fail at the repository boundary.

## 6. Persistence Design

Create a separate `aura_dreams.db` Room database, version 1, rather than increasing coupling between the automation and mini-app databases.

### 6.1 Entities

#### `dream_runs`

- `id: String` primary key
- `status: String`
- `stage: String`
- `windowStart: Long`
- `windowEnd: Long`
- `startedAt: Long`
- `updatedAt: Long`
- `completedAt: Long?`
- `signalCount: Int`
- `opportunityCount: Int`
- `proposalCount: Int`
- `provider: String?`
- `model: String?`
- `promptVersion: String?`
- `inputTokenCount: Int?`
- `outputTokenCount: Int?`
- `errorCode: String?`
- `errorMessage: String?` containing only sanitized diagnostics

#### `dream_signals`

- Fields matching `DreamSignal`
- Index `(kind, occurredAt)`
- Index `(source, subjectId, occurredAt)`
- Unique index on `fingerprint`
- Index on `expiresAt`

#### `dream_opportunities`

- `id`, `runId`, `kind`, `subjectKey`, `fingerprint`
- Scoring components stored individually for auditability
- `score`, `confidence`, `risk`
- Unique index `(runId, fingerprint)`

#### `dream_opportunity_evidence`

- Composite primary key `(opportunityId, signalId)`
- Foreign-key cascade when an opportunity is deleted

#### `dream_proposals`

- Proposal envelope fields
- `payloadJson`
- `validationStatus`, `validationCode`, `validationMessage`
- `baseRevision`
- `decisionAt`, `appliedAt`, `snoozedUntil`
- `applicationKey` equal to proposal ID for idempotency
- Index `(status, createdAt)`
- Index `(runId, createdAt)`

#### `dream_suppressions`

- `fingerprint` primary key
- `scope` (`exact`, `subject`, or `kind`)
- `reason`
- `createdAt`
- `expiresAt: Long?`

#### `dream_trace_events`

- `id`, `runId`, `stage`, `eventType`, `status`
- `detailsJson` with a strict allowlist
- `createdAt`
- Index `(runId, createdAt)`

### 6.2 DAO operations

The DAO must expose transactions for:

- Admitting a run only if the same window is not already active/completed.
- Advancing a run stage with compare-and-set semantics.
- Inserting signals with `IGNORE` on duplicate fingerprints.
- Replacing opportunities and evidence links atomically for a run.
- Persisting a proposal and its validation result atomically.
- Claiming a proposal for application only from `PENDING_REVIEW`.
- Completing or failing an application only from `APPLYING`.
- Pruning expired raw signals without deleting proposal evidence still under retention.
- Deleting all Dream data on user request.

Turn on Room schema export for the new database and commit exported schemas. Add migration tests before creating version 2.

## 7. Settings and Privacy Policy

Create `DreamSettingsStore` using DataStore preferences.

Settings:

- `enabled`, default `false` until onboarding is accepted.
- `runWindowStartLocal`, default `02:00` as a preference, not an exact guarantee.
- `requiresCharging`, default `true`.
- `requiresDeviceIdle`, default `true`.
- `requireBatteryNotLow`, default `true`.
- `maxProposalsPerRun`, default `5`, hard maximum `10`.
- `signalRetentionDays`, default `7`.
- `aggregateRetentionDays`, default `60`.
- `usageAccessEnabled`, default `false`.
- `modelMode`, default `RULES_ONLY`; later values `ON_DEVICE` and `CLOUD_OPT_IN`.
- Excluded package set.
- Suppressed opportunity kinds.

`DreamPrivacyPolicy` is the only component allowed to decide whether source data may become a signal. It returns a structured decision:

```kotlin
sealed interface DreamPrivacyDecision {
    data class Allow(val privacyClass: DreamPrivacyClass) : DreamPrivacyDecision
    data class Redact(val allowedKeys: Set<String>) : DreamPrivacyDecision
    data class Deny(val reason: DreamPrivacyDenialReason) : DreamPrivacyDecision
}
```

Default-denied categories include password managers, authenticators, banking/payment apps, package installer/security settings, incognito/private browser surfaces when detectable, and Aura itself where feedback loops would be misleading.

External strings are evidence, never instructions. A mini-app record, automation error, app label, or model-produced explanation cannot change permissions or workflow control.

## 8. Evidence Sources

Define a narrow source interface:

```kotlin
interface DreamEvidenceSource {
    val source: DreamSignalSource
    suspend fun collect(window: DreamWindow): DreamEvidenceBatch
}
```

Each source has a hard item limit and returns warnings separately from evidence.

### 8.1 Automation source

Repository changes:

- Add bounded queries for runs across all automations by time window.
- Add a bounded query for step runs by a set of run IDs.
- Expose current revision lookup using the existing repository revision behavior.

Collection rules:

- Read no more than 250 runs and 1,000 step attempts per Dream run.
- Normalize volatile error content into a stable failure signature.
- Link failures only to the automation ID, revision, step ID, action type, status, attempt count, and sanitized error class.
- Do not copy arbitrary event `valuesJson` into Dreams.

### 8.2 Todo source

MVP behavior:

- Snapshot current todos through `AssistantRepository.todos()`.
- Parse `created_at` strictly.
- Emit `TODO_STALE` for open items older than a configurable threshold.
- Do not label a task “abandoned” from age alone; use “stale” until activity history exists.

Later behavior:

- Add a lower-level activity event sink rather than importing Dreams into `AssistantRepository`.
- Record todo created, completed, reopened, and renamed events without storing task body beyond what the user already stores.

### 8.3 Mini-app source

Repository changes:

- Add bounded `MiniAppDao.eventsBetween(start, end, limit)`.
- Add bounded record queries where current APIs return unbounded lists.
- Add `MiniAppRepository.events(window, limit)` domain mapping.

Collection rules:

- Reuse `MiniAppEvolutionEngine.suggest(bundle, records)` for the first detector.
- Store field names and aggregate counts, not full record values, in Dream signals.
- Store the bundle ID and current version as the proposal base revision.

### 8.4 Routine source

First release:

- Derive recurring manual automation executions from automation run history.
- Derive named-place/time routines from geofence automation events already stored by Aura.
- Require at least three occurrences across at least two distinct days.

Do not infer a routine from a single day.

### 8.5 Usage source (later permission-gated phase)

Add `UsageStatsDreamSource` backed by `UsageStatsManager`.

- Declare `android.permission.PACKAGE_USAGE_STATS` as an intent permission.
- Provide explicit onboarding into `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- Verify access before every collection; permission may be revoked externally.
- Query only the bounded Dream window.
- Collapse events into app-session aggregates immediately.
- Do not persist the raw event stream.

Android retains detailed usage events for only a few days and most cross-app queries require Usage Access, so daily bounded aggregation is required. See the [UsageStatsManager API](https://developer.android.com/reference/android/app/usage/UsageStatsManager.html).

## 9. Detectors and Scoring

All detectors are pure Kotlin classes with no Android, Room, network, or model dependency.

```kotlin
interface DreamDetector {
    val kind: DreamOpportunityKind
    fun detect(input: DreamDetectorInput): List<DreamOpportunity>
}
```

### 9.1 Automation failure detector

Initial threshold:

- At least two terminal failures in seven days.
- Same automation revision.
- Same normalized failing step signature.
- Current automation revision still matches the failed revision.

Suppress when:

- A newer revision exists.
- A later successful run proves recovery.
- The automation is disabled or deleted.
- Failure is solely a denied runtime permission; emit a permission-resolution proposal instead of a repair.

### 9.2 Stale task detector

Initial threshold:

- Task is open.
- Age is at least seven days.
- No duplicate proposal is already pending.

Proposal options should include:

- Break into next actions.
- Schedule a review.
- Mark done.
- Dismiss the suggestion.

Only “schedule review” can become an automation draft. Task rewriting stays a reviewable text preview.

### 9.3 Mini-app evolution detector

- Run the existing evolution engine over bounded record samples.
- Require the current bundle version to match when validating and applying.
- Preserve the existing preview, migration-plan, version, and rollback path.

### 9.4 Repeated routine detector

- Group events by subject and local time bucket.
- Require three occurrences, two distinct days, and bounded time variance.
- Exclude routines that already have an equivalent scheduled/geofenced automation.
- Create a disabled automation draft payload, never an active rule.

### 9.5 Distraction loop detector

Later phase thresholds:

- Three or more foreground sessions for the same app within 45 minutes.
- Median session shorter than five minutes.
- Pattern observed on at least two days before proposing a durable intervention.

Present neutral language. Do not classify an app or person as addictive.

### 9.6 Score policy

Use explicit components, not one opaque model score:

```text
score =
    0.30 * recurrence
  + 0.25 * detector confidence
  + 0.20 * expected benefit
  + 0.15 * recency
  + 0.10 * source quality
  - risk penalty
  - prior-dismissal penalty
```

Selection policy:

- Minimum score: `0.60`.
- Maximum five proposals per run by default.
- Maximum two proposals of the same type.
- High-risk proposals cannot be generated in the first release.
- Exact or scoped suppressions always win over score.
- Ties are ordered deterministically by fingerprint.

## 10. Proposal Generation and Model Boundary

### 10.1 Rules-only generator

The initial `RuleBasedDreamProposalEngine` produces all required proposal fields from typed opportunities and templates. This guarantees useful output without a model or network.

### 10.2 Optional model interface

```kotlin
interface DreamProposalModel {
    suspend fun enrich(request: DreamModelRequest): DreamModelResult
}
```

`DreamModelRequest` contains only:

- Opportunity kind.
- Bounded aggregate evidence.
- Allowed proposal types.
- Current automation/mini-app schema fragment when required.
- Explicit safety constraints.
- Locale.

It excludes raw repository entities, API keys, auth tokens, precise location, screenshots, and unrestricted user content.

The response is parsed into a strict schema. Invalid JSON receives at most one repair request. Failure, unavailability, quota exhaustion, oversized context, unsupported language, or timeout falls back to the rule-based proposal.

Prompts live in versioned source files or versioned Kotlin constants with snapshot tests. Record prompt version and model identity in the Dream run, but never record private prompt payloads in logs.

### 10.3 Backend mode (future)

If cloud mode is approved later:

- Add `POST /api/dreams/proposals` with authenticated, bounded request/response models.
- Reuse the existing provider selection and LLM boundary, but create a Dreams-specific structured prompt contract.
- Do not reuse the conversational assistant endpoint.
- Add request timeouts and do not retry non-idempotent operations; proposal generation is idempotent by `run_id + opportunity_id`.
- Never send signals unless the settings screen explicitly enables cloud Dreams.

## 11. Validation and Shadow Execution

`DreamProposalValidator` dispatches by proposal type.

### Automation drafts and repairs

1. Parse payload into `AutomationSpec`.
2. Call `AutomationValidator.validate`.
3. Confirm all action types are currently supported.
4. Run `AutomationPermissionPlanner` and store missing requirements.
5. Confirm `baseRevision` still matches for repairs.
6. Compare old/new specs and produce a bounded semantic diff.
7. Mark as `VALIDATED` only; do not call the executor.

Do not perform live cross-app dry runs overnight. A “tested repair” in the first release means schema validation, revision validation, selector-shape validation, and permission planning passed. A user-initiated preview can later inspect the current screen without executing external sends.

### Mini-app evolution

1. Confirm bundle and version still exist.
2. Generate or load `MiniAppRevisionPreview`.
3. Run the existing mini-app validator.
4. Validate the migration plan against current record field names.
5. Store a preview payload; do not call `applyRevision`.

### Task rescue

1. Confirm task still exists and remains open.
2. Bound proposed subtasks and title lengths.
3. Reject destructive replacements.
4. Treat the proposal as text until the user chooses an action.

## 12. Approval and Application Safety

`DreamProposalApplier` is the only Dreams component allowed to call side-effecting repositories.

Application sequence:

1. Atomically claim `PENDING_REVIEW -> APPLYING` using proposal ID as the idempotency key.
2. Reload the current target from its source repository.
3. Revalidate permissions, base revision, and payload.
4. Record an application trace event before the side effect.
5. Execute exactly one typed operation.
6. Persist `APPLIED` with the resulting target ID/revision.
7. Refresh triggers or UI state through the existing owning runtime.

Typed operations:

- Automation creation/repair: `AutomationRuntime.upsertAndRestore`.
- Mini-app revision: `MiniAppRepository.applyRevision`.
- Todo update: a new bounded `AssistantRepository.updateTodo(...)` method only when that phase is implemented.

Crash policy:

- Never automatically repeat an interrupted side effect.
- Leave the proposal in `RECONCILIATION_REQUIRED`.
- On next foreground launch, compare the stored expected result with the current repository state.
- Mark applied only if the exact expected revision exists; otherwise return it to review with a diagnostic.

Conflict policy:

- If the automation or mini-app changed after the proposal was generated, mark `STALE` and offer regeneration.
- Never overwrite newer user changes.

## 13. Background Scheduling

Add:

```kotlin
implementation("androidx.work:work-runtime-ktx:2.11.2")
androidTestImplementation("androidx.work:work-testing:2.11.2")
androidTestImplementation("androidx.room:room-testing:2.8.4")
```

WorkManager 2.11.2 is the current stable release as of the plan date. See [AndroidX WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work).

### Scheduler behavior

- Enqueue unique periodic work named `aura-dreams-nightly`.
- Repeat every 24 hours with an appropriate flex interval.
- Apply `requiresCharging`, `requiresBatteryNotLow`, and optional `requiresDeviceIdle` constraints.
- Do not require network in rules-only or on-device mode.
- Require unmetered network only if a future cloud mode setting requests it.
- Use update/replace policy when settings change.
- Cancel unique work immediately when Dreams is disabled.
- Provide a separate unique one-time `aura-dreams-manual` worker for “Dream now.”

WorkManager execution is approximate and may be delayed or skipped when constraints remain unmet. The UI must say “Runs while your phone is idle overnight,” not promise an exact time. WorkManager supports persistent unique work, constraints, retries, and reboot recovery. See [Android task scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent) and [work request constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

### Worker behavior

`DreamWorker` extends `CoroutineWorker` and resolves `AppContainer` through `AuraApplication`.

- Return `Result.success()` for completed, disabled, or no-op runs.
- Return `Result.retry()` only for explicitly transient storage/provider failures.
- Return `Result.failure()` for invalid configuration or non-recoverable schema failure.
- Preserve `CancellationException`.
- Stop cleanly at stage boundaries when constraints are revoked.
- Keep a normal run below the standard worker time budget by bounding all sources and proposals.
- Do not use a long-running foreground worker in the first release.

## 14. UI and User Experience

### 14.1 New view model

Create `DreamsViewModel` with a focused `DreamsUiState`:

- Last completed run summary.
- Pending proposals.
- Selected proposal and evidence.
- Run-in-progress state and current stage.
- Settings summary and permission state.
- Applying proposal ID.
- User-facing error/warning.

Actions:

- `refresh()`
- `runNow()`
- `openProposal(id)`
- `applyProposal(id)`
- `dismissProposal(id)`
- `snoozeProposal(id, duration)`
- `suppressProposal(id, scope)`
- `regenerateProposal(id)`
- `cancelRun()`
- `deleteDreamHistory()`

### 14.2 Navigation

- Add a `dreams` route to the existing navigation graph.
- Add a compact morning card to Home only when proposals are pending.
- Add a Dreams entry to Settings for controls and data management.
- Keep the detailed screen in `LauncherDreamsUi.kt` rather than expanding `AuraLauncherApp.kt`.

### 14.3 Morning report

Header:

```text
Aura dreamed for 18 seconds
4 patterns found · 3 proposals ready · nothing changed automatically
```

Each proposal card shows:

- What Aura noticed.
- Number and date range of supporting observations.
- Confidence label.
- Expected benefit.
- Risk and required permissions.
- Affected automation, task, mini-app, or app.
- Semantic diff or preview.
- Apply, Edit, Snooze, Dismiss, and “Never suggest this again.”
- “Why does Aura know this?” evidence view.

Avoid claiming a repair was tested if only structural validation ran. Use exact language such as “Prepared and validated a repair draft.”

### 14.4 Notification

Post at most one summary notification per completed run and only when at least one proposal passes the score threshold:

```text
Aura found 3 improvements
Two repair drafts and one routine are ready to review.
```

The notification opens the Dreams screen. It never contains sensitive evidence.

## 15. Existing Files to Modify

### Android build and application wiring

- `android/app/build.gradle.kts`
  - Add WorkManager runtime/testing dependencies.
  - Add Room migration testing dependency.
  - Later add ML Kit Prompt API behind a clearly isolated phase.
- `android/app/src/main/AndroidManifest.xml`
  - Add Usage Stats declaration only in the permission-gated phase.
  - No new service declaration is required for standard WorkManager integration.
- `android/app/src/main/java/com/aura/app/AppContainer.kt`
  - Construct Dreams database, repository, sources, detectors, scorer, validator, applier, orchestrator, and scheduler.
- `android/app/src/main/java/com/aura/app/AuraApplication.kt`
  - Reconcile Dreams scheduling on startup after settings load.

### Source repositories

- `AutomationDao.kt` and `AutomationRepository.kt`
  - Add bounded time-window run and step queries.
- `MiniAppDao.kt` and `MiniAppRepository.kt`
  - Add bounded event and record queries.
- `AssistantRepository.kt`
  - No Dreams dependency.
  - Add typed todo mutation only when task-rescue application is implemented.

### UI

- `AuraLauncherApp.kt`
  - Register route and pass the minimal navigation hook.
- `LauncherHomeUi.kt`
  - Add compact Dream summary card.
- `LauncherSettingsUi.kt`
  - Add settings and privacy controls.

## 16. Delivery Milestones

Each milestone should be a reviewable change, ideally below roughly 500 non-mechanical changed lines where practical.

### Milestone 1 — Domain and persistence foundation

Implement:

- Domain enums and types.
- Dreams Room entities, DAO, database, repository.
- Run and proposal state-transition validation.
- Settings store.
- Privacy policy skeleton.

Acceptance criteria:

- Database initializes from empty state.
- Duplicate run/signal admission is idempotent.
- Illegal proposal transitions fail with typed errors.
- Delete-all removes every Dreams record.
- No existing Aura behavior changes.

### Milestone 2 — Existing-data evidence collection

Implement:

- Automation run/step bounded queries.
- Todo snapshot source.
- Mini-app event/record bounded queries.
- Routine source from existing Aura run events.
- Redaction and retention.

Acceptance criteria:

- A seven-day fixture produces deterministic signals.
- Sensitive or unknown attributes are rejected.
- Source failure produces a warning while other sources continue.
- Collection bounds are enforced.

### Milestone 3 — Detectors, scoring, and rules-only proposals

Implement:

- Four first-release detectors.
- Scoring and diversity policy.
- Suppression behavior.
- Rule-based proposal engine.
- Proposal validators and semantic diffs.

Acceptance criteria:

- Repeated failures produce one deduplicated repair candidate.
- A later success suppresses a stale failure diagnosis.
- Old open tasks are labeled stale, not abandoned.
- Equivalent existing routines are not proposed.
- A run never emits more than configured limits.

### Milestone 4 — Orchestrator and manual Dream run

Implement:

- Durable orchestrator stage machine.
- Trace events.
- Cancellation and retry/recovery behavior.
- “Dream now” one-time WorkManager request.
- Developer-only diagnostics screen or log view.

Acceptance criteria:

- Process interruption resumes at the correct stage.
- Re-running the same window does not duplicate proposals.
- Cancellation leaves a terminal, explainable run state.
- No side-effecting Aura repository is called.

### Milestone 5 — Morning report and feedback

Implement:

- `DreamsViewModel`.
- Dreams route and report UI.
- Evidence detail, dismiss, snooze, and suppress.
- Home summary card and notification.
- Dreams settings and delete-history controls.

Acceptance criteria:

- Every claim links to persisted evidence.
- Empty, disabled, running, failed, and completed states render correctly.
- Dismissal and suppression survive restart.
- Notification contains no sensitive details.

### Milestone 6 — Explicit proposal application

Implement:

- `DreamProposalApplier`.
- Automation draft/repair application using `AutomationRuntime.upsertAndRestore`.
- Mini-app evolution handoff to existing preview/application flow.
- Base-revision conflict detection.
- Application reconciliation after interruption.

Acceptance criteria:

- No proposal applies without an explicit UI event.
- Double taps and worker retries do not duplicate side effects.
- Newer user edits are never overwritten.
- Failed/interrupted applications become explainable review states.
- Applied automation changes retain existing trigger restoration behavior.

### Milestone 7 — Nightly scheduling and onboarding

Implement:

- Unique periodic WorkManager scheduling.
- Charging, battery, idle, and mode-specific network constraints.
- Startup/settings schedule reconciliation.
- First-run privacy onboarding.
- Single summary notification.

Acceptance criteria:

- Enabling creates one unique schedule.
- Disabling cancels it.
- Settings updates do not create duplicate workers.
- Reboot preserves scheduled work.
- UI copy does not promise exact execution time.

### Milestone 8 — Usage patterns

Implement:

- Usage Access onboarding/status resolver.
- Bounded `UsageStatsManager` source.
- Session aggregation and raw-event discard.
- Distraction-loop detector and neutral interventions.
- Package exclusion controls.

Acceptance criteria:

- Denial/revocation degrades cleanly.
- Raw usage events are never persisted.
- Excluded packages produce no signals.
- One anomalous evening does not produce a durable rule proposal.

### Milestone 9 — On-device model enrichment

Implement:

- `DreamProposalModel` interface and rule-based fallback.
- Gemini Nano availability/status checks.
- Bounded context renderer.
- Strict response schema and one objective repair pass.
- Prompt versioning and fake-model harness tests.

Acceptance criteria:

- Unsupported devices behave exactly like rules-only mode.
- Provider failure never loses deterministic proposals.
- Prompt input stays below configured bounds.
- Model output cannot bypass validation or approval.
- No real provider calls occur in tests.

### Milestone 10 — Hardening and staged rollout

Implement:

- Retention/pruning jobs.
- Performance and battery instrumentation.
- Local feature flag or build flag for staged exposure.
- Regression fixtures derived from anonymized test scenarios.
- Accessibility and responsive-layout review.

Release gates:

- Zero known silent mutation paths.
- Zero raw sensitive payloads in Dream database or logs.
- Crash/restart and double-apply scenarios pass.
- Detector precision is acceptable on curated scenario fixtures.
- Existing automation and mini-app suites remain green.

## 17. Test Plan

### 17.1 Pure unit tests

Add under `android/app/src/test/java/com/aura/app/dreams/`:

- `DreamPrivacyPolicyTest.kt`
- `AutomationFailureDetectorTest.kt`
- `StaleTaskDetectorTest.kt`
- `MiniAppEvolutionDreamDetectorTest.kt`
- `RepeatedRoutineDetectorTest.kt`
- `DistractionLoopDetectorTest.kt`
- `DreamScorerTest.kt`
- `DreamProposalValidatorTest.kt`
- `DreamRunStateMachineTest.kt`
- `DreamContextRendererTest.kt`

Required cases:

- Empty evidence.
- Duplicate and out-of-order signals.
- Boundary thresholds.
- Permission-related failure versus repairable failure.
- Newer revision conflict.
- Suppression precedence.
- Maximum context and proposal bounds.
- Invalid/unknown enum and malformed payload JSON.
- Prompt-injection-shaped text treated as inert data.

### 17.2 Repository and migration tests

- In-memory Room repository tests for transactions and compare-and-set transitions.
- Migration test from each future schema version.
- Expiry pruning while retained proposal evidence still exists.
- Corrupt payload handling without destructive fallback.

### 17.3 WorkManager tests

Use `androidx.work:work-testing`:

- Unique scheduling.
- Constraint handling.
- Disabled/no-op result.
- Retry only for transient errors.
- Cancellation.
- No duplicate run after worker restart.

Avoid time-based sleeps; drive the worker and clock with fakes.

### 17.4 Application harness tests

Create fake evidence sources, fake clock, fake proposal model, fake notification publisher, and in-memory/fake target repositories.

Scenarios:

1. One repair candidate completes cheaply without a model.
2. Multiple source failures still produce a partial report with warnings.
3. Model returns invalid JSON, repair fails, rules-only fallback survives.
4. Proposal approval conflicts with a newer automation revision.
5. Side effect succeeds but persistence is interrupted; reconciliation finds the exact revision.
6. Side effect outcome is ambiguous; no automatic replay occurs.
7. A malicious mini-app field value cannot become an instruction.
8. A long evidence set is deterministically compacted.

### 17.5 UI tests

- Morning report empty/loading/failure/success states.
- Evidence sheet.
- Apply confirmation and stale-proposal conflict.
- Dismiss/snooze/suppress controls.
- Narrow phone layout.
- Accessibility semantics for confidence, risk, and actions.
- Notification deep link to the correct proposal.

### 17.6 Verification commands

Run incrementally:

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

When database, WorkManager, or Compose integration is added, also run the relevant instrumentation tests and the existing API 34–36 managed-device matrix where feasible.

If the future cloud endpoint is added:

```bash
cd backend
pytest
```

## 18. Observability and Product Evaluation

Every Dream run records stage timing, counts, warnings, detector versions, validation outcomes, and sanitized errors.

Never log:

- Signal attribute payloads containing user text.
- API keys or auth headers.
- Exact task or memory content.
- Raw coordinates.
- Full app usage event streams.
- Model prompts containing evidence.

Local product metrics:

- Proposal view rate.
- Apply, edit, dismiss, snooze, and suppress rates by proposal kind.
- Reversion rate after applying.
- Stale/conflict rate.
- Detector precision from explicit feedback.
- Runs skipped for constraints or permissions.
- Runtime duration and signal/proposal counts.

Success gates for expanding beyond the first four detectors:

- At least half of viewed proposals receive a positive action in internal testing.
- Low “never suggest this again” rate for each detector.
- Automation repair proposals have a very low reversion/failure rate.
- Nightly processing remains bounded and has no material battery regression.

## 19. Residual Risks

### False confidence

Structural validation cannot prove a cross-app automation will work later. UI copy and validation status must distinguish schema validation from user-initiated live preview.

### Sparse evidence

New users will have little data. Do not manufacture a report; show “Aura is still learning” and the exact minimum observations needed.

### Background scheduling variability

WorkManager does not guarantee an exact nightly time. The product promise must be based on idle conditions and eventual completion.

### Source inconsistency

Todos may be local or cloud-backed, while automations and mini-apps are local. Every signal needs source and freshness metadata, and a proposal must re-read current state before applying.

### Model/device availability

Gemini Nano is optional and beta. Rules-only behavior remains the supported baseline.

### Sensitive behavioral inference

Even aggregate app usage can feel invasive. Usage-derived Dreams stays separately opt-in, inspectable, excludable per app, and deletable.

## 20. Recommended First Implementation Slice

Implement Milestones 1–3 first and expose them through a temporary developer-triggered repository test or debug action. Do not add scheduling, notifications, Usage Access, or model inference in the first code change.

That slice proves the hardest product invariant:

> Aura can turn existing local evidence into a small number of deterministic, explainable, validated proposals without changing anything.

After that invariant passes tests, add the manual Dream run and Morning Report, then explicit application, and only then unattended scheduling.

