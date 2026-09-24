# physai-isco-7413 — 送配電線架線工・修理工（ISCO 7413）の班の段取りロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-7413`、ISCO 7413 送配電線の架線工及び修理工）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 班の出動段取り・物流調整ロボットが、作業記録・班と出動計画の提案・安全上の懸念の提示・電線と電柱資材の発注を調整する（架線作業と安全の判断は人がする）。
その物理的な仕事（電柱金物を作業道で運び上げる・碍子を腕金まで持ち上げる・張線前のアルミ電線の引張確認）を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:hardware-up-access-track` | transport | クローラ運搬車が腕金・碍子・金物を作業道 100 m で電柱まで運び上げる | 1 区間の所要時間 | 120 s（estimate） |
| `:insulator-to-crossarm` | manipulator | ブーム先端のアームが碍子をバケットのトレーから腕金のピンまで持ち上げる | 肩関節ピークトルク | 150 N·m（estimate） |
| `:conductor-pull-check` | material | 100 mm² の硬アルミ電線の試料を張線張力以上に引く | 最終ひずみ | 0.002（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test-physai/linecoord/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。repo 自身の `test/` の .cljk も同じ runner で走る）。

## 測って分かったこと・限界（成長の第一候補）

1. **作業道**: 勾配 0〜10° では 85.83 s で変わらない（加速度上限 0.4 m/s² が効く）。15° で駆動力 1500 N が効き 100.38 s、20° では **停止（stalled）**。
   限界 120 s を超える勾配は **約 15.1°**。エネルギーは 0° 35.2 kJ → 15° 146.9 kJ（転がり抵抗 0.08 の軟弱路）。
2. **碍子の持ち上げ**: 肩トルクは 2 kg で 74.4 N·m、12 kg で 141.6 N·m、18 kg で 182.0 N·m。限界 150 N·m に達する積荷は **13.2 kg**。
3. **電線の引張**: 12 kN まで弾性（ひずみ 0.00174）、約 16.5 kN で降伏（20 kN でひずみ 0.042）。ひずみ 0.002 を超える張力は **約 13.8 kN**。
4. **estimate のままの値**: 作業道の所要時間 120 s と転がり抵抗 0.08、肩トルク上限 150 N·m、アルミ線の降伏応力 160 MPa とひずみ限界（電線規格の定格引張強さで置き換える）、運搬車・アームの諸元。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この職種のロボットがする別の物理的な仕事を 1 case 足す（例: 電柱の建て込み時の吊り（:manipulator）、変圧器の絶縁油（:tank-drain）、電線の通電発熱（:thermal、:q-gen-w-m3））。
   `:kind` は :transport / :manipulator / :material / :thermal / :tank-drain / :pipe-flow。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-7413 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-7413 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
