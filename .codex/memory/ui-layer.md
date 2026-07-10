---
name: ui-layer
description: Codex mirror of durable Joyufy UI-layer notes
metadata:
  type: project
---

# UI Layer Notes

- i18n uses `interface Strings` plus singleton implementations `StringsEn` and `StringsEs`, exposed through `LocalStrings`.
- Do not convert `Strings` back to a data class. The string set is large enough to exceed the JVM method-argument limit for generated constructors/copy methods and can crash startup with `ClassFormatError`.
