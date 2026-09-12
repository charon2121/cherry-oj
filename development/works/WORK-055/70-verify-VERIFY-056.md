---
id: "VERIFY-056"
type: "verify"
title: "分离CI软件包准备与回归测试"
status: "draft"
work: "WORK-055"
owners: ["codex/root"]
depends_on: ["TASK-119", "TASK-120"]
related: []
implements: []
verifies: ["IMPROVEMENT-005#AC-001", "IMPROVEMENT-005#AC-002", "IMPROVEMENT-005#AC-003", "IMPROVEMENT-005#AC-004", "IMPROVEMENT-005#AC-005", "TASK-119", "TASK-120"]
tags: []
result: "pending"
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# VERIFY-056：共享包与独立冷下载的验收证据

## 验证对象

IMPROVEMENT-005 AC-001至005，TASK-119和TASK-120。当前只完成文档方案，实施和远端测试均未运行，result保持pending。

## 检查与结果

本轮只读核对ci.yml三个独立prepare调用、prepare.py下载600秒和VERIFY-055两轮证据。文档检查实际结果：scripts/work check通过471份开发文档（WORK-033既有状态提示保留）；新文档以git add -N标记后，python3 scripts/docs_test.py通过540份Markdown；git diff --check通过。未运行实现测试、Linux下载或CI，未提交推送。

## 计划证据

分别记录无缓存/命中缓存/两次独立冷检查的run与源码、包锁、harness、请求次数、传输失败与清理；核对三个消费者实际构建，不使用旧PASS。负例按AC-002/003逐条列出命令与结果，触发路由按AC-004逐类验证。

## 未通过项

本工作所有实施验收尚未执行。WORK-050的proc采样失败和business.io失败独立保留；两轮全绿基线未成立。

## 范围检查

本轮仅新工作文档及工具生成索引/总览，无业务代码、workflow、包锁或远端资源变更。成熟工具冷获取效果和缓存服务可用性仍需实测。

## 结论

可供意图审核，尚未实施，不具备验收通过结论。

## 对应要求

front matter逐条verifies AC-001至005；分别对应冷/热共享、污染拒绝、有界重取、冷检查触发和证据复核。

## 遗留问题

WORK-050独立承接业务io及proc检查，未在本轮处理。

## 剩余风险

首次获取与缓存服务均可能失败，必须用实际Linux运行验证，不以设计预期替代结果。

## TASK-119本地实施证据（2026-09-11）

实际核验WORK-055意图闸passed，用户在后续消息明确允许实施。完成ci/packages.py及packages_test.py、prepare.py及prepare_test.py；未修改rootfs默认下载入口、包锁、workflow或apps。packages.py只复用原索引解析与包路径函数，CI获取使用curl；每次完整对象重取最多3次，总600秒/单次120秒/连接30秒/持续30秒低于64KiB/s，4包并发、32MiB索引及100MiB包上限。HTTP头只保留有界内部缓冲并提取状态，错误正文/头不进入日志；HTTP拒绝及重定向不进入网络重试。

校验复制使用逐组件不跟随链接的目录FD、普通单链接文件检查、打开前后身份/集合一致性检查及复制字节哈希，成功集合才交付。prepare的--packages路径在rootfs构建前调用该校验，原无参数路径仍为address-failover与600秒。命中已有包路径不创建Curl，测试证明requests=0。

最终命令：python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work055-task119-final；输出/private/tmp/cherry-work055-task119-final.stdout。15安装+27rootfs+80CI=122项通过、零skip，Python AST、shell及报告检查通过。新增17项包测试与1项prepare测试。运行环境为本机macOS、Python3.12、curl8.7.1；只使用临时loopback HTTPS夹具，不访问公网或用户服务器。首次受沙箱端口绑定限制的测试未计通过，获工具执行许可后完成完整回归。

正反例包括完整包集/2至4并发、无网络已有包、重复与非法锁、缺/多包、错误摘要、symlink/硬链接/FIFO/超限、校验期间文件替换、原输出保留、失败临时目录回收、最多三次尝试、共享截止、真实子进程超限/取消/期限后被回收。真实curl通过受控TLS，拒绝不可信证书与错误主机名、404及跨源重定向；正文截断和持续低速分别先失败后重新下载完整对象。低速夹具只将测试观察窗口缩为1秒，不修改生产30秒设置。

TASK-119本地实现完成但仍保持doing：完成标准要求独立复核，PLAN-039要求本工作委派单独授权，尚未启动新复核。TASK-120依赖TASK-119，保持todo，未提前修改workflow。Linux冷/热缓存与业务运行尚未执行，不能根据本地122项宣称CI下载已恢复或本工作验收通过。未提交推送。

## 独立复核修正及TASK-120本地接线（2026-09-11）

用户明确授权本工作独立复核。work055_review发现TASK-119发布后证据失败/取消留下输出P2；已先在私有preparation目录完成校验与元数据，发布后按dev/inode身份清理本次输出，不删除已被替换的目录。补元数据失败、发布后真实SIGTERM、最终日志异常及目录替换3项回归，独立复跑20项packages测试通过，无剩余阻断。TASK-119完成本地实现与复核后才进入TASK-120，Linux集成验收并未提前宣称完成。

TASK-120新增sandbox-packages统一准备任务；精确缓存键由平台/包锁/获取校验脚本产生，无restore-keys，命中仅复制校验，未命中下载一次。保存完整已校验cache，再上传本run/attempt的verified目录；三个原消费者needs该任务，通过--packages重新校验且保留当前源码/rootfs构建及原套件。冷workflow不读写cache/artifact，相关路径的PR/main push、每周一02:00UTC和手工触发，执行全包下载与rootfs构建；独立并发组，失败日志保存和项目临时目录清理。report.harness_sha纳入新workflow，新增变更使证据失效测试。

官方GitHub只读API核验actions/cache v4 commit=0057852bfaa89a56745cba8c7296529d2fc39830，actions/download-artifact v5 commit=634f93cb2916e3fdff6788551b99b062d0335ce0，均为commit对象；核对restore/save及download元数据：contents只读，cache精确key，download默认当前run，无额外github-token/run-id覆盖。现有checkout/upload固定SHA沿用。没有安装工具或访问用户服务器。

最终本地命令python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work055-task120-basic，日志同前缀.stdout及目录内install.log/rootfs.log/ci.log；15安装+27rootfs+92CI=134项通过、零skip，AST/shell/报告检查通过。Ruby Psych解析两份YAML通过；8项workflow测试实际执行提取的Python编排、模拟命中/未命中/冷路径命令，检查路由/权限/同轮交付及shell语法，不将其冒充GitHub运行验证。

work055_review对TASK-120再独立审查并复跑8项workflow测试全部通过，未发现阻断。记录操作限制：artifact按run_attempt隔离，单独重跑失败消费者会因缺本attempt artifact失败，必须重新触发完整workflow；失败历史保留，不能以重试掩盖。包准备及冷job15分钟仍为最终边界，不保证所有子命令同时耗尽各自最大期限时可完成。

本批代码和测试可供发布审核，但尚未提交或推送。PLAN-039的本工作提交/推送授权尚未获得；真实无缓存/命中缓存两轮及两次独立冷检查未执行，TASK-120保持doing，VERIFY结果pending。原business.io与proc采样失败未改，完整CI和重构基线仍未通过。

- 2026-09-12：用户明确授权本批代码、测试与关联记录commit/push origin/main及运行处理计划中的Linux CI；现进入发布验证阶段，验收闸仍pending。
