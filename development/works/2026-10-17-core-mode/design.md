# 设计：C++ CORE 模式

工作说明见 [00-work.md](./00-work.md)，方案依据见 [multi-file-languages.md](./multi-file-languages.md)。
现状来自 2026-10-17 对代码的只读调查。**设计已于 2026-10-17 与用户讨论定稿（决定见 §9，延后的功能见 §8），尚未授权实施。**

## 1. 方案

### 1.1 三段文本，两个文件

| 文本 | 粒度 | 公开吗 | 存在哪里 |
|---|---|---|---|
| **驱动代码** | 题目 × 语言 | 不下发给用户，报错里出现照常显示 | problem-service `problem_language.judge_template`（已有列） |
| **前缀代码** | 每门语言一份，所有题目共用 | 不保密（只有 include/import） | problem-service 新增的语言表 `language.solution_prefix`；开发阶段直接改数据库 |
| **模板代码**（给用户的起始代码） | 题目 × 语言 | 公开 | problem-service `problem_language.starter_code`（已有列） |

一次 CORE 判题由两个文件组成：

- **用户文件**（C++ 为 `solution.cpp`）= 前缀代码 + 用户在模板代码基础上编辑完的代码；
- **驱动文件**（C++ 为 `Main.cpp`）= 驱动代码，读输入、调用用户函数、输出结果，并引用用户文件。

C++ 的样子（以 A+B 为例）：

```cpp
// solution.cpp
#include <bits/stdc++.h>          // ┐ 前缀代码：C++ 所有题目共用，用户看不到
using namespace std;              // ┘
class Solution {                  // ← 用户代码，编辑器里的第 1 行
public:
    int add(int a, int b) { return a + b; }
};
```

```cpp
// Main.cpp —— 这道题的驱动代码
#include "solution.cpp"

int main() {
    int a, b;
    cin >> a >> b;
    cout << Solution().add(a, b) << "\n";
}
```

### 1.2 谁做什么

| 步骤 | 在哪里 |
|---|---|
| 合并前缀与用户代码，冻结两个文件 | Java：submission-service（正式提交、自测）、problem-service（标定参考解） |
| 按固定文件名写入两个文件，编译、运行 | judge（文件名来自契约，不接受外部传入） |
| 改写报错：用户文件行号减去前缀行数，路径只留文件名 | Java：judging-service（拿到 judge 的原始输出后） |

judge 只多了一项通用能力："一个程序由驱动文件和用户文件组成"，它不知道 CodeMode，也不改写报错。

这与 LintCode 实测的做法一致：Java 的 `Solution.java` 前面多 5 行 import，驱动在 `Main.java`，展示的编译错误与异常栈帧里
`Solution.java:N` 被改写，标准输出不改（multi-file-languages.md §6.3）。

## 2. 为什么这样做（摘要）

- **前缀不可避免。** Go、Java、Kotlin、C#、PHP 等要求 `package`/`import`/`using`/`<?php` 写在文件最前面，用户不写 import
  就能用常用库也只能靠前缀（multi-file-languages.md §6.1）。
- **驱动代码单独成文件，** 驱动自己的 import 和入口不会和用户代码争文件开头；13 门语言实测可行（multi-file-languages.md §2–§3）。
- **改写报错，** 让前缀对用户不可见。用户文件名由平台决定，改写只认"用户文件名 + 行号"；一个不区分语言的改写器在 13 门语言的
  真实输出上全部换算正确（multi-file-languages.md §7）。
- 放弃的做法：占位符合并、`#line`、模板以占位符开头、前缀只读显示、同行前缀与各语言的预加载技巧。

## 3. 需要改写的已确认内容

实施前先改全局文档（§7 第 0 步）：

| 位置 | 现在 | 改成 |
|---|---|---|
| `docs/product.md` §4.2、§10.1 第 4 条 | CORE 使用题目语言级模板的唯一占位符替换；模板含且只含一个 `{{USER_CODE}}` | CORE 由驱动代码、语言前缀、模板代码三段组成；用户文件 = 前缀 + 用户代码，驱动文件通过固定文件名引用它；不建立通用函数类型系统这一点不变 |
| `docs/product.md` §3.4 第 4 条 | 服务端完成唯一占位符替换，Go judge 仍只接收普通完整源码 | 服务端合并前缀与用户代码；judge 接收驱动文件与用户文件，不知道 CodeMode |
| `docs/product.md` §3.3、§4.2（保密） | 不向普通用户暴露完整 judgeTemplate | 改为：接口不下发驱动代码，也不提供查看入口；报错里带出驱动代码的位置和源码行属于正常，不做遮掩；驱动代码里不放答案 |
| `docs/architecture.md` §1 第 7 条、§7 | 源码模式在进入 judge 前消失；CORE 在 submission-service 合并模板 | judge 只看到两个文件，仍看不到 CodeMode；合并改为"前缀 + 用户代码"；judging-service 改写报错 |
| `docs/data-model.md` §4.3、§8.1 第 5 步、§8.3、§12.2 | `judgeTemplate` 含占位符；`completeSource` 为替换结果 | 新增语言表与前缀；JudgeInput 分别冻结驱动文件与用户文件 |
| 各契约中 `judgeTemplate`、`completeSource`、`source` 的说明 | 按占位符合并描述 | 按两个文件描述（§6.1） |

