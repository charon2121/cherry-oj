---
id: "VERIFY-055"
type: "verify"
title: "为CI软件包下载增加有界同源地址回退"
status: "draft"
work: "WORK-054"
owners: ["codex/root"]
depends_on: ["TASK-118"]
related: []
implements: []
verifies: ["ISSUE-018#AC-001", "ISSUE-018#AC-002", "ISSUE-018#AC-003", "ISSUE-018#AC-004", "ISSUE-018#AC-005", "TASK-118"]
tags: []
result: "pending"
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# VERIFY-055：本地验证与独立复核通过，待Linux实测

## 验证对象

当前工作区的CI显式同源连接回退实现及配套测试；未提交推送。

## 对应要求

AC-001至003已取得本地证据；AC-004真实Linux全量下载及AC-005人工验收尚待完成；独立源码复核已通过，整体结果pending。

## 检查与结果

CI34571258539的3a74fc7三次尝试均失败在rootfs下载TLS握手；日志分别保存在/private/tmp/cherry-task112-34571258539-build、attempt2-build及attempt3-build目录（后二者使用同一前缀）。既有Go编译/指定Linux单测已完成，但未启动内核/原生/业务套件。最新本地官方源索引HEAD 200，两个IP上的同一cpp-13包HEAD也200。

本地直接解析archive.ubuntu.com并对每个地址进行TCP、SSL默认链/主机名验证、索引HEAD（socket5秒、4并发）：185.125.190.81=200/2.932s；185.125.190.82=TLS TimeoutError/5.251s；185.125.190.83=200/1.292s；91.189.91.81=200/1.079s；91.189.91.82=200/3.492s；91.189.91.83=200/1.578s。公开地址仅为本次诊断，不可抄作长期固定配置。

## 未通过项

尚无新Linux全量下载及业务结果，独立源码复核已通过；不能宣称下载阻断已解除。

## 范围检查

经用户意图闸passed及明确实施许可，改动限定下载入口、新transport与测试、CI prepare参数及测试/README。包锁、rootfs构建、安装器、业务与现有服务未改，未提交推送。

## 遗留问题

GitHub旧日志没有失败peer，不能断言它命中了本地异常IP；原浏览器business.io失败也仍未定位。

## 剩余风险

源服务可能变化，多地址回退可能仍全部失败；不预先承诺恢复，失败不能算PASS。

## 结论

意图闸已由用户签署，本地实现与验证完成；Linux和最终人工验收未完成，整体pending。

## 文档检查

462份开发文档通过；在仅供检查的临时Git索引纳入新文档后，531份Markdown入口/链接通过，真实暂存区未变。常规链接检查在新文档未跟踪时会拒绝WORKS的新入口，这是跟踪状态要求，不代表缺少文件；本轮未提交推送。WORK-054 board确认意图闸可以签署，TASK-118仍todo，VERIFY结果pending。

## TASK-118本地实现与验证（2026-09-11）

先以旧标准库HTTPSConnection运行同一可控DNS/socket/TLS场景：首TCP成功、TLS超时、第二地址可用。/private/tmp/cherry-work054-old-transport.log为1FAIL，失败断言明确“TLS timeout prevented trying the next resolved address”。随后只替换连接类为AddressHTTPSConnection，同样期望与夹具通过；其他负例覆盖关闭失败socket、证书/未知异常终止、地址去重/8个上限、共同30秒与TCP+TLS每地址5秒、DNS迟返、HTTP发送/响应失败不重试、来源与重定向/代理边界。

新增连接适配仅由CI参数显式选择；默认download仍用urllib.request.urlopen。保留原索引/路径/流式大小/SHA256/resume逻辑，并以两种路径读取相同字节、HTTP/响应体/哈希/大小异常及非法文件负例核对。重定向附加64KiB响应体上限，防止标准库重定向处理无界排空；不影响原始未启用路径。

`python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work054-basic-final`：15安装+19rootfs+62CI=96项通过、零skip，Python AST及shell语法和报告校验通过。证据目录及/private/tmp/cherry-work054-basic-final.stdout保存完整结果。真实本地TLS使用临时OpenSSL证书与随机127.0.0.1端口：可信localhost成功，不可信链/主机名错误/过期分别得到SSLCertVerificationError；临时目录、连接、线程测试结束均关闭。默认工具沙箱曾禁止回环bind，允许受控端口后的相同测试通过，不把沙箱权限错误当实现失败。

开发中修正了测试里的Linux专用errno硬编码、OpenSSL过期证书生成方式和代理mock方式；上述最终完整运行已通过，尚未在Linux验证OpenSSL/标准库组合。随后用户已明确授权本工作独立复核、修正后commit/push main及运行处理GitHub CI，见下方复核记录。

新适配真实只读冒烟：以`source_urlopen('https://archive.ubuntu.com/ubuntu/')`请求noble主索引，最多读取2MiB，HTTP200/1401160字节；实际peer91.189.91.81、首次TLS成功。该单次本地读取不证明发生回退，也不等同全部包哈希验证或LinuxCI恢复。

## 独立复核与修正（2026-09-11）

用户本轮授权后，work054_review只读复核发现P2：带巨大Content-Length却截断的重定向响应可能在有界read短读后，仍被urllib默认处理器无界排空。已拒绝剩余声明长度，并在交回标准库前关闭响应；新增实际HTTPResponse和完整http_error_302路径，覆盖正常、chunked、无长度、超限和截断，检查底层读取大小、关闭及失败不跳转。复核者复测10项TransportTests通过，确认P2关闭、无剩余源码发布阻断；真实Linux证据仍待CI。

修正后运行`python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work054-reviewed-basic`，15安装+20rootfs+62CI=97项通过，零skip；Python AST、shell语法、报告检查通过。完整输出为/private/tmp/cherry-work054-reviewed-basic.stdout。代码与证据经过复核后发布，尚不把本地结果计为Linux下载恢复。
