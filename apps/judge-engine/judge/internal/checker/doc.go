// Package checker 比对选手输出和标准答案。
//
// 算法参考 HustOJ 的 compare_zoj（ZOJ 移植版）：**单遍、逐字节、流式**，
// 两个流并排推进，内存恒定，不限文件大小。
//
// HustOJ 里另有一版早期实现，把两份文件的 token 全部拼成一个大字符串再
// strcmp。那版有三个我们不想继承的问题：
//   - 有文件大小上限，且不论实际多大都先分配一整块
//   - fscanf("%s") 不限宽度，超长 token 直接缓冲区溢出
//   - 标准答案打不开时 return OJ_AC —— 少传一个 .out，全场 AC
//
// # 三种结论
//
//	token 序列不同         → WA
//	token 相同、空白不同    → 严格判 PE，宽松判 AC（Options.StrictWhitespace）
//	完全一致               → AC
//
// **空白差异永远不会变成 WA。** 答案的内容是对的，只是排版不同；判成 WA
// 会让选手去查一个根本不存在的算法错误。严不严格只影响「PE 还是 AC」这一档，
// 不影响「是不是 WA」。
package checker
