# Java 编码规范（`apps/server`）

> 语言层规范。Spring / MyBatis / HTTP 协议等框架约定见
> [`frameworks/spring.md`](../frameworks/spring.md)，本项目跨语言约定见
> [`project-conventions.md`](../project-conventions.md)，通用编码指令见 [`general.md`](../general.md)。

Java 21 LTS。下面只写本仓库反复出现、且容易写歪的部分。

## 1. 数据建模

- **DTO 一律用 `record`。** 仓库里已有 137 个，不要再写手工 getter 的可变类。
- **同一边界的 record 收进一个 `final class XxxDtos`，构造函数私有。** 例如
  `ProblemDtos`、`AdminProblemDtos`。这样一个 endpoint 家族的形状能一眼看完，也不会在包里散落
  几十个单文件类型。
- **不使用 `Optional` 作为字段或参数类型。** 全仓当前零使用，保持这个状态：`Optional` 只适合
  做返回值上的局部表达，塞进 record 字段会让序列化、`equals` 和 JSON 映射同时变复杂。
  「可能没有」用可空字段加显式检查表达，并在字段名或注释里说清什么时候为 null。
- **`var` 用于局部变量**（已有 544 处），但右侧必须能一眼看出类型；`var result = service.run()`
  这种读不出类型的写法要写全。

## 2. 异常与不变量

- **违反不变量用 `IllegalStateException`，参数非法用 `IllegalArgumentException`。** 这是全仓
  已经形成的默认（84 / 54 处），不要每个包再发明一套基础异常。
- **跨进程边界的失败用领域异常**：`ProblemApiException`、`JudgingApiException`、
  `ApiProblemException` 等，带上足以定位的上下文。
- **错误信息要能定位**：带上路径、字段名、对方返回的 body 片段。`unexpected status 400`
  会让人调试到怀疑人生。
- **不要 catch 了又原样吞掉。** 要么处理，要么包一层加上下文再抛，要么让它上去。

## 3. 并发与资源

- 网络客户端必须设超时，且大于对端最慢的一次操作。
- 清理动作不要用那个正在被取消的上下文 / 已关闭的资源句柄。
- 能流式就别攒全量：几十 MB 的测试数据和编译产物边读边写。

展开见 [`project-conventions.md`](../project-conventions.md) §1.6。

## 4. 命名

- 遵循 [`project-conventions.md`](../project-conventions.md) §1.1 的命名规则（主语=接收者、动词成对、限定词只在有
  对立面时才用、别让名字结巴）。
- **时间 ns、内存 bytes，单位写进字段名**：`cpuNs`、`memoryBytes`。不要在 Java 侧换算或改名。
- 跨语言 DTO 的字段名、单位、可选性全部照 `contracts/*.json` 写，并用 schema 示例做契约对齐测试。

## 5. 格式

- 缩进用 **Tab**（现有代码如此，不要混入空格缩进的新文件）。
- 其余交给 IDE 和 `apps/server/TOOLCHAIN.md` 里声明的工具，评审不讨论排版。
