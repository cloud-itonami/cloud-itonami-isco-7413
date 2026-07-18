(ns linecoord.advisor
  "Electrical Line Crew Coordination Advisor — the advisor named in
  this repository's README, proposing a crew dispatch scheduling/
  logistics coordination operation (log a service record, schedule a
  crew/dispatch operation, flag a safety concern, coordinate a supply
  order) from a circuit roster, crew roster and dispatch schedule.
  Swappable mock/llm; the advisor ONLY proposes — `linecoord.governor`
  checks circuit/worker registration, the closed op-allowlist and
  scope-exclusion independently, and always escalates safety-concern
  flags, above-threshold supply orders and low-confidence proposals.
  This actor coordinates CREW/DISPATCH SCHEDULING/LOGISTICS ONLY — it
  never performs power-line work and never proposes to finalize a
  power-line-work-execution decision, authorize a de-energization/
  re-energization/lockout clearance, or override a utility-safety
  officer's judgment. Modeled on cloud-itonami-isco-7214's
  steelcoord.advisor.

  A proposal: {:op :log-service-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :circuit-id str :worker-id str? :cost
               number? :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake circuit-id worker-id cost task materials
                             concern-type severity description time-window
                             progress-notes outage-report]
                      :as request}]
  (cond-> {:op op
           :effect :propose
           :circuit-id circuit-id
           :stake (or stake :low)
           :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
           :rationale (str "proposed " (name op) " for circuit " circuit-id)}
    worker-id (assoc :worker-id worker-id)
    (some? cost) (assoc :cost cost)
    task (assoc :task task)
    materials (assoc :materials materials)
    concern-type (assoc :concern-type concern-type)
    severity (assoc :severity severity)
    description (assoc :description description)
    time-window (assoc :time-window time-window)
    progress-notes (assoc :progress-notes progress-notes)
    outage-report (assoc :outage-report outage-report)))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an electrical-line-installation-and-repair crew dispatch
   scheduling/logistics coordination advisor. Given a request, propose
   an :op (:log-service-record, :schedule-crew-operation,
   :flag-safety-concern or :coordinate-supply-order ONLY — no other op
   exists), the :circuit-id, an honest :confidence and a :stake. You
   coordinate crew dispatch scheduling and logistics ONLY: never
   propose to finalize a power-line-work-execution decision, never
   propose to authorize a de-energization, a re-energization or a
   lockout clearance, never propose to override a utility-safety
   officer's judgment, and never propose an op outside the closed
   allowlist above. The governor checks circuit/worker registration
   and scope independently. Safety-concern flags and above-threshold
   supply orders always require human sign-off regardless of
   confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
