(ns daichou.chain
  "受領証の連鎖。

   **検証は「壊れているか」ではなく「どこで壊れたか」を返す。**壊れた事実だけを
   返す検証器は、運用では『台帳全体が信用できない』としか読めず、実務では捨てられる。
   位置が出れば、そこから前は使える。"
  (:require [daichou.receipt :as receipt]
            [daichou.hash :as h]))

(defn append
  "連鎖の末尾に1件足す。前件のハッシュを自動で結ぶ。"
  [chain {:keys [action approval at]}]
  (conj (vec chain)
        (receipt/make {:action action
                       :approval approval
                       :at at
                       :previous-hash (:receipt/hash (last chain))})))

(defn verify
  "連鎖を検証する。

   返すのは `{:ok? bool :length n :broken-at idx :reason kw}`。
   `:broken-at` は**最初に壊れた位置**で、そこまでは信用できる。"
  [chain]
  (let [chain (vec chain)]
    (loop [i 0 prev h/zero-hash]
      (if (>= i (count chain))
        {:ok? true :length (count chain)}
        (let [r (nth chain i)]
          (cond
            (not= (:receipt/previous-hash r) prev)
            {:ok? false :length (count chain) :broken-at i :reason :chain-break}

            (not (receipt/intact? r))
            {:ok? false :length (count chain) :broken-at i :reason :receipt-altered}

            :else (recur (inc i) (:receipt/hash r))))))))

(defn approvals-by
  "誰の承認で何件動いたか。監査で最初に聞かれる問いなので、台帳の側に置く。"
  [chain]
  (frequencies (map (comp :approval/by :receipt/approval) chain)))
