// Package language 是语言注册表：每种语言的源文件名、编译命令、编译产物名与运行命令。
//
// 注册表里有 cpp、python、java，但整条业务链路目前只支持 cpp：judging-service 把 languageId
// 限定为 cpp，节点注册也只声明 cpp（理由见 node/identity 的 declaredLanguages），生产的 linux
// 后端 rootfs 也只装了 C++ 工具链。python、java 供语言配置的功能测试使用。
// 命令一律写裸名称：linux 后端的执行器按 /work、/usr/bin、/bin 的顺序解析，请求里的 PATH 不参与；
// devhost 后端则在工作目录没有同名文件时交给 PATH。
package language