## 4. 现状：哪里把 CORE 拦掉了

| 位置 | 现在的行为 |
|---|---|
| problem-service `V1__create_problem_tables.sql` | ✅ `code_mode` 允许 `CORE`，`problem_language.judge_template` 可空列已存在；没有语言表 |
| `AdminProblemService.create` | ❌ `codeMode != ACM` 时报"目前只支持 C++ ACM 题目" |
| `AdminProblemDtos.UpdateProblemRequest` | ❌ 只有 `starterCode`，没有 `judgeTemplate` |
| `ProblemPublicationService.localChecks` / `calibrate` | ❌ LANGUAGE 检查要求 ACM；`referenceSource` 当作完整程序原样转发 |
| `SubmissionSnapshotService.resolve` | ❌ 非 ACM 报 `UNSUPPORTED_CODE_MODE`；`Snapshot` 里没有驱动代码和前缀 |
| `SubmissionService.validateSnapshot`、`CustomRunController.run` | ❌ 要求 `codeMode == "ACM"` |
| judge `JudgeRequest`、`language.Language`、`flow/compile.go` | 只有一个源文件 `Main.cpp` |
| judging-service `FormalWorker.safe()`、`TrialController.diagnostic()` | 用正则把所有路径替换成 `<file>`，不认识用户文件与驱动文件 |
| judging-service 与 problem-service 之间的标定、readiness 接口 | 只有两边各自的 `JudgingDtos`，`contracts/` 里没有契约 |
| Web 管理端创建、管理端 schema、做题页 | ❌ 写死 ACM |

## 5. 规则细节

### 5.1 合并

- 前缀必须以 `\n` 结尾；**前缀行数** = 前缀中 `\n` 的个数；用户文件 = 前缀 + 用户代码，直接拼接。规则写进契约，
  submission-service 与 problem-service 各自实现并各写测试。
- 冻结：JudgeInput 记录驱动文件、合并后的用户文件、前缀行数。前缀以后可能被改，判题后的改写与重判都用冻结的值。
- ACM 不变：只有一个完整程序，没有前缀，没有改写。

### 5.2 文件名

每门语言的驱动文件名与用户文件名写进契约（C++：`Main.cpp`、`solution.cpp`）。judge 按它写文件；judging-service 按它识别报错里的文件；
两边各有一个测试断言自己的取值与契约一致。

### 5.3 报错改写（judging-service）

**作用范围：** 正式判题与自测的编译诊断、运行错误的 stderr 片段。**不改**程序的标准输出（要和答案比对，也是用户自己打印的）。

**按位置分四类处理：**

| 位置 | 原始输出（示例） | 展示给用户 |
|---|---|---|
| 用户代码（行号 > 前缀行数） | `solution.cpp:12:5: error: ...`，加源码摘录 `   12 \| ...` | `solution.cpp:10:5: error: ...`；摘录左侧行号同样减去前缀行数，宽度不变 |
| 前缀代码（行号 ≤ 前缀行数） | `solution.cpp:2:1: error: ...` | `[预置代码]:2:1: error: ...`，保留摘录 |
| 驱动文件 | `Main.cpp:13:52: error: too many arguments ...`，加源码摘录 | **原样保留**，只去掉路径里的目录（`Main.cpp:13:52`）。用户看文件名就知道是调用方式不对 |
| 其他文件（如标准库头文件） | `/usr/include/c++/13/bits/stl_vector.h:1125:...` | 只留文件名：`stl_vector.h:1125:...` |

**识别方式：** 不按语言分支，只认四种位置写法（`文件:行`、`文件(行,`、`文件", line 行`、`文件 on line 行`），以及紧跟在用户文件位置之后的
源码摘录（`  行 |`）。只有用户文件的行号会变；任何路径只保留文件名。

