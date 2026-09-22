> 本文是[《智能体协作开发文档系统规范》](../SPECIFICATION.md)的一章。

# 4. 流程与验证强度

## 4.1 流程是控制面，文档是产物面

compact 的默认流程为确认目标与边界 → 实施 → 复核 → 验证与验收。
主文档支撑确认和实施，VERIFY 保存证据，复核记录实际行为，不为阶段生成空文档。
已有 layered 工作保留原类型流程与产物关系。

## 4.2 阶段必需性与阶段进度

阶段保留 requirement、status、status_source、artifacts、checks、source、reason。
阶段进度使用 pending / ready / doing / done / blocked，旧格式可选阶段允许 skipped。
精简工作四个阶段都必需。状态来自闸、工作进度、任务与证据，流程表由工具生成。

检查结论使用 pending / pass / fail / not-applicable / unrecorded；最后一种仅兼容历史记录。
精简流程中 fail 阻塞阶段，pending 或 unrecorded 不能通过验收。not-applicable 必须说明理由，
不能为了推进状态把必需验证说成不适用。

## 4.3 类型与拆分

类型影响主文档应回答的问题，见第 2 章；不再为五种类型预先填满不同文档包。
任务依赖、独立委派或分批交付才触发 TASK/PLAN，重要取舍才触发 DECISION，复杂方案才触发 DESIGN。
用户流程较长时拆 EXPERIENCE，未来有用的新结论才拆 MEMORY。

## 4.8 快速流程

普通工作使用最小主文档与证据，不建立额外“快速审批”机制。两道闸保留，工程边界也保留。
减少的是重复交接材料，不是对目标、风险和证据的判断。

## 4.9 强制完整流程

不再以高风险强制生成完整文档包。high/critical 加独立复核和回退检查，system 加跨模块回归，
非 low 或非 local 加影响分析，concerns 加专项验证。方案和证据的深度与真实风险匹配。
没有生产环境，因此不引入上线、线上观察阶段；交付与回退方式写入执行方案或确有必要的 PLAN。

[返回总览](../SPECIFICATION.md)
