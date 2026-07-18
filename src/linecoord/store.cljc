(ns linecoord.store
  "SSoT for the ISCO-08 7413 electrical line installers and repairers
  crew dispatch scheduling/logistics coordination actor (itonami
  actor pattern, ADR-2607121000 / CLAUDE.md Actors section; README's
  'Robotics premise' — a crew dispatch scheduling/logistics
  coordination robot proposes crew/dispatch scheduling, service-record
  logging, safety-concern flags and line/pole-materials order
  coordination under this advisor/governor pair, which never
  dispatches hardware itself, never performs power-line work, and
  never finalizes a power-line-work-execution decision, authorizes a
  de-energization/re-energization/lockout clearance, or overrides a
  utility-safety officer's judgment). Modeled on
  cloud-itonami-isco-7214's steelcoord.store.

  Domain:

    circuit — a registered electrical distribution circuit / service
              territory (:circuit-id, :name, :territory).
    worker  — a registered lineworker crew member {:worker-id
              :circuit-id :name :role}, belonging to exactly one
              registered circuit.
    record  — a committed operating record (a logged service record,
              scheduling proposal, safety-concern flag or supply-order
              coordination entry) — written ONLY via commit-record!.
              This actor coordinates crew dispatch scheduling/logistics
              ONLY — a `record` is a coordination artifact, never a
              power-line-work-execution act, a de-energization/
              re-energization/lockout-clearance authorization, or a
              utility-safety-officer-judgment override.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (circuit [s circuit-id])
  (worker [s worker-id])
  (records-of [s circuit-id])
  (ledger [s])
  (register-circuit! [s circuit])
  (register-worker! [s w])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (circuit [_ circuit-id] (get-in @a [:circuits circuit-id]))
  (worker [_ worker-id] (get-in @a [:workers worker-id]))
  (records-of [_ circuit-id] (filter #(= circuit-id (:circuit-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-circuit! [s circuit]
    (swap! a assoc-in [:circuits (:circuit-id circuit)] circuit) s)
  (register-worker! [s w]
    (swap! a assoc-in [:workers (:worker-id w)] w) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:circuits {} :workers {} :records [] :ledger []}
                                   seed)))))