**截断：** judge 返回的消息已按长度截断。消息达到截断长度时，先丢掉最后一个不完整的行再改写，避免把 `solution.cpp:12` 切成 `solution.cpp:1` 后改错。

**测试：** 用 [`rewrite-samples/`](./rewrite-samples/) 中 13 门语言的真实输出做固定样例，并补上驱动文件出错、前缀行出错、截断三类样例。
以后升级编译器或加语言时，先补样例再上线。

**标定：** 标定失败的消息给管理员看，用同一套改写。

### 5.4 驱动代码

- 不再有占位符。保存时只检查：非空、不超过 1 MiB。是否正确引用用户文件，由标定证明（参考解必须 AC）。
- 管理端编辑框给出骨架（C++：`#include "solution.cpp"` 加 `main`），并写明：用户文件名、前缀里已有哪些库、驱动代码可能出现在
  用户看到的报错里所以不放答案、少占全局名字（放进 `main` 里或加 `driver_` 前缀），用户不能定义 `main`。

### 5.5 驱动代码的可见性

**不主动展示：** 任何接口都不把驱动代码下发给普通用户，前端没有查看驱动代码的入口。**也不刻意遮掩：** 编译器或运行时在报错里
带出驱动代码的位置和源码行，就照常显示，与 LeetCode、LintCode 一致。驱动代码里不放答案。

### 5.6 修改驱动代码或前缀后的提示

修改驱动代码或前缀**不会**让标定自动失效（用户 2026-10-17 决定：先只做提示，不做标定指纹）。提示两处：

- 管理端：本次会话里保存过改动后的驱动代码，就在"驱动代码"一节的标题行显示"已修改，建议重新标定"和"重新标定"按钮，
  标定成功后收起。私有题目是自动保存的，每次保存弹提示会变成噪音，所以放在标题行而不是弹出提示。
- 公开检查的 CALIBRATION 项：judging-service 的 readiness 返回当前有效标定的批准时间 `calibratedAt`，problem-service 把它和
  驱动代码、该语言前缀的修改时间比较，晚于标定时在这一项的说明后附上"驱动代码（或前缀）在最近一次标定后修改过，建议重新标定"，
  检查结果仍为通过。这样不需要新的前端字段，管理端现有的公开检查列表就能显示提示。
  为此 `problem_language` 加 `judge_template_updated_at`，语言表带 `updated_at`；前缀直接改数据库时要一并更新这个时间。
  只比较时间，不比较内容。

### 5.7 前缀代码的挑选

前缀按语言复用，应当是挑选过的常用库，而不是"所有库"：

- 名字冲突：Java 同时通配导入 `java.util.*` 与 `java.awt.*` 会让 `List` 有歧义，`java.util.*` 与 `java.sql.*` 会让 `Date` 有歧义；
  C++ 的 `bits/stdc++.h` 加 `using namespace std;` 会占用 `y1`、`count`、`next` 等名字。
- 编译时间：C++ 每次编译 `bits/stdc++.h` 约 1～2 秒，以后可以用预编译头优化。

每门语言配一个"常见写法不冲突"的样例程序；改前缀前先跑它。

## 6. 各部分怎么改

### 6.1 契约（先改）

| 文件 | 变化 |
|---|---|
| `judge.schema.json` | `JudgeRequest` 加可选 `solutionSource`（用户文件全文）；`source` 说明改为"ACM 为完整程序；CORE 为驱动文件"；顶层 `x-language-files` 定义每门语言的驱动文件名与用户文件名（§5.2）；`JudgeResult.message` 写明位置是原始输出 |
| `judge-input.schema.json` | CORE 加 `solutionSource`、`solutionSha256`、`solutionPrefixLineCount`（ACM 禁止）；`completeSource` 说明改为"ACM 为用户程序；CORE 为驱动文件"；写明合并规则（§5.1）；`contractVersion` 保持 `"2"` |
| `custom-run-internal.schema.json` | trial 请求加可选 `solutionSource`、`solutionPrefixLineCount`；`source` 上限放宽到 1 MiB（CORE 时是驱动文件，用户源码的 256 KiB 上限仍在浏览器入口检查） |
| `problem-judge-snapshot.schema.json` | CORE 返回 `judgeTemplate`（驱动代码）与 `solutionPrefix`（前缀代码）；去掉占位符说明 |
| **新增** `calibration.schema.json` | problem-service → judging-service 的 `POST /internal/admin/calibrations`（请求、结果）与 `GET /internal/admin/readiness`（结果）。CORE 的标定请求以 `referenceSource` 带驱动文件，另带 `referenceSolutionSource`（合并后的用户文件）与前缀行数；readiness 的执行配置增加 `calibratedAt`（§5.6） |
| `web-api.openapi.json` | 管理端创建：`codeMode` 允许 `CORE`；更新：本来就不接收 `codeMode`，新增可选 `judgeTemplate`（CORE 用，ACM 必须省略）；管理端详情：语言新增可选 `judgeTemplate`（ACM 为 null）。新增字段都是可选的，保证前端在第 6 步之前照常编译；标定：`referenceSource` 说明改为"ACM 为完整程序，CORE 为核心代码"。用户端接口不变 |
| `execution-profile.schema.json`、`submission.json`、`judge-events.schema.json` | 不变 |

