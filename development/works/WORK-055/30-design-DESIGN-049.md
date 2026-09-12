---
id: "DESIGN-049"
type: "design"
title: "分离CI软件包准备与回归测试"
status: "checked"
work: "WORK-055"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-005"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# DESIGN-049：共享经过核验的软件包，保留冷下载检查

## 背景

依据IMPROVEMENT-005及VERIFY-055。仅改变CI依赖获取，不改变受测程序与环境内容。方案尚待本工作意图闸签署，WORK-054旧授权不覆盖此工作流调整。

## 整体方案

ci.yml新增packages准备job。先按精确键恢复完整包缓存，校验成功后上传本轮artifact；未命中时下载、全量校验并保存。kernel/native/business通过needs获取同一轮artifact，独立校验，再用当前提交构建二进制及rootfs。保留现有三个job的名称、套件、清理与15/20/40分钟期限；各自不再访问Ubuntu源。

缓存只含56个锁定deb，不含二进制、rootfs、账号、数据回执、标定或PASS报告。键包含格式版本、Ubuntu24/amd64、包锁SHA256和获取/校验脚本摘要；不使用restore-keys模糊匹配。缓存不可信，命中错误立即失败且不覆盖、不删除远端缓存；修正需新格式键。上传前和每个消费者使用前分别校验集合、大小及SHA256。当前源码SHA另记在每轮证据，不以源码SHA强制所有无关改动失去包缓存。

## 模块与数据

新增ci/packages.py负责包缓存目录校验、冷获取编排和有界证据；ci/prepare.py增加显式--packages参数，接收外部只读包来源并复制到本轮私有目录后使用。无该参数的现有路径保持。消费者不继承缓存中的build.json或成功标记。

必要时从rootfs/download.py复用包锁与索引解析函数，允许小范围提取可注入传输入口；其默认CLI、urllib默认部署及既有address-failover行为保持。禁止把本工作扩成通用下载框架。

## 冷获取与成熟工具

CI传输采用runner提供的curl，执行前记录版本并检查所用参数支持；不自动安装或升级，不声称只换工具即可解决。Python只编排包锁定位、校验、重取分类和证据。两个Packages.xz索引及deb均使用同源HTTPS，证书与主机名校验开启；不继承个人curl配置、代理或自定义CA。不自动跟随跨源重定向，当前方案遇到重定向明确失败，不固定或屏蔽IP，不换源。

初始可审核预算：全获取命令600秒；索引串行、deb最多4并发；每请求最多3次尝试，单次最多120秒、连接最多30秒；持续30秒低于64KiB/s中止。次数、退避和所有请求共享剩余总期限，2秒/4秒退避不得突破总期限。只对curl传输超时、正文截断和接收断连（退出码28/18/56）重新获取完整对象；HTTP错误、证书拒绝、大小和摘要不符立即失败。不得启用retry-all-errors。以上阈值是候选实施值，需受控及Linux验证，失败后不自行提高。

每次用全新私有临时文件，失败即清理，不做Range拼接。索引压缩体沿用32MiB上限，单包100MiB；监控实际文件量，不能只信Content-Length。完整校验后才原子交付；600秒硬截止负责终止并等待所有curl子进程，不依赖线程Future取消假装中止读取。诊断256KiB、命令日志2MiB保持。包准备job拟定15分钟，含缓存/artifact传输、校验和清理，不增加测试服务限额。

## 安全与失败

缓存和artifact只接受锁定平面文件名集合，拒绝额外/缺失/重复包、路径穿越、symlink、magic-link、非普通文件及多链接文件。校验和复制使用不跟随链接的打开方式，核对文件身份，在本轮私有目录流式限长、复制与哈希；实际送入rootfs的文件必须是被校验的字节，避免检查旧文件再使用被替换路径。消费者在任何sudo步骤前完成验证。artifact仅从当前run和明确名称获取，不下载任意他人run结果。

所有新增Actions固定审核过的完整commit SHA，contents:read、checkout不保留凭据。不使用pull_request_target，不向PR暴露密钥；不为写缓存授予仓库写权限。来自PR的缓存仍按不可信文件处理；实际可见范围由GitHub隔离规则约束。

## 监控与部署

新增sandbox-download-cold.yml：PR和main push命中rootfs目录、CI目录、build-release.sh及两份相关workflow时强制运行；weekly schedule周一02:00 UTC和workflow_dispatch可运行。冷检查永远空目录、不恢复/保存缓存、不消费共享artifact，走同一获取函数，验证全部包并构建rootfs。独立并发组，不取消日常CI。周期任务检查默认分支，调度延迟不视为已执行。

无关改动不触发冷workflow，日常CI仍跑。本轮不改仓库branch protection；有条件冷检查不当作所有PR必须出现的固定检查。TASK-113验收必须查看本SHA所有应运行检查，不能把应跑未跑或失败的冷检查忽略。

记录当前sourceSha、包锁、工具/脚本摘要、cache hit/miss、每次传输结果和总期限终止，消费者记录实际包来源及重新校验事实。report.py的harness摘要纳入新增workflow，防止旧准备证据被新脚本接受。

## 迁移与兼容

回退workflow与prepare接线到原独立下载，缓存无需远端删除。正式部署默认行为不变。缓存仅减少重复获取，首次获取仍可能失败；两轮冷路径不能完成或传输实现继续膨胀时停止扩展并重审，不自动换镜像。原business.io与proc采样问题分别交WORK-050，不以本工作验收代替完整CI全绿。

## 参考

[curl传输期限与低速控制](https://curl.se/docs/manpage.html)、[GitHub缓存规则](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching)、[任务间artifact](https://docs.github.com/en/actions/tutorials/store-and-share-data)。实际runner版本和Actions SHA在实施时核验。

## 目标与限制

满足IMPROVEMENT-005五项要求。正式部署默认路径、锁定内容、沙箱及业务限额不改；新冷获取仅CI显式启用。

## 接口与状态

packages.py提供校验、获取及证据输出入口；prepare.py通过显式--packages接收包目录。状态为cache-hit/下载中/已验证/失败；只有已验证状态可上传及消费，部分下载不得变成缓存命中。

## 备选方案

仅缓存、持续加超时及换源的比较见DECISION-033；当前不引入预构建rootfs分发。

## 风险与重审条件

受限重取仍可能全部失败；取消不能回收、内容校验失效或必须改来源时停止实施并升级方案。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：完整方案与任务边界已整理，供人工意图审核，未实施
- 2026-09-11：结构与内容校验通过，由工具置为 checked。
