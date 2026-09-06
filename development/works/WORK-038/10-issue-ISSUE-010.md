---
id: "ISSUE-010"
type: "issue"
title: "兼容常见测试数据 ZIP 并返回可操作校验错误"
status: "approved"
work: "WORK-038"
owners: ["codex/root"]
depends_on: ["FEATURE-007"]
related: ["WORK-025", "WORK-037"]
implements: []
verifies: []
tags: []
created_at: "2026-09-05"
updated_at: "2026-09-05"
---

# ISSUE-010：兼容常见测试数据 ZIP 并返回可操作校验错误

<!--
本节面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。报错原文、依赖坐标、字段、类、框架、协议、表名、路径和
命令从下一节开始再出现。
-->

## 为什么做

管理员把本地测试用例文件夹压缩后上传，系统应当能识别其中成对的输入和答案文件。macOS 的 Finder
会自然地保留外层文件夹，并自动加入少量系统说明文件；这些内容不改变测试用例本身，不应让一份本来
可用的数据包变成无法理解的 422。若压缩包确实有问题，页面也应告诉管理员该怎样修改，而不是只说
“请检查请求内容”。

## 问题现象

在题目工作台上传 `/Users/charon/Downloads/testin.zip` 后，接口返回 422
`INVALID_TEST_DATA_ARCHIVE`，详情为“请检查请求内容”。压缩包中的有效文件是
`testin/test1.in` 与 `testin/test1.out`。

## 复现方式

1. 在 macOS 中建立 `testin` 文件夹，放入 UTF-8 的 `test1.in`、`test1.out`。
2. 使用 Finder 的“压缩”操作生成 ZIP；包内包含 `testin/`、`.DS_Store` 和 `__MACOSX/...`。
3. 在管理工作台“测试与标定”步骤上传该 ZIP。
4. 观察公开响应及 `test_data_version.error_message`。

## 实际结果

公开响应只有泛化的 422；数据库记录的实际失败码为 `TEST_DATA_INVALID_ZIP_ENTRY`。上传流和身份验证均
已成功，失败发生在 problem-service 的 ZIP 条目校验：外层目录与 Finder 元数据被当成非法测例文件。

## 预期结果

符合 [FEATURE-007](../WORK-025/10-feature-FEATURE-007.md) 测例约束的 `.in/.out` 文件，无论直接放在 ZIP
根目录，还是统一放在一个外层文件夹中，都能生成相同的逻辑 manifest 并成为 READY。常见 macOS
元数据不进入 manifest 或判题目录。真正无效的包继续失败，并返回管理员可以照着处理的中文原因。

## 影响与条件

影响所有通过常见桌面压缩工具把“文件夹”而非“文件列表”打成 ZIP 的管理员。当前已确认 macOS Finder
稳定复现；平面 ZIP 不受影响。变化涉及公开错误详情、problem-service 入库前校验和 judging-service
部署时的二次校验，不改变已有 READY 数据、数据库结构、上传大小限制或测试数据保密边界。

## 原因

`FileTestDataAssetStore` 只接受 ZIP 根目录中的 `.in/.out` 普通文件，遇到任何目录或其他文件立即抛出
`TEST_DATA_INVALID_ZIP_ENTRY`。Finder 生成的外层目录、`.DS_Store`、`__MACOSX` 因而被误判。随后
`ProblemServiceClient` 只保留上游 status/code，`ProblemApiErrors` 按 HTTP 状态重造固定文案，又丢失了
problem-service 已有的具体 detail。

## 修复方向

为 ZIP 校验增加受限的逻辑路径归一化：继续接受平面包；额外接受所有测例统一位于一个安全外层目录的
包；识别并跳过 `.DS_Store`、`__MACOSX` 与 AppleDouble `._*` 元数据。忽略项仍计入条目数量且永不
解压。problem-service 与 judging-service 使用相同规则和对齐测试；Gateway 仅对允许透传的业务错误
保留经过长度与控制字符检查的上游 detail。

## 回归检查

- AC-001：上传实际 `testin.zip` 形态后得到 READY；manifest 只含逻辑名 `test1.in/test1.out`，下载仍与
  原始 ZIP 字节一致。
- AC-002：平面 ZIP 与单一外层目录 ZIP 产生相同的 caseCount、totalBytes 和逻辑文件 manifest；部署后
  判题目录只出现根级 `.in/.out` 文件。
- AC-003：`.DS_Store`、`__MACOSX/**`、同目录 AppleDouble `._*` 不进入 manifest、不解压，但全部计入
  最大条目数；元数据正文不参与测例解压大小。
- AC-004：多外层目录、混合根级与目录级测例、多层测例目录、路径穿越、软链接、重复逻辑名、孤儿
  `.in/.out`、非 UTF-8 和现有限额仍被拒绝，且无 READY 或临时资产残留。
- AC-005：ZIP 结构、缺少配对、文本编码等可修复错误经过 Gateway 后保留稳定 code、可操作中文 detail
  和同一 requestId；不返回文件正文、标准答案、存储路径或异常堆栈。
- AC-006：problem-service、judging-service 与 Gateway 定向测试通过，后端聚合回归通过；公开响应结构、
  数据库和已有平面 ZIP 行为保持兼容。

## 变更记录

- 2026-09-05：状态变更：draft → review。原因：真实 Finder ZIP 与数据库失败码已定位根因，复现、边界和验收标准已写清，提交意图审核
- 2026-09-05：意图闸通过：review → approved。原因：确认兼容单外层目录和 macOS 元数据，并改进 ZIP 校验错误提示
