# Android Lint Baseline Audit

Last reviewed: 2026-06-30

The lint baseline is still required for legacy launcher assets, dependency update noise, and a few known follow-up items. New code should not add entries to `lint-baseline.xml` without documenting why the warning is acceptable.

## Fixed in this pass

- Removed obsolete SDK checks in `AuraListeningService` now that `minSdk` is 26.
- Removed an obsolete SDK check in `AndroidAutomationActionExecutor`.
- Replaced package and SMS `Uri.parse` calls with `String.toUri()` in automation permission/action code.
- Removed stale `ObsoleteSdkInt`, `QueryAllPackagesPermission`, and duplicate `UseKtx` suppressions from `lint-baseline.xml`.

## Remaining high-signal items

- `SetJavaScriptEnabled` in `AuraLauncherApp.kt`: keep only if the mini-app runtime remains fully local and sanitized. Re-audit whenever remote content or user-supplied HTML is introduced.
- `InvalidFragmentVersionForActivityResult` in `LauncherActivity.kt`: upgrade or align the Activity/Fragment dependency stack instead of relying on the baseline.
- `IconExtension`, `IconLauncherShape`, and `MonochromeLauncherIcon`: regenerate launcher assets with correct formats and adaptive icon metadata.
- `AutoboxingStateCreation`: replace boxed Compose state with primitive state helpers during nearby UI work.
- Dependency/version warnings: update as a batch after confirming Compose BOM and AGP compatibility.
