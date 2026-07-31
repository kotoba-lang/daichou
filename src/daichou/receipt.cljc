(ns daichou.receipt
  "agent の行為1件ぶんの受領証。

   **この台帳の主張は『承認と実行が同じ記録の中にある』の一点である。**
   記録するだけの製品はすでに無料でもあり有償でもある(microsoft/agent-governance-toolkit、
   Microsoft Agent 365、Asqav)。ここが違うのは、`:approval` を欠いた受領証を
   **作れない**ことにある — 承認の無い行為は、後から承認を書き足せない。

   受領証は pure な値で、連鎖は前件のハッシュで結ぶ:
     :receipt/action-ref     行為の正規形の SHA-256
     :receipt/previous-hash  直前の受領証のハッシュ(先頭は 0 詰め)
     :receipt/hash           この受領証自身の正規形の SHA-256
   `:receipt/hash` は自分自身を含まない部分から計算するので、再計算で検証できる。"
  (:require [daichou.canonical :as canonical]
            [daichou.hash :as h]))

(def ^:private required-approval-keys
  #{:approval/by :approval/decision :approval/policy-version})

(defn- check-approval! [approval]
  (when-not (map? approval)
    (throw (ex-info "受領証には :approval が要る。承認の無い行為は記録できない"
                    {:approval approval})))
  (let [missing (remove (partial contains? approval) required-approval-keys)]
    (when (seq missing)
      (throw (ex-info "承認は「誰が・何を決めたか・どの policy 版で」が揃って初めて承認になる"
                      {:missing (vec missing) :approval approval}))))
  (when-not (contains? #{:allow :deny :review} (:approval/decision approval))
    (throw (ex-info "決定は :allow / :deny / :review のいずれか"
                    {:decision (:approval/decision approval)}))))

(defn make
  "受領証を1件作る。

   `action` は行為(agent・種別・対象など)、`approval` は誰がどの policy 版で
   何を決めたか。`previous-hash` は直前の受領証の :receipt/hash、先頭なら nil。"
  [{:keys [action approval previous-hash at]}]
  (when-not (map? action)
    (throw (ex-info "行為が無い受領証は作れない" {:action action})))
  (check-approval! approval)
  (let [body {:receipt/action action
              :receipt/approval approval
              :receipt/at (or at (throw (ex-info "受領証には時刻が要る" {})))
              :receipt/action-ref (h/sha256 (canonical/write action))
              :receipt/previous-hash (or previous-hash h/zero-hash)}]
    (assoc body :receipt/hash (h/sha256 (canonical/write body)))))

(defn recompute-hash
  "受領証の :receipt/hash を、その中身から計算し直す。"
  [receipt]
  (h/sha256 (canonical/write (dissoc receipt :receipt/hash))))

(defn intact?
  "この受領証1件が、書かれた内容と整合しているか。"
  [receipt]
  (and (= (:receipt/hash receipt) (recompute-hash receipt))
       (= (:receipt/action-ref receipt)
          (h/sha256 (canonical/write (:receipt/action receipt))))))
