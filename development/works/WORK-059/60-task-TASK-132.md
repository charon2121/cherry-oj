---
id: "TASK-132"
type: "task"
title: "业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位"
status: "doing"
work: "WORK-059"
owners: ["team/server"]
depends_on: ["ISSUE-020", "DESIGN-052"]
related: []
implements: ["ISSUE-020#AC-001", "ISSUE-020#AC-002", "ISSUE-020#AC-003", "ISSUE-020#AC-004"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "development", "docs", "contracts", "apps/server", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "scripts/work", "scripts/docs_test.py"]
write_paths: ["development/works/WORK-059", "development/WORKS.md", "deploy/sandbox-linux/ci/business_journal.py", "deploy/sandbox-linux/ci/business_test.py", "deploy/sandbox-linux/ci/business_api.py", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/api", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/api", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/problem", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/api/ProblemExceptionHandler.java", "apps/server/problem-service/src/test/java/com/cherryoj/problemservice/api"]
forbidden_paths: ["apps/judge-engine", "apps/web", "contracts", "apps/server/pom.xml", "apps/server/problem-service/pom.xml", "apps/server/problem-service/src/main/resources", ".github", "development/works/WORK-058", "development/works/WORK-060"]
created_at: "2026-09-15"
updated_at: "2026-09-22"
---

# TASK-132：业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位

## 任务目标

修复已确认的诊断与错误边界缺口，收集原 PATCH 500 的可关联证据，构造确定性回归并修复根因。
前两步可独立验证；没有根因证据时不得将整项标为完成。

## 依据

[ISSUE-020](./10-issue-ISSUE-020.md) 的 AC-001 至 AC-004，
[DESIGN-052](./30-design-DESIGN-052.md) 的三步方案。用户已签署意图闸，当前实施诊断修复与根因定位。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 结构化、安全且有界的网关异常日志；CI 导出保留 requestId，并严格兼容旧消息模板。
- 只识别固定公开码的 CI 分类器；题目服务稳定内部未知异常响应及 HTTP 组件回归。
- 原故障的证据、根因分析与修复前后回归；无复现时如实保留该项未完成。
- VERIFY-060 逐条证据，不重复建立旁路报告或修改其他工作的记录。

## 完成标准

- [x] AC-001：正常关联与导出拒绝路径已有测试；敏感合成标记不出现在日志事实或响应中。
- [x] AC-002：分类器与异常边界分别验证，鉴权、框架 HTTP 状态和网关上游映射组件回归通过。
- [ ] AC-003：有对应原链路的失败证据和确定性回归，证明实现改动消除已定位原因。
- [ ] AC-004：Python 回归、Java verify 与 93 项真实 CI 通过；没有通过重试写请求、提高超时或降低断言取巧。

## 验证

1. Python：`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/sandbox-linux/ci -p '*_test.py'`。
   覆盖合法新旧日志、requestId 关联、非允许来源、完整字段校验、循环/过长原因链、敏感正文和分类器。
2. Java 定向：从 apps/server 运行 `./mvnw -pl gateway-service,problem-service -am test`，检查实际
   surefire 结果。通过后 `./mvnw clean verify`；依赖容器的测试未执行不能算通过，须记录环境限制。
3. HTTP 组件场景：未知应用异常、既有业务异常、校验/解析失败、上传过大、框架 HTTP 异常、鉴权失败；
   保持约定状态，安全摘要、requestId 与日志一致，上游 500 对外仍为 503。
4. 已授权提交的完整 CI：basic 5、kernel 63、native 10、business 15。记录提交 SHA/runId/attempt，
   保留首次失败报告。无失败时不宣称 AC-003 通过，按 DESIGN-052 的三轮上限结束主动复现。
5. 文档：`scripts/work check`、`python3 scripts/docs_test.py`、`git diff --check`。

## 风险

范围只覆盖确定的诊断修复与有限异常边界；根因落在其他文件时先更新方案和路径。
原有 business_journal.py/business_test.py 增量属于本工作，实施时在其基础上修正，不能直接当成已验证成品。
不修改数据、超时、依赖或安全配置，不自动重试写请求，不签署人工闸。

## 执行记录

- 2026-09-15：创建任务。
- 2026-09-22：用户要求解决问题；明确调查与实施路径，补齐验收与测试方案。只读验证发现分类器、
  正文提取和请求关联缺口；尚未开始本轮业务实现，详见 VERIFY-060。
- 2026-09-22：状态变更：todo → ready。原因：用户已签署意图闸，范围与方案已确认
- 2026-09-22：状态变更：ready → doing。原因：开始诊断与异常边界修复
