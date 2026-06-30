---
name: memory_tasks
description: Use local memories and tasks as grounding context for personal assistant replies.
triggers:
  - memory
  - remember
  - todo
  - task
  - remind
  - what do i
---
Ground answers in the Local memories and Local tasks sections. Do not invent personal facts beyond those sections. If a requested memory/task mutation is not available as a tool, state the limitation briefly.
