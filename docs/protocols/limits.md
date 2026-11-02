# 运行限制推导协议

> **状态：** 2026-11-02 审核通过，尚未实施。第 6 节的契约改动与实现在同一次改动里落地（先契约、再类型、再实现）。

规定一道题在每门语言下的运行限制（每个测试点的 CPU 时间、内存）从哪里来：题目只做一次**基础标定**，
其余语言由**语言限制参数**按公式推导，题目还可以为个别语言做**覆盖标定**。推导只在 judging-service 里发生，
结果随提交冻结；judge 仍只接收算好的绝对限制，不计算任何语言倍率。

## 快速使用

题目 P 用 C++ 做基础标定：`cpuNs = 1s`、`memoryBytes = 256MiB`，测试点里输入加输出最大 2MB。Java 的限制参数为
`a = 3.2`、`b = 170ms`、`c = 0`、`k = 1.25`、`m = 40MiB`：

```text
T_ref = (1s − 0 − 0) / 1.0                    = 1s
T_java = 3.2 × 1s + 170ms + 0 × 2MB           = 3.37s
M_ref = (256MiB − 0) / 1.0                    = 256MiB
M_java = 1.25 × 256MiB + 40MiB                = 360MiB
```

以后用 Java 提交 P，冻结的限制就是 `cpuNs = 3370000000`、`memoryBytes = 377487360`，并记下这些值是怎么算出来的。

## 1. 参与方

| 参与方 | 职责 |
|---|---|
| problem-service | 发起标定（代表管理员）；向 judging-service 提供测试数据地址、指纹，以及本协议新增的 `maxTestcaseIoBytes` |
| judging-service | 保存基础标定与覆盖标定；按语言限制参数推导；在就绪检查与执行配置里返回限制及其来源 |
| submission-service | 创建提交时取执行配置，把限制和推导记录一起冻结进 JudgeInput |
| judge（Go） | 不变：只接收 `JudgeLimits` 里的绝对值 |
| `contracts/languages.json`（新） | 每门语言的限制参数，唯一真源，改动走代码审核 |

## 2. 三种来源

| 来源 `limitSource` | 怎么得到 | 数量 |
|---|---|---|
| `BASE` | 管理员选一门**可作基础**的语言 X，给出绝对限制，X 的参考解在节点上用正式数据验证通过 | 每道题、每个测试数据指纹最多一条 VALID |
| `OVERRIDE` | 管理员为语言 L（L ≠ X）给出绝对限制，L 的参考解验证通过 | 每道题、每门语言最多一条 VALID |
| `DERIVED` | 由 BASE 与语言参数推导，不运行任何程序 | 不保存，按需计算 |

某门语言 L 的限制取第一个可用的：**L 的 OVERRIDE → BASE（当 L = X）→ DERIVED**。

标定的验证方式不变：管理员给的限制就是这次标定的结果，参考解只证明"在这个限制内能通过"，`benchmarkSummary` 记录实测的最大值。

## 3. 语言限制参数

真源是 `contracts/languages.json` 中每门语言的 `limitModel`。为了避免浮点误差，倍数用千分比整数表示，字段名都带单位：

| 字段 | 公式符号 | 类型与范围 | 含义 |
|---|---|---|---|
| `cpuFactorPermille` | a × 1000 | 整数，100–100000 | 相对 C++ 的 CPU 时间倍数 |
| `cpuFactorCapPermille` | A_max × 1000 | 整数或 null | 推导时倍数的补偿上限（政策值）；null 表示不设上限 |
| `cpuOverheadNs` | b | 整数，≥ 0 | 每个测试点的固定成本：启动、解释器或虚拟机加载、JIT 预热 |
| `ioPicosPerByte` | c | 整数，≥ 0 | 每字节输入或输出的额外成本，单位皮秒（1ps = 10⁻³ns）；拟合之前一律为 0 |
| `memoryFactorPermille` | k × 1000 | 整数，1000–10000 | 相对 C++ 的内存增量倍数 |
| `memoryOverheadBytes` | m | 整数，≥ 0 | 运行时自身的固定内存 |
| `baseEligible` | — | 布尔 | 能否作为基础语言；拟合残差大的语言为 false |

C++ 固定为 `1000 / null / 0 / 0 / 1000 / 0 / true`，它是"C++ 当量"的定义。一门语言没有 `limitModel`，就只能靠 OVERRIDE 开放。

## 4. 推导公式

记 `IO = maxTestcaseIoBytes`，即题目当前测试数据里各测试点 `input.sizeBytes + output.sizeBytes` 的最大值，由
problem-service 从 `testdata.json` 读出，跟着测试数据指纹走。

**第 1 步，从基础标定反推 C++ 当量**（X 为基础语言，用 X 的原始倍数，不套用上限）：

```text
T_ref = max( (T_X − b_X − c_X × IO) / a_X , T_REF_MIN )
M_ref = max( (M_X − m_X) / k_X , M_REF_MIN )
```

**第 2 步，推导到语言 L：**

