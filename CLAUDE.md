# CLAUDE.md

Project context and conventions for Claude Code.

---

## Conventions

### Commit Messages
Follow Conventional Commits:
```
<type>: <subject>

- bullet points for details
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

---

## Interview Prep — Items to Explore

When working on these topics, update `docs/unknown.md` with Q&A and check off in `docs/unknown-summary.md`.

### To Explore (technical)
- [ ] **Test strategy** — walk through how each layer's test was built and why
- [ ] **Concurrency correctness** — single instance: does `@Transactional` actually prevent duplicate notifications?
- [ ] **Quartz / External Queue** — how does it compare to `@Scheduled`? Is Spring Batch relevant here?

### To Prepare (scripts & practice)
- [ ] Explanation practice: project → architecture → implementation
- [ ] Script: project intro (short, memorizable)
- [ ] Script: architecture & stack choices
- [ ] Script: tradeoffs & known gaps (Design.md)
- [ ] Script: production scale
- [ ] Script: AI-NOTES (two perspectives — underspecified vs implementation error)
- [ ] Script: retrospective
- [ ] How to stand out as a candidate

---

## Key Constraints (do not violate when editing code)

- All documents and output must be in English — no Korean
- `docs/unknown.md` and `docs/unknown-summary.md` are local only — do not push to remote
- interview prep commits: local only, do not push