### 6.2 judge（Go）

- `contract`：`JudgeRequest.SolutionSource`。
- `language.Language` 加用户文件名；`cpp` 的取值与契约一致，加对齐测试。
- `flow`：带 `SolutionSource` 时，编译输入多放一个用户文件；解释型语言的运行输入也带上（现在没有解释型语言，留好分支并写测试）。
- 不做改写。
- benchmark 语料加一道 CORE 题（正确、WA、用户文件编译错误、驱动文件编译错误、运行错误），直接走 `POST /judge`，断言 judge 返回的原始位置。

### 6.3 problem-service

- **语言表：** 改写 V1，新增 `language(language_id, display_name, solution_prefix, updated_at, ...)`；`problem_language` 加 `judge_template_updated_at`（§5.6），并插入 `cpp` 一行，前缀为
  `#include <bits/stdc++.h>\nusing namespace std;\n`。开发阶段直接改库，不做管理界面。
- **创建：** 允许 `codeMode=CORE`；CORE 题的语言行 `starter_code=''`、`judge_template` 为空，此时不能公开。**代码模式创建后不可修改**，更新接口不接收它。
- **更新：** CORE 接收 `judgeTemplate`，做 §5.4 的检查；ACM 收到 `judgeTemplate` 返回 400；驱动代码变化时追加审计事件
  `PROBLEM_JUDGE_TEMPLATE_CHANGED`（不记内容）。
- **公开检查 LANGUAGE 项：** CORE 要求驱动代码非空，且该语言有前缀。
- **标定：** CORE 按 §5.1 合并前缀与参考核心代码，连同驱动代码、前缀行数发给 judging-service。
- **判题快照：** CORE 返回驱动代码与前缀；去掉"只支持 ACM"的拦截，语言仍只允许 `cpp`。
- **种子数据：** `DevProblemSeed` 加一道 CORE A+B。

### 6.4 judging-service

- 标定与 readiness 接口按新契约对齐。
- 构造 `JudgeRequest` 时，CORE 带上 `solutionSource`（标定、正式判题、自测）。
- 新增报错改写模块（§5.3），替换 `FormalWorker.safe()` 与 `TrialController.diagnostic()` 中的路径清洗；正式判题从 JudgeInput 取前缀行数，
  自测从请求取，标定从标定请求取。
- 数据库不变。

### 6.5 submission-service

- `Snapshot` 加驱动代码与前缀，日志和 `toString` 不输出它们。
- `validateSnapshot` 接受 `ACM | CORE`：CORE 必须带驱动代码与前缀，ACM 必须不带，否则 `INVALID_JUDGE_SNAPSHOT`（503）。
- 正式提交：CORE 冻结 `completeSource = 驱动代码`、`solutionSource = 前缀 + 用户代码`、`solutionPrefixLineCount` 及两个 SHA-256；
  两份大小分别检查，超出返回 413。Submission 的 `source` 仍是用户原文。
- 自测：同样合并，带上 `solutionSource` 与前缀行数。
- 取快照的响应上限从 64 KiB 放宽到 4 MiB：CORE 快照带着驱动代码（最多 1 Mi 个字符）与前缀。
- ~~读模型 `View` 补上 `codeMode`~~：实施时核对后撤回。`contracts/submission.json` 描述的是完整的 Submission 实体（还要求
  `userId`、`source` 等），`View` 对应的是 web 契约的 `SubmissionData`，本来就没有 `codeMode`，不是契约漂移，CORE 也不需要它。

### 6.6 gateway-service

`ProblemDtos` 与 `AdminProblemsController` 透传管理端的新字段，用户端 DTO 不变。

### 6.7 Web

- **管理端创建：** 对话框加"代码模式"（ACM / 核心代码），默认 ACM；创建后编辑页只显示、不可修改。
- **管理端编辑：** CORE 题加"驱动代码"编辑框（与起始代码同一个 `CodeBlockEditor`）；为空时标题行给"填入骨架"按钮——不预先填入，
  否则管理员改别的字段时骨架会被自动保存，悄悄满足公开检查；说明按 §5.4；改动后的提示按 §5.6；属性栏显示"核心代码 · C++"。
