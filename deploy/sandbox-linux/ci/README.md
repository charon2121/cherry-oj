# 沙箱回归脚本

本目录保留此前完整沙箱 CI 的回归脚本与报告校验器，方便按需排查和查证历史结果。
当前日常 CI 只检查契约与文档、Go、Web、Java 和 C 编译，见
[提交流程](../../../docs/git-workflow.md)。完整部署、真实内核、全栈浏览器和冷下载工作流已删除，
这些脚本不会随提交自动运行，核心 CI 通过也不代表这些场景已验证。

## 本地轻量检查

普通开发机可运行安装器、rootfs、回归工具的单测，以及 Python AST 和 shell 语法检查。
输出目录必须不存在：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /tmp/cherry-ci-basic
```

## 保留的完整回归

- `prepare.py` 构建当前代码、执行器探针和锁定 rootfs；`packages.py` 获取并校验固定 Ubuntu 快照的软件包。
- `kernel.py` 检查真实 Linux 隔离、资源计量与执行组回收。
- `native.py` 检查原生安装、权限删减、服务故障和卸载恢复。
- `business_prepare.py` 与 `business.py` 准备并验证 Java、Web、Kafka 与 Linux 判题节点的完整业务链路。
- `cases.json`、`report.py` 和 `summary.py` 保存原回归清单与报告规则；旧汇总仍按原工作流的任务集合校验，不能用来汇总当前核心 CI。

完整回归入口仍要求一次性、独占的 GitHub-hosted Linux/amd64 VM、运行身份和 root 权限，
需要另行准备调用环境。不要在日常开发机或已有服务的服务器上运行；Mac 不运行资源耗尽夹具。
本次精简没有改变测试断言、资源预算或清理逻辑。旧设计与验收证据保留在
[WORK-050](../../../development/works/WORK-050/00-work.md) 及关联历史记录中。
