// Package runner 编排单次执行：归一化限额、打开输入、调用后端、把执行事实归类成 Status，
// 结论为 OK 时才把产物存进 store 并发布 ref。不持有特权操作。
//
// 文件：runner（Run 的主流程）、request（限额默认值与请求校验）、sources（输入与总量预算）、
// result（事实到 Status 的归类）、collector（产物收集与发布）、capwriter（有界输出）。
package runner
