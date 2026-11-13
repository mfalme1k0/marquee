# Contributing to Marquee

## Who owns what

Each layer has one owner. The owner is responsible for that layer's code, its
unit tests, and its section of the docs.

| Layer | Package | Owner |
|-------|---------|-------|
| Handlers (HTTP in/out) | `com.marquee.handler` | Francis Kimani  |
| Services (validation, rules) | `com.marquee.service` | Fidel Shikokoti, project lead  |
| Repositories (SQL) | `com.marquee.repository` | Jackson Macharia  |
| Shared plumbing: `model`, `http`, `db`, build and CI | `com.marquee.model`, `com.marquee.http`, `com.marquee.db` | Fidel Shikokoti (lead). Anyone may change these through a PR. |

Because each layer only calls the one below it, agree on the method signatures
between layers early (handler -> service -> repository) and write them down in
the PR or issue before implementing. If your PR touches a layer you don't own,
request a review from that layer's owner as well.

## Workflow

1. Pick an issue (or task from the board) and assign yourself.
2. Branch from an up-to-date `main`:
   `git checkout -b feat/films-repository`
3. Commit small and often. Each commit does one thing.
4. Push and open a pull request into `main` using the PR template.
5. Get **at least one approving review** from a teammate and a **green CI**.
6. Squash-merge. The branch is deleted automatically.

Nobody pushes directly to `main`, including the project lead. This is enforced
by branch protection.

## Branch names

`feat/<topic>`, `fix/<topic>`, `docs/<topic>`, `test/<topic>`, `chore/<topic>`

## Commit messages

Format: `<type>: <imperative summary>` (72 characters or fewer)

```
feat: add FilmRepository.findById
feat: validate film payload in FilmService
fix: enable foreign keys on every connection
docs: document pagination defaults
test: cover 405 on /genres
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`.

## Pull requests

- Keep PRs small and focused (aim for under ~400 changed lines).
- Describe what changed, why, and how you tested it.
- Review within one working day. Default review rotation:
  **Fidel's PRs -> Francis, Francis's PRs -> Jackson, Jackson's PRs -> Fidel**
  (anyone may review more).
- Reviewers: check layering, error envelope, status codes, tests, docs.
- Resolve all conversations before merging.

## Code rules

- Respect the layers: handler -> service -> repository.
    - **Handlers** parse HTTP and write HTTP. No business rules, no SQL.
    - **Services** validate and decide. No HTTP types, no SQL.
    - **Repositories** run SQL. No business decisions, no HTTP types.
- All errors use the single envelope: `{"error": {"code", "message", "details"}}`.
- Hand-written SQL with **parameterised queries** only. Never concatenate user input.
- Every connection runs `PRAGMA foreign_keys = ON`.
- The server must never crash on bad input; every failure maps to a status code.
- New or changed endpoints update `docs/api.md` and the test collection in the same PR.

## Definition of done

- [ ] Works, and demonstrated with curl or the test collection
- [ ] Error cases return correct status code and envelope
- [ ] Unit tests added for your layer (`mvn verify` passes)
- [ ] Docs updated (`docs/api.md`, design decisions if you made a choice)
- [ ] Reviewed and approved by a teammate

## Local setup

```bash
mvn -B verify
java -jar target/marquee.jar
```