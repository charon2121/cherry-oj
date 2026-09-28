// Package api 是 judge 的 HTTP 入口：POST /judge 完成一次判题，GET /version 报告版本。
//
// 解码是严格的：未知字段、尾随的第二个 JSON 值、缺少必填字段都返回 400，请求体超过
// 16 MiB 返回 413。只要请求能解开，判题结论是 AC、WA 还是 SE 一律返回 200——HTTP 只描述
// 「这次对话成不成」，结论写在 JudgeResult.Verdict 里。
package api