```text
T_L = ceil10ms( min(a_L, A_max_L) × T_ref + b_L + c_L × IO )
M_L = ceilMiB( k_L × M_ref + m_L )
```

- `T_REF_MIN = 10ms`，`M_REF_MIN = 16MiB`，防止基础语言的参考限制很小时反推出负数或过小的值。
- 计算用整数和有理数完成，最后一步向上取整：时间取到 10ms，内存取到 1MiB。实现必须和本文的黄金用例（见第 8 节）逐位一致。
- 墙钟上限：推导值不带 `clockNs`，由 judge 现有的运行策略按 `cpuNs` 推算；BASE 和 OVERRIDE 仍然可以显式给出。
- **超出平台上限就截断：** 推导出的 `cpuNs` 或 `memoryBytes` 超过 judging-service 配置的平台硬上限（`PLATFORM_MAX_CPU_NS`、`PLATFORM_MAX_MEMORY_BYTES`）时，取平台上限，**这门语言照样可用**；需要更多资源的程序会得到 TLE 或 MLE。截断要写进推导记录（`cpuCapped`、`memoryCapped`），管理端标定页要标出被截断的语言，便于管理员决定是否为它做覆盖标定。
- **平台上限的配置：** `cherry.judging.limits.max-cpu-ns` 与 `cherry.judging.limits.max-memory-bytes`，**必填、没有默认值**，缺失或不大于 0 时 judging-service 拒绝启动。`max-cpu-ns` 还要保证 judge 按它推算的墙钟不超过 judge 的墙钟硬界（120s），否则同样拒绝启动。本文示例按 20s / 1GiB 计，不是默认值；按 judging-service 现在的 `wall-ratio` 10，`max-cpu-ns` 最多只能到 12s。

## 5. 失效规则

| 事件 | 影响 |
|---|---|
| 测试数据指纹变化 | BASE 和所有 OVERRIDE 一起失效（沿用现有规则），DERIVED 随之不可用；要重新做基础标定，覆盖标定也要逐个重做 |
| 新的 BASE 变为 VALID | 旧 BASE 变为 SUPERSEDED；如果新基础语言原来有 OVERRIDE，这条 OVERRIDE 也变为 SUPERSEDED；其他语言的 OVERRIDE 不受影响 |
| 删除某门语言的 OVERRIDE | 这门语言退回 DERIVED |
| `contracts/languages.json` 的参数改动 | 只影响此后解析出的执行配置；已冻结的提交不变 |
| 驱动代码或前缀改动（CORE） | 沿用现有做法：只提示重新标定，不自动失效 |

## 6. 契约改动

| 契约 | 改动 |
|---|---|
| `contracts/languages.json`（新增） | 每门语言一项，本协议只要求其中的 `limitModel`；语言目录的其余字段在语言解耦时再补 |
| `calibration.schema.json` | `CalibrationRequest`、`Calibration` 增加必填字段 `kind`：`BASE` 或 `OVERRIDE`。`ReadinessCheck.code` 增加 `LIMIT_MODEL`（这门语言在 `languages.json` 里有 `limitModel`）。就绪查询增加必填参数 `maxTestcaseIoBytes`（problem-service 从当前测试数据读出），`ExecutionProfile` 增加 `limitSource` 与 `limitDerivation`，`calibrationId` 在 DERIVED 时是基础标定的编号。新增 `GET /internal/admin/limits?problemId=&testDataDigest=&maxTestcaseIoBytes=&languageIds=...`，`languageIds` 由 problem-service 给出题目开放的语言（judging-service 不知道题目开放了哪些语言），1–32 个且不重复；响应为每门语言一行 `LanguageLimits`，顺序与请求一致，不要求有在线节点：`languageId`、`limitSource`、`cpuNs`、`memoryBytes`、`clockNs`、`calibrationId`、`cpuCapped`、`memoryCapped`、`unavailableReason` |
| `problem-test-data.schema.json` | 响应增加 `maxTestcaseIoBytes`（整数，≥ 0） |
| `execution-profile.schema.json` | 增加必填字段 `limitSource` 与 `limitDerivation`（见下）；`languageCalibrationId` 的含义改为"依据的标定编号"：DERIVED 时是 BASE 的编号 |
| `judge-input.schema.json` | 增加必填字段 `limitSource` 与 `limitDerivation`，与 `effectiveLimits` 一起冻结。`contractVersion` 保持 `"2"`：按既定做法不做兼容，已有数据删除 |
| `judge.schema.json` | 不变 |

`limitDerivation`：`limitSource` 为 DERIVED 时必填，否则为 null。

