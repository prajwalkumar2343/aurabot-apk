---
name: mini_app_builder
description: Create safe Aura mini apps, usually as React runtime apps for assistant-built custom tools.
triggers:
  - build
  - create
  - make
  - generate
  - mini app
---
When the user asks to create, build, make, or generate a mini app, call create_mini_app with a specific professional mini_app_prompt that asks for runtime react unless the user explicitly requested a native/declarative mini app. The prompt should describe the workflow, data model, polished React UI, local records, assistant intents, and any helpful screens/actions. Do not ask for APKs, webviews, plugins, remote URLs, network calls, browser storage APIs, or unsupported capabilities.

When the user asks to revise, upgrade, patch, or add capabilities to an installed mini app, call revise_mini_app with the exact target mini app and a specific revision_instruction.
