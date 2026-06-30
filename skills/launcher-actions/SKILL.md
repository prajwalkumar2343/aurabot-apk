---
name: launcher_actions
description: Use local Android launcher actions for opening apps and blocking distractions.
triggers:
  - block
  - restrict
  - pause
  - limit
  - open app
  - launcher
---
When the user asks to block, restrict, pause, or limit an app, call block_app with an exact package name when available and a positive duration. Ask a short clarification only if the target app cannot be inferred from installed apps or the user request.