```json
{
  "baseLanguageId": "cpp",
  "baseCalibrationId": "0198cafe-0000-7000-8000-000000000006",
  "baseCpuNs": 1000000000,
  "baseMemoryBytes": 268435456,
  "maxTestcaseIoBytes": 2000000,
  "refCpuNs": 1000000000,
  "refMemoryBytes": 268435456,
  "platformMaxCpuNs": 20000000000,
  "platformMaxMemoryBytes": 1073741824,
  "cpuCapped": false,
  "memoryCapped": false,
  "baseLimitModel": { "cpuFactorPermille": 1000, "cpuFactorCapPermille": null, "cpuOverheadNs": 0,
                      "ioPicosPerByte": 0, "memoryFactorPermille": 1000, "memoryOverheadBytes": 0 },
  "languageLimitModel": { "cpuFactorPermille": 3200, "cpuFactorCapPermille": null, "cpuOverheadNs": 170000000,
                          "ioPicosPerByte": 0, "memoryFactorPermille": 1250, "memoryOverheadBytes": 41943040 }
}
```

冻结参数原值与当时的平台上限，而不是参数的版本号：哪怕 `languages.json` 或平台配置以后改了，历史提交也能独立复算。

## 7. 成功与失败

- **执行配置解析：** 有可用限制时返回 200 和 `limitSource`。没有可用限制时沿用现有的拒绝方式，不生成默认限制。没有可用限制只有两种：没有 VALID 的 BASE 也没有这门语言的 OVERRIDE；需要推导但这门语言没有 `limitModel`。推导值超出平台上限不算没有可用限制（见第 4 节）。
- **标定请求：** `kind = BASE` 时，语言必须是 `baseEligible`，否则返回 400；`kind = OVERRIDE` 时，语言不能是当前 BASE 的语言，否则返回 409。管理员给出的 `cpuNs`、`memoryBytes` 超过平台上限时返回 400，手填的限制和推导值受同一组上限约束。
- **就绪检查：** 一门语言就绪，需要满足 `ONLINE_JUDGE_NODE`、`LANGUAGE`，并且满足以下二者之一：(`CALIBRATION` 通过，即有 VALID 的 OVERRIDE)，或者 (BASE 为 VALID 且 `LIMIT_MODEL` 通过)。

## 8. 初始参数与黄金用例

初始值来自 IBM Project CodeNet 约 742 万份 AC 提交的逐题拟合，加约 15% 余量。**新语言上线前要在我们自己的节点上用 benchmark 语料重新拟合**，再替换下表；`c` 在 IO 样本补齐之前一律为 0。

| 语言 | a | A_max | b | k | m | 可作基础 |
|---|---|---|---|---|---|---|
| C++ | 1.0 | — | 0 | 1.0 | 0 | 是 |
| C | 1.0 | — | 0 | 1.0 | 0 | 是 |
| Rust | 1.2 | — | 0 | 1.0 | 4MiB | 是 |
| Go | 1.9 | — | 15ms | 1.0 | 4MiB | 是 |
| C# | 3.1 | — | 60ms | 1.0 | 20MiB | 是 |
| Java | 3.2 | — | 170ms | 1.25 | 40MiB | 是 |
| Kotlin | 4.8 | — | 380ms | 1.25 | 40MiB | 否 |
| JavaScript | 4.3 | — | 50ms | 1.0 | 24MiB | 是 |
| PyPy | 4.5 | — | 150ms | 1.1 | 64MiB | 是 |
| CPython | 6.8 | 5.0 | 130ms | 1.0 | 8MiB | 否 |
| Ruby | 7.6 | 5.0 | 10ms | 1.0 | 10MiB | 否 |

CPython 与 Ruby 的补偿上限 5.0（2026-11-02 定）：按 CodeNet 估算，题目限制取 C++ 参考解 2 倍时，上限 5 倍覆盖约 70%（CPython）与 77%（Ruby）的题，不设上限也只到 78% 与 82%，再放宽主要是让节点承担更长的判题时间。Python 优先提供 PyPy（不设上限即覆盖约 93%）。

黄金用例（实现与测试各钉一份）：

| 基础 | IO | 目标 | 期望 |
|---|---|---|---|
| C++ 1s / 256MiB | 0 | Java | 3.37s / 360MiB |
| Java 3.37s / 360MiB | 0 | C++ | 1s / 256MiB |
| C++ 5ms / 16MiB | 0 | Java | 0.21s（T_ref 取下限 10ms）/ 60MiB |
| C++ 8s / 1GiB | 0 | Java | 20s（截断，`cpuCapped`）/ 1GiB（截断，`memoryCapped`）；按平台上限 20s / 1GiB 计 |

## 9. 版本与兼容

- `contracts/languages.json` 带 `x-contract-version`。参数取值的变化不算协议变化；字段的增删和语义改变才算。
- 本协议落地时直接改各服务的 V1 schema，不写迁移；已有的标定和提交数据删除（沿用去版本化时定下的原则）。
- 消费者：judging-service（推导、就绪检查）、problem-service（`maxTestcaseIoBytes`、管理端标定页）、submission-service（冻结）、Web 管理端（标定页展示来源）。

## 10. 真源

以本文为准；字段以落地后的 `contracts/` 为准。

## 11. 实测记录

尚无。参数初始值的依据见第 8 节；落地后在测试服务器上验证黄金用例与真实判题，再补记录。
