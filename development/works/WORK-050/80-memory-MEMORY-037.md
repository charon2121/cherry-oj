---
id: "MEMORY-037"
type: "memory"
title: "将沙箱已验收回归固化为重构 CI"
status: "checked"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["VERIFY-051"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-10"
updated_at: "2026-09-13"
---

# MEMORY-037：回归自动化的边界

## 背景

WORK-048已验收并推送，用户要求先固化这些测试再重构judge-engine。

## 决定与原因

用户已签署意图闸并允许实施：使用一次性GitHub Ubuntu VM分层运行，现有服务器和IDEA保持独立。基础、内核、原生、真实业务及必需汇总已全部实施，冻结基线见MEMORY-037冻结基线；人工验收留用户。

## 尝试与教训

普通Go测试可跳过内核用例，trusted-host容器冒烟和模拟浏览器响应不能证明真实隔离业务链。旧手工脚本包含固定端口、UID与业务ID，不能直接批量执行便宣称自动化完成。

## 已知问题

完整基线8fe6e41已在同SHA冷/热两轮通过，93项及12job全部成功；所有权清理完整。取消实验覆盖Java及依赖启动阶段并正确失败，不能扩大成所有取消时点。机器重启和其他平台仍留置；共享内核不能提供虚拟机安全保证。重构候选必须使用相同断言重新运行，不能引用旧基线代替验证。

CI夹具也会出错：新题发布不自动公开；Monaco多行insertText会改变缩进，现用paste并验证请求原文；cgroup目录含控制文件，观察器仅枚举子目录；/proc观测须丢弃不完整快照且保留安全断言。报告绑定源码/harness/run/attempt，重跑不覆盖旧产物；取消后的资源清空不意味着未完成用例通过。

TASK-115三台VM保留了首次Java超时2/3和随后原配置全部通过的差异；新日志确认wall/SIGKILL，即使Wait error为nil。候选工具JVM参数使后续样本中位数下降约20%，但没有首次稳定证据，且正式CI也复现C++超时。系统I/O压力支持首次加载等待的解释，不能当成Java专属计量。建议将功能集成测试的编译等待上限显式配置，与严格资源回归分开；用户随后条件授权多次验证成立后适当放松：追加6台交替预读对照支持文件加载贡献，再采用测试专用15秒；3台未预读新VM全部通过，其中Java首次10.56秒、随后约0.47秒。保留快速首次反例及历史失败，不宣称所有延迟来自同一底层原因；未采用30秒，不更改生产命令和限额，不用预热/重跑替换首次失败。

## 重新考虑条件

托管平台缺能力、业务资源不足、执行策略变化或目标平台增加时重审方案。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：已盘点既有验收与CI缺口，补齐分层方案、边界及验收条件供人工审核；尚未实施
- 2026-09-13：结构与内容校验通过，由工具置为 checked。

## 冻结基线

以下为两轮已复验的固定字段摘要；它保存证据身份，不代替未来候选运行。

```json
{
  "sourceSha": "8fe6e413a4cc0291204c298705403462ac4ed0d4",
  "harnessSha": "d17e8e55a889d410839c6dab9a78fe610a7ce0405d98a28283794f52243d563f",
  "casesSha256": "5be0031c6735e7d8cb580aae4fd4d0d8b6b6d55514484fc070ae58e4ca29f83d",
  "requiredCheck": "sandbox CI（必需回归汇总）",
  "runs": [
    {
      "runId": "34703661410",
      "runAttempt": "1",
      "url": "https://github.com/charon2121/cherry-oj/actions/runs/34703661410",
      "packageCache": "cold",
      "jobs": 12,
      "summary": {
        "schemaVersion": 1,
        "sourceSha": "8fe6e413a4cc0291204c298705403462ac4ed0d4",
        "runId": "34703661410",
        "runAttempt": "1",
        "status": "PASS",
        "jobs": {
          "sandbox-packages": "success",
          "sandbox-basic": "success",
          "sandbox-kernel": "success",
          "sandbox-native": "success",
          "sandbox-business": "success",
          "development": "success",
          "web": "success",
          "contracts": "success",
          "go": "success",
          "tidy": "success",
          "containers": "success"
        },
        "suites": {
          "basic": {
            "status": "PASS",
            "required": 5,
            "passed": 5
          },
          "kernel": {
            "status": "PASS",
            "required": 63,
            "passed": 63
          },
          "native": {
            "status": "PASS",
            "required": 10,
            "passed": 10
          },
          "business": {
            "status": "PASS",
            "required": 15,
            "passed": 15
          }
        }
      },
      "environment": {
        "system": "Linux",
        "kernel": "6.17.0-1022-azure",
        "architecture": "x86_64",
        "runnerImage": "20260907.300.1",
        "lsm": "lockdown,capability,landlock,yama,apparmor,ima,evm"
      },
      "toolVersions": {
        "java": "openjdk 21.0.12.1 2026-08-18 LTS\nOpenJDK Runtime Environment Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS)\nOpenJDK 64-Bit Server VM Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS, mixed mode, sharing)",
        "node": "v24.20.0",
        "npm": "11.19.0"
      },
      "goVersionFromModule": "1.26.3",
      "build": {
        "sourceSha": "8fe6e413a4cc0291204c298705403462ac4ed0d4",
        "harnessSha": "d17e8e55a889d410839c6dab9a78fe610a7ce0405d98a28283794f52243d563f",
        "architecture": "x86_64",
        "packageLock": "4dbcf4dd7025146ff44782024354b1868eacaaa313d53a57ded5fab247fb129c",
        "rootfsManifest": "ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0",
        "binaries": {
          "sandbox-helper": "c475a0f9aa94ef27d65bc6814bdec8ba35507ac81ebd894226934f78d3c4f54f",
          "sandbox": "34d3b52012adb1daf3d4f4eb682d3390f5ed0740db98b1bbca743370665ae992",
          "judge": "8fd9a7d18705fd34138181dda320a5eed351b7e8e7eb814966f1f195c0f9464e"
        },
        "probe": "c3eaf953c2b432bee8cf3af53569b90f58f7beff5e91eabe57b797d287c74e21",
        "boundary": "f559edc5bdf716f9dad865fbd3e708808e32840ef6fff18458e517b7db60c50b"
      },
      "reportDigests": {
        "basic": "64709eda08aee27d4b474f7dfdc72ee0c129e849f401c04c141b8bf6925d5940",
        "kernel": "565e03baa39f384278e3ca74ea39f54a26cd933937203fb750c94e51aaad2f73",
        "native": "77517a5a7404883fab0599e560c798b5cffdafc9b8fea1a172891a63e8606d2c",
        "business": "40a4a006e19f535c0aae1eea0c6747441f140bed0af3ca002f42d1b1e4e0b4fb"
      },
      "cleanup": {
        "basic": "PASS",
        "kernel": "PASS",
        "native": "PASS",
        "business": "PASS"
      }
    },
    {
      "runId": "34703991365",
      "runAttempt": "1",
      "url": "https://github.com/charon2121/cherry-oj/actions/runs/34703991365",
      "packageCache": "warm",
      "jobs": 12,
      "summary": {
        "schemaVersion": 1,
        "sourceSha": "8fe6e413a4cc0291204c298705403462ac4ed0d4",
        "runId": "34703991365",
        "runAttempt": "1",
        "status": "PASS",
        "jobs": {
          "sandbox-packages": "success",
          "sandbox-basic": "success",
          "sandbox-kernel": "success",
          "sandbox-native": "success",
          "sandbox-business": "success",
          "development": "success",
          "web": "success",
          "contracts": "success",
          "go": "success",
          "tidy": "success",
          "containers": "success"
        },
        "suites": {
          "basic": {
            "status": "PASS",
            "required": 5,
            "passed": 5
          },
          "kernel": {
            "status": "PASS",
            "required": 63,
            "passed": 63
          },
          "native": {
            "status": "PASS",
            "required": 10,
            "passed": 10
          },
          "business": {
            "status": "PASS",
            "required": 15,
            "passed": 15
          }
        }
      },
      "environment": {
        "system": "Linux",
        "kernel": "6.17.0-1022-azure",
        "architecture": "x86_64",
        "runnerImage": "20260907.300.1",
        "lsm": "lockdown,capability,landlock,yama,apparmor,ima,evm"
      },
      "toolVersions": {
        "java": "openjdk 21.0.12.1 2026-08-18 LTS\nOpenJDK Runtime Environment Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS)\nOpenJDK 64-Bit Server VM Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS, mixed mode, sharing)",
        "node": "v24.20.0",
        "npm": "11.19.0"
      },
      "goVersionFromModule": "1.26.3",
      "build": {
        "sourceSha": "8fe6e413a4cc0291204c298705403462ac4ed0d4",
        "harnessSha": "d17e8e55a889d410839c6dab9a78fe610a7ce0405d98a28283794f52243d563f",
        "architecture": "x86_64",
        "packageLock": "4dbcf4dd7025146ff44782024354b1868eacaaa313d53a57ded5fab247fb129c",
        "rootfsManifest": "ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0",
        "binaries": {
          "sandbox-helper": "c475a0f9aa94ef27d65bc6814bdec8ba35507ac81ebd894226934f78d3c4f54f",
          "sandbox": "34d3b52012adb1daf3d4f4eb682d3390f5ed0740db98b1bbca743370665ae992",
          "judge": "8fd9a7d18705fd34138181dda320a5eed351b7e8e7eb814966f1f195c0f9464e"
        },
        "probe": "c3eaf953c2b432bee8cf3af53569b90f58f7beff5e91eabe57b797d287c74e21",
        "boundary": "f559edc5bdf716f9dad865fbd3e708808e32840ef6fff18458e517b7db60c50b"
      },
      "reportDigests": {
        "basic": "7801ec0774a1759349e41ccee2cb738e68ce6f5f0f907438725c8d0e7a4b144b",
        "kernel": "4b43a6b5a7bdc7be70b5a7fe6367590542781795027a25dc0a6acbcf783b7f67",
        "native": "82900ec4260d4013177352e16662491976db78b013ff5c28275c644b809bc6b9",
        "business": "e268e8eeacf516c656fe2679a78a6e92201486601e4a04b08973969f1849c6ac"
      },
      "cleanup": {
        "basic": "PASS",
        "kernel": "PASS",
        "native": "PASS",
        "business": "PASS"
      }
    }
  ],
  "cancellation": {
    "runId": "34703154825",
    "runAttempt": "1",
    "url": "https://github.com/charon2121/cherry-oj/actions/runs/34703154825",
    "result": "cancelled",
    "summary": "FAIL",
    "scope": "Started Java services and MySQL/Redis/Kafka; before native installation",
    "ownedResourcesAfter": "empty"
  }
}
```