- **标定对话框：** CORE 题把"参考程序"改成"参考核心代码"。
- **用户端做题页：** `supported` 放开 CORE；编辑器以模板代码为起点，行号从 1 开始；模式标签显示"核心代码"。样例运行、自测、提交交互不变。
- **CE 与运行错误：** 原样显示 judging-service 改写后的文字，第一版不映射成编辑器内标记。
- 动 UI 前按 AGENTS.md 先读 `react.md` 与 `design-system/PROMPT.md`，交付前回答自检九问。

### 6.8 验证

- judging-service：改写模块单元测试（13 门语言样例 + 驱动文件、前缀行、截断样例）；标定、正式、自测三条路径的组装测试。
- judge：用户文件组装测试；benchmark 的 CORE 题在真实节点上通过。
- problem-service、submission-service：单元与 Testcontainers 测试覆盖 §6.3、§6.5 的每个分支，含合并规则。
- `scripts/work-002-e2e.py` 加 CORE A+B：创建 → 写驱动代码 → 上传数据 → 标定 → 公开 → AC / WA /
  CE（断言 `solution.cpp:<用户行号>`；签名写错时报错指向 `Main.cpp`）→ 自测的运行错误行号 → 断言浏览器响应、服务日志、
  Kafka 里没有驱动代码全文。
- Playwright（mock 后端）覆盖管理端模式选择与驱动代码编辑、用户端 CORE 做题页。
- 跑通后更新 `docs/status.md`，核对 `docs/product.md` §3.4 的证据。

## 7. 实施顺序

每一步独立提交、能独立编译：

0. 全局文档按 §3 修订（product.md、architecture.md、data-model.md）。
1. 契约（§6.1），生成的 TS 类型一起更新。
2. judge：用户文件、文件名对齐、benchmark CORE 题（§6.2）。ACM 请求行为不变。
3. problem-service 与 gateway：语言表与前缀、CORE 创建与更新、公开检查、标定合并、快照、种子数据（§6.3、§6.6）。重建开发库。
4. judging-service：标定契约对齐、`solutionSource` 透传、报错改写模块（§6.4）。
5. submission-service：合并、冻结、自测（§6.5）。
6. Web 管理端。
7. Web 用户端。
8. e2e 补 CORE 场景，跑隔离栈与真实节点，留证据，改 `docs/status.md`（§6.8）。

## 8. 延后的功能

为控制前期开发复杂度，下列内容先不做，以后按需要再考虑（用户 2026-10-17 决定）：

- 修改驱动代码或前缀后自动让标定失效（标定绑定内容指纹）；现在只做 §5.6 的提示。
- 前缀代码的管理界面；现在直接改数据库。
- 报错里驱动代码的遮蔽或替换；现在照常显示。
- 驱动代码按函数签名自动生成、C++ 以外的语言、诊断映射到编辑器标记、驱动代码版本历史。

## 9. 决定记录（2026-10-17，用户确认）

| # | 问题 | 决定 |
|---|---|---|
| D1 | 行号偏移怎么解决 | 驱动代码单独成文件 + 按用户文件名统一改写 |
| D2 | 代码文本怎么组织 | 三段：驱动代码（题目 × 语言）、前缀代码（每门语言一份，所有题目复用）、模板代码；用户文件 = 前缀 + 用户代码 |
| D3 | 前缀存在哪里 | problem-service 的语言表；开发阶段直接改数据库 |
| D4 | 改写放在哪里 | Java 服务（judging-service），不放 judge |
| D5 | 驱动代码的可见性 | 不主动展示（不下发到前端），也不刻意遮掩：报错里出现照常显示（§5.5） |
| D6 | 前缀行出错怎么显示 | 由设计决定：`[预置代码]:行:列`，保留摘录（§5.3） |
| D7 | 文件名 | 写进契约，judge 与 judging-service 共用 |
| D8 | 代码模式能否修改 | 创建后不可修改 |
| D9 | 改驱动代码或前缀后标定是否失效 | 不自动失效，先只做提示（§5.6）；更深的功能以后再考虑 |
| D10 | 数据库迁移 | 开发阶段直接改 V1，重建开发库 |
| D11 | 标定接口 | 补成正式契约文件 `contracts/calibration.schema.json` |
| D12 | JudgeInput 契约版本 | 不升级，保持 `"2"`（所有服务一起部署，没有新旧混跑） |
