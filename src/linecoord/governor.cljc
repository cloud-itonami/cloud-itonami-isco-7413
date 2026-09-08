(ns linecoord.governor
  "LineCoordGovernor — the independent safety/traceability layer named
  in this repository's README/business-model.md, gating every crew
  dispatch scheduling/logistics coordination proposal an advisor may
  make for an electrical distribution circuit. The governor never
  dispatches hardware itself, never performs power-line work, and
  never allows a proposal to finalize a power-line-work-execution
  decision, authorize a de-energization/re-energization/lockout
  clearance, or override a utility-safety officer's judgment — this
  actor coordinates CREW/DISPATCH SCHEDULING/LOGISTICS ONLY. Modeled
  on cloud-itonami-isco-7214's steelcoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. circuit provenance        — the electrical distribution circuit
                                 / service territory must be
                                 independently verified/registered
                                 before any action.
    2. no-actuation            — proposal :effect must be :propose
                                 (the governor never dispatches
                                 hardware and never performs
                                 power-line work; it only gates what
                                 the advisor may coordinate).
    3. closed op-allowlist     — :op must be one of the four
                                 coordination ops
                                 (:log-service-record,
                                 :schedule-crew-operation,
                                 :flag-safety-concern,
                                 :coordinate-supply-order). No op that
                                 directly finalizes power-line-work
                                 execution, authorizes a
                                 de-energization/re-energization/
                                 lockout clearance, or overrides
                                 utility-safety authority exists in
                                 this allowlist.
    4. circuit-mismatch        — if the proposal names a circuit, it
                                 must be the SAME circuit verified for
                                 this request (defense-in-depth
                                 against a proposal quietly targeting
                                 a different, unverified circuit).
    5. worker basis            — if the proposal references a worker,
                                 that worker must be a REGISTERED crew
                                 member belonging to this circuit (an
                                 unregistered or foreign-circuit
                                 worker reference is not a routine
                                 scheduling proposal).
    6. scope-exclusion         — a proposal that attempts to finalize
                                 a power-line-work-execution decision,
                                 to authorize a de-energization, a
                                 re-energization or a lockout
                                 clearance, or to override a
                                 utility-safety officer's judgment, is
                                 a hard, PERMANENT block — never
                                 overridable by human approval,
                                 regardless of confidence or stake.
                                 Detected as finalization/execution
                                 ACTION PHRASES (e.g. 'proceed with the
                                 power-line work', 'authorize the
                                 de-energization', 'override the
                                 utility safety officer's judgment')
                                 in free-text proposal fields, never as
                                 bare domain nouns ('power line',
                                 'voltage', 'energized', 'circuit',
                                 'pole', 'transformer') — bare-noun
                                 matching would false-trip on the
                                 default mock advisor's own routine
                                 rationale text, since this actor's
                                 entire domain is high-voltage
                                 power-line installation and repair at
                                 height. See `linecoord.governor-test`
                                 `default-mock-advisor-proposals-never-self-trip-scope-exclusion`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off,
  regardless of confidence):
    7. :op :flag-safety-concern always escalates (a surfaced
                                 weather-hazard/equipment-condition/
                                 outage-report concern always requires
                                 human review — the governor never
                                 resolves a safety concern itself).
    8. :op :coordinate-supply-order with :cost above
                                 `supply-order-cost-threshold` always
                                 escalates.
    9. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [linecoord.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold 20000)

(def ^:private allowed-ops
  #{:log-service-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Scope-exclusion is matched as finalization/execution ACTION
;; PHRASES, never as bare nouns ("power line", "voltage", "energized",
;; "circuit", "pole", "transformer") — this actor's entire domain is
;; high-voltage power-line installation and repair at height, so
;; bare-noun matching would false-trip on the default mock advisor's
;; own routine rationale text (e.g. "proposed :coordinate-supply-order
;; for circuit C-1" naming line/pole materials, or a crew-schedule
;; proposal naming "energized" circuits). See governor-test's
;; dedicated self-trip guard.
(def ^:private scope-exclusion-phrases
  ["proceed with the power-line work"
   "proceed with the power line work"
   "finalize the power-line-work-execution decision"
   "finalize the power line work execution decision"
   "execute the power-line work directly"
   "execute the power line work directly"
   "perform the power-line work directly"
   "perform the power line work directly"
   "dispatch the crew to perform power-line work"
   "authorize the de-energization"
   "authorize the deenergization"
   "authorize the re-energization"
   "authorize the reenergization"
   "authorize the lockout clearance"
   "authorize lockout clearance"
   "clear the lockout"
   "override the utility safety officer's judgment"
   "override the utility-safety officer"
   "bypass the utility-safety officer"
   "bypass de-energization authorization"
   "bypass lockout clearance"])

(defn- scope-excluded-text [proposal]
  (str/lower (str (:rationale proposal) " " (:description proposal))))

(defn scope-exclusion-violation?
  "true if any free-text field of `proposal` contains a
  finalization/execution action phrase attempting to finalize
  power-line-work execution, authorize a de-energization/
  re-energization/lockout clearance, or override utility-safety
  authority. Phrased as multi-word action phrases (never bare nouns)
  so this never false-trips on legitimate power-line-domain
  vocabulary."
  [proposal]
  (let [text (scope-excluded-text proposal)]
    (boolean (some #(str/includes? text %) scope-exclusion-phrases))))

(defn- hard-violations [{:keys [request proposal]} circuit-record w]
  (let [{:keys [op circuit-id worker-id]} proposal]
    (cond-> []
      (nil? circuit-record)
      (conj {:rule :no-circuit :detail "未登録 electrical distribution circuit"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は送電線作業判断を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op :detail "closed op-allowlist 外の op（送電線作業実行・停電/復電の承認・遮断措置解除・安全判断の上書きにあたる op は許可されていない）"})

      (and circuit-id (not= circuit-id (:circuit-id request)))
      (conj {:rule :circuit-mismatch :detail "proposal の circuit が request で検証済みの circuit と一致しない"})

      (and worker-id (nil? w))
      (conj {:rule :unknown-worker :detail "未登録 worker への提案は不可"})

      (and w (not= (:circuit-id w) (:circuit-id request)))
      (conj {:rule :worker-wrong-circuit :detail "worker が別 circuit 所属"})

      (scope-exclusion-violation? proposal)
      (conj {:rule :scope-exclusion-violation
             :detail "送電線作業の施工判断の確定・停電/復電の承認・遮断措置(ロックアウト)解除の承認・utility safety officer の判断の上書きにあたる提案は恒久的に禁止（human 承認でも上書き不可）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `linecoord.store/Store`. Pure — never mutates
  the store, never dispatches a robot action, never performs
  power-line work."
  [request context proposal store]
  (let [circuit-record (store/circuit store (:circuit-id request))
        w (some->> (:worker-id proposal) (store/worker store))
        hard (hard-violations {:request request :proposal proposal} circuit-record w)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (:cost proposal)
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? cost) (> cost supply-order-cost-threshold))
        always-risky? (or (contains? always-escalate-ops (:op proposal)) over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
