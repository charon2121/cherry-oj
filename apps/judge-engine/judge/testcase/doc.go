// Package testcase 提供判题用的测试点（testcase：一对 .in/.out；一整套测试点叫 testdata）。
// submit 模式按测试数据协议（docs/testdata-protocol.md）读取题目的测试数据，trial 模式把请求里内联的
// 测试点包成同样的形状。包名不叫 testdata，是因为 Go 工具链会忽略名为 testdata 的目录。
//
// Load 读 <地址>/testdata.json，把数据复制到 judge 自己的本地目录并逐个核对大小与 SHA-256，
// 复制完成后才返回，之后写入方再改动这个地址不影响本次判题。测试点只记录「怎么打开」而不读内容，
// 几十 MB 的数据乘以并发数不会占满内存。任何一步对不上都整体报错而不是只判一部分：
// 少判一个点会让错解拿到 AC。
package testcase
