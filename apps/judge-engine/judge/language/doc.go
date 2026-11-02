// Package language 是语言注册表：每种语言的源文件名、编译命令、编译产物名与运行命令。
//
// 注册表说明 judge 认得哪些语言、怎么编译运行，以及启动时用来实测的冒烟程序；节点实际声明哪些语言，
// 由发布里的包锁（开发环境由配置 node.languages）决定，并逐门冒烟通过才上线。平台支持哪些语言以
// contracts/languages.json 为准，目前只有 cpp；python、java 只供语言配置的功能测试使用。
// 命令一律写裸名称：linux 后端的执行器按 /work、/usr/bin、/bin 的顺序解析，请求里的 PATH 不参与；
// devhost 后端则在工作目录没有同名文件时交给 PATH。
package language
