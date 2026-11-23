# Design Decisions

Record the **why**, not just the what. One entry per decision.

| Topic | Decision | Why | Alternatives considered |
|-------|----------|-----|-------------------------|
| Endpoint structure | One explicit router over a root JDK HTTP context; dynamic segments use `{name}` | Resolving paths before methods distinguishes an unknown route (404) from an unsupported method on a known route (405), without adding a framework. | One context per route; framework router |
| Error format | One envelope: `error.code`, `error.message`, and optional `error.details` | Clients can implement one error parser; validation details remain available without changing other error responses. | Endpoint-specific response shapes |
| Exception mapping | Validation -> 400; typed missing-resource -> 404; duplicate genre -> 409; unexpected repository failures -> 500 | HTTP status translation belongs at the HTTP boundary; services should throw domain/repository exceptions rather than write responses. | Services returning HTTP status codes; catch-all 500 |
| Data model | | | |
| Default page size | Film `per_page` defaults to 10; service caps it at 100 | Seed data explicitly documents a default page size of 10, keeping API behavior aligned with the prepared fixture. | Return all films; default 20 |
| Timestamps | ISO-8601 UTC strings in TEXT columns | | |
| Handler scope | Expose only operations available in current service methods; document missing API capabilities rather than implementing business/data access in handlers | Preserves the handler -> service -> repository boundary and avoids guessing a feature contract. | Inventing behavior in handlers |
