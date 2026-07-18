# cloud-itonami-isco-7413

Open Occupation Blueprint for **ISCO-08 7413**: Electrical Line Installers and Repairers.

This repository designs a forkable OSS business for an electrical-line-installation-and-repair crew dispatch coordination service: a crew dispatch scheduling/logistics coordination robot manages service-record logging, crew/dispatch scheduling, safety-concern flagging and line/pole-materials order coordination under a governor-gated actor, so the practice keeps its own operating records instead of renting a closed crew-dispatch SaaS.

**This actor coordinates CREW/DISPATCH SCHEDULING/LOGISTICS ONLY — it never performs power-line work itself.** Electrical line installers and repairers install, maintain and repair high-voltage overhead and underground power lines, working live/de-energized at height — combining electrocution risk with fall-from-height risk, one of the highest-hazard trades in this rollout. The actor's closed op-allowlist contains no op that directly finalizes a power-line-work-execution decision, authorizes a de-energization/re-energization/lockout clearance, or overrides a utility-safety officer's judgment. Any proposal that attempts any of these is a hard, permanent block, never overridable by human approval.

**Maturity: `:implemented`.** `src/linecoord/` implements the
`LineCoordActor` as a `langgraph.graph/state-graph`
(`linecoord.actor`) wired to an `Electrical Line Crew Coordination
Advisor` (`linecoord.advisor`) and an independent `LineCoordGovernor`
(`linecoord.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok? true) +-> :request-approval (:escalate? true, human-in-the-loop
interrupt) +-> :hold (:hard? true)`. See `clojure -M:test` output for
the current test/assertion counts.

HARD invariants (always `:hold`, never overridable): the electrical
distribution circuit / service territory must be independently
verified/registered before any action; a referenced worker must be a
registered crew member belonging to that circuit; `:effect` must be
`:propose` only (no hardware dispatch, no power-line work performed);
the closed op-allowlist is enforced (no op in the allowlist finalizes
power-line-work execution, authorizes a de-energization/
re-energization/lockout clearance, or overrides utility-safety
authority); and any proposal that attempts to directly finalize a
power-line-work-execution decision, authorize a de-energization/
re-energization/lockout clearance, or override a utility-safety
officer's judgment is a hard, **permanent** block — detected as
finalization/execution action phrases (never bare nouns like "power
line"/"voltage"/"pole"/"transformer", which are ordinary vocabulary
for this domain and must not false-trip the guard).

Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (every surfaced weather-hazard/
equipment-condition/outage-report concern) and
`:coordinate-supply-order` above the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a crew dispatch scheduling/logistics coordination robot performs service-record logging, crew/dispatch-schedule proposals, safety-concern surfacing and line/pole-materials order coordination under an actor that proposes
actions and an independent **Electrical Line Crew Coordination Governor** that gates them. The governor never
dispatches hardware itself, never performs power-line work, never authorizes a de-energization/re-energization/lockout clearance, and never overrides a utility-safety officer's judgment; `:high`/`:safety-critical` actions (such as a safety-concern flag or an above-threshold supply order) require human sign-off.

## Core Contract

```text
circuit roster + crew roster + dispatch schedule
        |
        v
Electrical Line Crew Coordination Advisor -> LineCoordGovernor -> log record/schedule/order, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a power-line-work-execution decision, authorize a
de-energization/re-energization/lockout clearance, override a
utility-safety officer's judgment, suppress an operating record, or
disclose sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7413`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
