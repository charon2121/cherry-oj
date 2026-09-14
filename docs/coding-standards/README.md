# 编码规范

根目录 [`AGENTS.md`](../../AGENTS.md) 只保留每次会话都必须遵守的部分：项目边界、仓库结构、
跨语言铁律、提交约定和协作协议。展开的规范放在这里，动手之前按需读。
各模块当前是可用实现还是骨架，见 [`status.md`](../status.md)。

编码规范分四层，**越具体越优先**：写某个框架的代码时四层都适用，冲突时下层压上层。

```text
general.md              通用编码指令：简单优先、不过度设计、错误与安全、测试、范围控制、交付前自检
project-conventions.md  本项目踩出来的跨语言约定：命名、单位与契约、零值陷阱、错误边界、资源、依赖方向、待办锚点、测试
languages/              某种语言的写法：类型、异常、格式、该语言特有的坑
frameworks/             某个框架的写法：状态归属、接口协议、数据访问、该框架特有的约束
```

这个顺序不是我们自己定的——`general.md` 的 Priority Rules 把自己排在第 4 位，明确低于「当前项目
已有约定」和「语言和框架专属规范」。**通用指令说「遵循现有项目约定」，具体约定就在下面三层。**

| 你要做什么 | 先读 |
|---|---|
| 写任何代码前 | [`general.md`](./general.md) |
| 本项目的跨语言约定 | [`project-conventions.md`](./project-conventions.md) |
| 写 Go（judge / sandbox） | [`languages/go.md`](./languages/go.md) |
| 写 Java | [`languages/java.md`](./languages/java.md) + [`frameworks/spring.md`](./frameworks/spring.md) + [`TOOLCHAIN.md`](../../apps/server/TOOLCHAIN.md) |
| 写 TypeScript | [`languages/typescript.md`](./languages/typescript.md) + [`TOOLCHAIN.md`](../../apps/web/TOOLCHAIN.md) |
| 写 React / Web UI | 上一行，外加 [`frameworks/react.md`](./frameworks/react.md) + [`design-system.md`](../design-system.md) |
| 写 Python（`scripts/`、`deploy/`） | [`languages/python.md`](./languages/python.md) |
| 提交、hooks、CI | [`git-workflow.md`](../git-workflow.md) |
| 开发流程、工作项、闸 | [`development/README.md`](../../development/README.md) |

尚未使用的技术栈（Rust、C#、FastAPI、Django、Next.js 等）**不预先建空文档**：真正引入时再在
对应目录下新建，那时才知道本项目会踩哪些坑。空壳规范没人读，还会让路由表指向没有内容的文件。
