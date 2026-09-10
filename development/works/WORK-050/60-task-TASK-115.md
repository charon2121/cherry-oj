---
id: "TASK-115"
type: "task"
title: "明确语言集成测试的编译期限与失败诊断"
status: "done"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-109"]
related: []
implements: ["CAPABILITY-008#REQ-005", "CAPABILITY-008#AC-006"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "development/README.md", "docs/engineering", "development/works/WORK-050", "development/works/WORK-051", "apps/judge-engine/internal/judge/language", "apps/judge-engine/internal/sandbox/container", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", ".github/workflows/language-diagnostic.yml"]
write_paths: ["development/works/WORK-050", "apps/judge-engine/internal/judge/language/languages_e2e_test.go", "deploy/sandbox-linux/ci/diagnose_language.py", "deploy/sandbox-linux/ci/diagnose_language_test.py", ".github/workflows/language-diagnostic.yml"]
forbidden_paths: ["apps/judge-engine/internal/sandbox", "apps/judge-engine/internal/judge/language/languages.go", "apps/judge-engine/cmd", "apps/server", "apps/web", "contracts", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", ".github/workflows/ci.yml", "AGETNTS.local.md"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-115：明确语言集成测试的编译期限与失败诊断

## 任务目标

首轮诊断已完成；用户追加多轮验证，并条件授权：解释得到支持后，只适当放松测试编译墙钟。按PLAN-034追加两轮各3台的首次/后续及JDK文件预读对照；结果支持时采用测试专用15秒并在第三轮3台未预读VM及正式CI验证。

## 依据

CAPABILITY-008 REQ-005、AC-006，PLAN-034。CI 34470867753 全绿，随后仅文档提交的 34471753900 在 Java 编译 5.00 秒、exit=-1 失败；镜像、Go/JDK、命令和源码相同。两个运行均保留。host 的 Wait 将程序退出表达为 Usage（含 Reason/Signal），不一定返回 error；诊断必须同时记录二者。

## 可查看范围

以 read_paths 为准；另可只读查看本机 Go/JDK 工具链帮助与对应源代码，核对调度和启动参数，不读取私有配置。

## 可修改范围

以 write_paths 为准。语言测试保留全部三语言和内部类、产物迁移及输出断言。可加入仅测试二进制的编译期限参数，初始默认5秒；满足PLAN-034条件后才改默认15秒，运行期限不变。

诊断使用独立手动 GitHub workflow，每轮最多三个一次性 Linux job，每 job 最多 10 分钟；不改现有 ci.yml。只读环境记录限镜像/内核、CPU/内存、JDK/Go 路径版本和 CPU 压力，禁止输出完整环境或凭据。驱动编译当前真实 Go 测试二进制，以原 5 秒重复执行并保留全部成功/失败；另用同一 Java 源码分段测量 javac/jar CPU 与墙钟，每个命令独立有界，结果仅作诊断，不计入正式基线 PASS。

可对 JVM 启动参数作候选实验，明确标注、核对产物与运行结果；不得写入生产或正式 CI 默认配置。输出有界，独占临时目录，失败回收本次进程组及文件，不使用用户服务器或 sudo。可对固定JDK运行库/启动器作有界只读预读，不执行JVM、不清空系统缓存；与未预读组交替分配。测试期限遵循本轮条件授权，生产代码及工具JVM参数仍不得修改。

## 禁止修改

生产 host 默认 5 秒、Linux 编译/运行限额、资源断言、语言注册命令、helper、cgroup、公开契约、当前服务器、IDEA 与 ci.yml 均不变。禁止用预读后通过替换未预读失败或重跑刷绿；不能无依据扩大到30秒。预读只作因果对照，不进入正式CI。

## 依赖

TASK-109 已完成，WORK-050 意图闸已通过；用户后续明确要求“继续确认，然后优化，给我一个最终结论”，授权首轮诊断和有界实验；之后用户已要求追加多次验证并条件允许适当放松测试编译墙钟。仍不授权独立子智能体委派。

## 产出

真实错误/资源日志、可复现的手动诊断入口、各阶段和候选优化的对照报告、明确的根因结论及测试期限建议。

## 完成标准

- [x] 保留原 5 秒与所有功能断言，失败日志包含 Wait error、Reason/Signal 和 CPU/墙钟。
- [x] 在 Linux 记录真实首次和重复样本，分辨完整编译管线与 javac/jar 阶段；无结果被覆盖为 PASS。
- [x] 有界验证可行优化，区分已证事实、推断与无法从旧日志恢复的原因。
- [x] 本地 race/vet、诊断驱动验证通过，真实诊断报告可核对代码 SHA、环境与清理。
- [x] 给出是否需要测试专用期限的最终建议，不自行采纳新期限，不宣称全部 CI 已完成。

## 验证

本地 Go 语言及完整 race/vet、Python 驱动边界和语法检查、workflow 静态检查；手动一次性 GitHub VM 运行原始语言测试与候选诊断。人工终止/超时也须保存报告和回收。诊断 job 成功只表示测量完成，原始测试失败必须单独显示，不能用于替代 ci.yml 的通过状态。

## 风险

历史两轮没有 Usage 与分段日志，无法恢复精确 JVM/调度原因；需要复现或给出证据上限。仅同镜像不代表同负载；首次样本与暖缓存样本分开标记，避免偏置。若只有宽期限才通过，先说明代价和依据，等用户决定。

## 执行记录

- 2026-09-10：先形成 30 秒待审提案，没有实施。
- 2026-09-10：用户要求先确认和优化再决定；本任务按 PLAN-034 收窄为原期限诊断，修改边界后才开始实施。
- 2026-09-10：状态变更：todo → ready。原因：用户已允许先诊断并优化，30秒仍未批准；PLAN与精确诊断路径已更新，WORK050意图闸已通过
- 2026-09-10：状态变更：ready → doing。原因：保留原5秒期限，补Usage诊断并开展独立Linux有界测量
- 2026-09-10：仅补语言Usage日志及独立手动诊断，原5秒不变；本地完整race/vet、43项基础单测与actionlint通过，准备一次性Linux实测。
- 2026-09-10：用户明确授权推送，451b4d6已发布；诊断34476105471完成三台VM全部测量，2/3首次Java触发wall，随后6次原配置和6次候选通过。正式34476081108为7/8通过，C++与Java均复现wall；详见VERIFY-051。建议测试独立编译上限，尚未采纳30秒或候选参数；本任务仅完成诊断，不替代正式CI通过。
- 2026-09-10：状态变更：doing → done。原因：三台Linux原5秒诊断和候选对照完成，复现wall并记录首次与重复差异；已给出独立测试编译上限建议，未采纳新期限，正式CI失败原样保留

- 2026-09-10：追加用户条件授权与PLAN-034多轮验证边界；首轮已完成记录保留，新阶段需完成对照及满足条件后的验证。
- 2026-09-10：状态变更：done → doing。原因：开始两轮交替预读对照，初始期限仍为5秒，条件成立后再修改测试默认值
- 2026-09-10：状态变更：doing → done。原因：追加两轮6台交替对照支持文件加载贡献；按用户条件授权仅测试编译15秒，第三轮3台未预读VM及正式8项CI通过，原5秒负例和严格资源限额保持

## 追加完成标准

- [x] 两轮共6台新VM的交替预读/未预读对照完整保留，记录首次与后续差异。
- [x] 依据对照判断解释是否获支持，明确不能由系统PSI定位精确进程阻塞。
- [x] 若满足条件，仅测试编译默认15秒；第三轮3台未预读新VM及正式CI核验，原5秒负例与运行/资源限额保持。

- 2026-09-10：两轮交替对照均支持文件加载对首次延迟的贡献，达到预先记录判据；按用户条件授权仅将功能测试编译默认改15秒，第三轮未预读VM与正式CI尚待验证。

- 2026-09-10：e44f9b4的第三轮三台未预读VM全部通过；首次Java实际最高10.559792秒、随后约0.47秒，固定5秒负例继续生效，清理均确认。正式CI34478647561为8/8通过；本任务完成，不等于WORK-050其余任务完成。
