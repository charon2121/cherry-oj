# 运行限制推导协议

> **状态：** 2026-11-02 确认推导模型；2026-11-10 确认第 12 节的同题多语言接口。推导、冻结及管理端接入已实现。

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
| `contracts/languages.json` | 每门语言一项，`limitModel` 定义限制参数，`files` 定义驱动与用户文件名 |
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
- 消费者：judging-service（推导、就绪检查）、problem-service（语言配置、数据指纹与 IO 规模）、submission-service（冻结）、Gateway（浏览器接口与权限）、Web（标定管理与作答语言选择）。

## 10. 真源

行为以本文为准；字段以 `contracts/` 为准。

## 11. 实测记录

2026-11-10，本机 macOS + Docker Desktop 隔离栈（MySQL、Redis、Kafka、五个 Java 服务与当前 Go judge，
可信开发后端 devhost）：`scripts/e2e.py` 验证 C/C++ 同题 ACM 与 CORE、推导限制、C 的覆盖标定、
切换基础语言到 C、关闭基础语言入口后继续推导，以及已冻结输入不随标定变化而改变；真实编译运行得到 AC。
CORE 的 C 编译错误仍按用户编辑器行号显示。Java 集成测试另覆盖不能跨语言拼接公开条件与版本冲突。
语言参数仍是第 8 节的初始值，本次功能回归不代替自有原生节点上的参数重拟合。


## 12. 同题多语言与管理端接入

本节规定同题多语言配置、标定管理与作答选择。字段真源为
`contracts/web-api.openapi.json` 与 `contracts/calibration.schema.json`。

### 12.1 管理员配置与标定

- 建题仍选一门初始语言。新增 `PUT /api/admin/problems/{problemId}/languages`，请求
  `{rowVersion, allowedLanguages: [{id, starterCode, judgeTemplate}]}`，按顺序原子替换 1–32 门语言；
  不重复，且必须已在平台登记。CORE 未准备好的驱动可为 null，ACM 必须为 null；不接受客户端传入前缀。
- `rowVersion` 沿用题目乐观锁，过期返回 `409 ROW_VERSION_CONFLICT`；归档题只读。
  公开题修改仍立即生效，不自动取消公开。移除语言只关闭新的作答入口，不删除标定或历史输入；
  原有基础标定仍是题目事实，可继续推导其余语言的限制。
- 旧 `PATCH /api/admin/problems/{problemId}` 保持兼容：`starterCode` 与 `judgeTemplate`
  继续只修改配置顺序第一门语言，不增删或重排语言。新工作台通过专用接口修改语言配置。
- 标定请求新增可选 `kind`，`BASE | OVERRIDE`；省略按 BASE 处理，null 或未知值拒绝。
  响应增加 `kind`。请求语言必须已配置，CORE 按这门语言自己的驱动和前缀合并参考代码；
  资格、有效性、替换与资源上限沿用第 2–7 节。
- 新增 `GET /api/admin/problems/{problemId}/limits`：problem-service 读取当前数据指纹与 IO 规模，
  将题目配置的 languageIds 传给既有内部 limits 接口，按配置顺序返回来源、绝对限制、截断与不可用原因。
  没有测试数据返回 `404 TEST_DATA_NOT_FOUND`；离线不影响读取限制。做题页是否展示限制不在本片变更中。

### 12.2 公开检查与作答语言

- 公开检查返回每门语言的配置、在线节点、有效限制检查。`ready` 要求题面、样例、测试数据通过，
  且至少一门语言同时满足三项；不能跨语言拼接条件。保留旧的六项总览，后三项取第一门已就绪语言，
  没有就绪语言时取配置顺序第一门；新增 `languages` 数组供管理端逐门解释。
- 新增匿名只读 `GET /api/problems/{slug}/languages`，响应 `{data: {items: [...]}, meta}`；
  items 仅包含就绪语言的 id、displayName、starterCode，按题目配置排序，响应 no-store。
  题库和详情原有 allowedLanguages 仍表示配置的语言，作答选择器改用本接口，避免静态列表承诺在线状态。
- 私有、归档或不存在的题目统一为 `404 PROBLEM_NOT_FOUND`。当前没有可判题数据或没有就绪语言时
  返回 200 和空列表，页面仍可阅读题面并提示暂不可提交；判题服务故障返回 503，不能伪装为空列表。
  自测和正式提交仍在后端重新解析快照与执行配置，前端列表不是授权凭据。
- 新增 `POST /internal/problems/readiness`：problem-service 以自己的服务身份传入
  `{problemId, testDataDigest, maxTestcaseIoBytes, languageIds}`，1–32 门且不重复；
  judging-service 按既有 readiness 规则返回逐语言 ready 与检查，顺序和数量与请求一致。
  不需要用户，不允许借此伪造管理员；现有管理端接口继续要求 ADMIN。查询不执行程序、不产生标定。

### 12.3 兼容与冻结

新增端点，放宽管理详情的语言数量，并给响应增加字段；旧客户端的单语言请求仍可用，不改 judge、
sandbox、Kafka 或 JudgeInput 格式。客户端切换语言时按 userId、problemId、languageId 分别保存草稿，
自测和提交必须使用当前所选语言。已经冻结的提交不随后续语言配置、标定或参数变化而改变。
