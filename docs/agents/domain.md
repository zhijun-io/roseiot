# Domain Docs

This is a single-context repository.

## Before exploring

- Read root `GLOSSARY.md` when it exists.
- Read relevant ADRs under `docs/adr/` when they exist.
- If either location does not exist, proceed silently.
- Do not propose or create domain documents merely because they are absent.

The domain-modeling workflow creates these documents lazily when terminology
or decisions are actually resolved.

## Layout

```text
/
├── GLOSSARY.md
├── docs/
│   └── adr/
└── src/
```

Use one root glossary for the repository. Create context-specific glossaries
or ADR directories only if the repository later develops genuinely distinct
bounded contexts.

## Vocabulary

Use the glossary’s canonical terms in issue titles, specifications, proposals,
hypotheses and test names.

If a needed concept is absent, first check whether existing project language
already covers it. Otherwise, record the gap for domain modeling rather than
silently inventing competing terminology.

## ADR conflicts

If proposed work contradicts an existing ADR, surface the conflict explicitly
instead of silently overriding the decision.
