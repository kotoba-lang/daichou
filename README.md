# daichou（台帳）

**agent の行為を、承認ごと、改ざん耐性のある連鎖として残す。自己ホスト。**

`.cljc` の再利用ライブラリで、IO を持たない。値と関数だけ。

## なぜ作ったか

記録するだけの実装は既にある — [microsoft/agent-governance-toolkit](https://github.com/microsoft/agent-governance-toolkit)（MIT・無料）、Microsoft Agent 365（GA・$15/user/月）、Asqav（Elastic License 2.0）。**この台帳が違うのは2点だけ**:

1. **承認を欠いた受領証を作れない。** `:approval`（誰が・何を決めたか・どの policy 版で）が無いと `receipt/make` は例外を投げる。後から承認を書き足す余地を残さない。
2. **完全性が手元で完結する。** Asqav を実際に入れて確認したところ、`local_sign` は署名せず `{... "status": "pending"}` の素の JSON をキューに積むだけで、**署名・ハッシュ・連鎖はすべてサーバ側**だった（README も "All cryptography runs server-side"）。**agent の行為を第三者クラウドへ送れない組織では、あちらの改ざん耐性は一切得られない。** ここはその制約下で動く。

## 使う

```clojure
(require '[daichou.chain :as chain])

(-> []
    (chain/append {:action   {:action/agent "claude-code" :action/type "git:push" :action/target "main"}
                   :approval {:approval/by "gftd-governor"
                              :approval/decision :allow          ; :allow / :deny / :review
                              :approval/policy-version "fleet-policy@2026-07-31"}
                   :at       "2026-07-31T00:00:01Z"})
    chain/verify)
;; => {:ok? true :length 1}
```

検証は**壊れた位置**を返す。壊れた事実だけを返す検証器は、運用では「台帳全体が信用できない」としか読めない。

```clojure
(chain/verify tampered)
;; => {:ok? false :length 3 :broken-at 1 :reason :receipt-altered}
```

## 設計上、意図的に狭くしてあるところ

- **浮動小数を受け付けない。** ClojureScript では `1` と `1.0` が同じ値なので、「整数と小数を区別する」正規化は JVM でしか成り立たない。**両 runtime で同じ連鎖にならない台帳は、台帳として無いのと同じ**（nbb で作った受領証を JVM の検証器に出せない）。金額や計測値は文字列か整数（最小単位）で入れる。これは実装後に**両 runtime で走らせて見つけた**欠陥で、区別する代わりに受け付けないことにした。
- **map のキー衝突を潰さない**（`{:a 1 "a" 2}` は例外）。潰すと改ざんが隠れる。
- **鍵の保管・取得を持たない。** 署名を足すときは kagi 経路を使う（`kagami` と同じ流儀）。第二の keyring を作らない。
- **送信経路を持たない。** クラウドへ送るかどうかは利用側の判断で、この層は関与しない。

## runtime

第一の実行経路は **nbb / ClojureScript**、JVM は互換スイート用（CLAUDE.md の runtime 優先順位）。`test/daichou/chain_test.cljc` は**両方で走らせる前提**で、その中の golden hash が「片方で作った連鎖がもう片方で検証できる」ことを固定している。

```bash
nbb --classpath "src:test" -e '(require (quote [clojure.test :as t]) (quote daichou.chain-test)) (t/run-tests (quote daichou.chain-test))'
clojure -M:test
```

## 位置づけ

- 設計判断の正本: `com-junkawasaki/root` の `90-docs/adr/2607302210-*.edn`（D33 ②、D42）
- 競合の実測データ: `gftdcojp/kabuto`（`market-maturity.edn` / `conformance.edn`）
- 隣接: `kagami`（repo の pin 事象の台帳。ドメインが違う）、`ai-gftd-cybersecurity`（証跡の出力と規制への写像）
