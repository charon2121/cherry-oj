# 提交流程

> 本文从 [`AGENTS.md`](../AGENTS.md) 拆出，按需阅读；根目录只保留每次都必须遵守的部分。

## 4.0 先启用 git hooks（每个新克隆跑一次）

```bash
sh scripts/setup-hooks.sh
```

它做的事只有一行 `git config core.hooksPath .githooks`。**不能自动生效**——
`.git/hooks/` 不进版本库，而 git 也不会自动信任仓库里的可执行脚本
（否则 clone 一个陌生仓库就等于给了它任意代码执行权限）。

| hook | 检查 | 耗时 |
|---|---|---|
| `pre-commit` | Go 格式/vet、contracts JSON、全局/开发文档系统 | ~0.5s |
| `commit-msg` | 标题符合 Conventional Commits | 瞬时 |
| `pre-push` | `go test -race -count=1 ./...`（无 Go 改动时自动跳过） | ~6s |

两条设计取舍：

- **pre-commit 只检查、不改写。** 自动 `gofmt -w` 再 `git add` 回去看着省事，
  但 `git add -p` 只暂存一半时，它会把你没打算提交的另一半也带进去。
  「提交的内容 == 你亲手暂存的内容」不该被 hook 破坏，所以只报错并打印该跑的命令。
- **测试放 pre-push 不放 pre-commit。** 6 秒 × 一天十几次提交，人会开始用
  `--no-verify` 绕过，hook 就形同虚设。

临时跳过：`git commit --no-verify` / `git push --no-verify`。
**hook 不是执行边界，CI 才是**——它只是把反馈从 2 分钟提前到 1 秒。

## 4.1 五步

**① 本地自检。** 装了 hooks 的话这步基本自动完成，剩下要手动的只有动过依赖时：

```bash
cd apps/judge-engine && go mod tidy   # 之后确认 git diff 无输出
```

hook 提前运行部分检查；CI 还会验证完整构建和 Java 测试，本地通过不等于 CI 已通过。

**② 分 commit，一个 commit 一件事。** 跨越多个关注点就拆开
（例：一次改动拆成「契约」「配置模块」「sandbox 接线」三个）。

**每个 commit 都要能独立编译。** 拆的时候检查一下：后面 commit 才引入的符号，
前面的 commit 有没有引用到。

**③ 写 commit message**（格式见 §4.2）。

**④ `git push origin main`。**

**⑤ 看 CI 结果。** 红了先修再继续，**别在红的基础上叠新提交**——
第二个人来看时分不清是谁弄红的。

## 4.2 commit message

- Conventional Commits + 中文正文：`feat(scope): 摘要`、`fix(runner): …`、
  `refactor(sandbox): …`、`test(container): …`、`ci: …`、`docs: …`、`chore: …`。
- **正文写「为什么」，不写「改了什么」**——改了什么 diff 里有。
  重点记两件事：**这个问题不改会怎样**，以及**当时在两个方案间是怎么权衡的**。
  三个月后 blame 到这一行时，需要的正是这两样。

## 4.3 CI 会检查什么

只保留 `.github/workflows/ci.yml`，push 到 main 和所有 PR 自动运行，也支持手动触发。
五个任务独立执行，不再准备整套部署环境或汇总多份验收报告：

| job | 检查 | 要守住什么 |
|---|---|---|
| `checks` | 契约结构与引用、校验器和记录工具测试、文档链接、历史原件完整性 | 共享契约有效，文档可查，历史依据不丢失 |
| `go` | gofmt、依赖同步、vet、build、`test -race -count=1 -p=1 ./...` | 判题机能编译，基础行为与并发安全不退化 |
| `web` | `npm ci`、`npm run check`、`npm run build` | 生成物同步，格式、类型、组件测试和生产构建通过 |
| `java` | JDK 21 与根 Maven wrapper 的 `clean verify` | 所有 Java 模块能编译、测试并打包，不以跳过测试代替验证 |
| `sandbox` | 安装 libseccomp 编译依赖、`make -C apps/sandbox` | C 执行器能在目标 Linux 平台编译，保留 `-Werror` |

Go 的依赖检查并入 `go`，契约与文档检查合并为 `checks`。Go 任务仍打印 g++、Python、Java
版本，方便核对依赖工具链的测试是否跳过。Java 中的单服务 MySQL、Redis、Kafka 测试通过
Testcontainers 使用 runner 的 Docker；依赖外部真实 Linux 判题节点的测试需另行按需运行。

完整部署、真实沙箱内核、容器联调、全栈浏览器、Storybook 构建、冷下载和语言诊断已退出日常 CI，
相关独立工作流已删除。需要这些验证时使用保留的脚本和对应环境，实际结果单独报告；
核心 CI 通过只证明上表所列检查通过。

## 4.4 文档与开发记录

`docs/` 保存已经确认的全局事实。只有明确调用 `$dev-work` 时，才使用 `development/` 的过程管理：
每项工作一份短说明，复杂方案和较长证据按需拆附件，通过聊天确认目标和交付。普通开发不要求创建
WORK、编号 TASK 或阶段状态；旧记录保留供查证。

用户行为或技术路线变化时，先核对已确认规则、说明范围和关键代价，解决会影响决定的未知。
完成后报告实际验证与遗留问题，不把测试成功当作人工接受。技术债写清原因和退出条件，有现成依据
时附引用，不为待办强制创建过程文档。具体写法见 [`development/README.md`](../development/README.md)。

只有已经确认且会约束多个未来工作的结论才整理进 `docs/`，保留来源。

**契约先行**：改 `contracts/*.json` → 再改各语言类型 → 再改实现。反过来做必然漂移。

---
