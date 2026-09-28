// Package sandboxclient 是 judge 调用 sandbox 的 HTTP 客户端：上传 blob、执行命令、删除 blob，
// 以及节点探测用的 /version 与探测执行。
//
// 只有非 2xx 与网络错误才返回 error；Status 不是 OK 的 200 仍是一次成功的调用，原样交给 flow，
// 这样「选手程序超时」和「沙箱自己出错」不会混为一谈。客户端整体超时必须覆盖 sandbox 最慢的
// 一次操作，零值会被替换成安全的默认值。
package sandboxclient
