close #300
close #303

# PR 描述：业务系统数据打通（MCP/内置工具）+ Thinking 流式回传
## 一、背景与目标
本 PR 聚焦两个用户问题：
1. **#300 与业务系统打通数据**：希望在聊天中直接获取业务数据（例如“我今天有多少个任务”），而不仅是通用问答。
2. **#303 Thinking 过程前端可见**：希望模型推理/思考过程可以像主流 AI 平台一样流式展示到前端。

本次改动目标：
- 在后端提供可开关的工具增强模式，将 **MCP 工具 + 内置工具** 接入聊天链路，支持业务数据查询；
- 将模型返回的 `partial thinking` 显式转换为 SSE 的 `reasoning` 事件，供前端实时展示。

## 二、变更概览
### 1) 请求参数扩展（启用业务数据工具模式）
文件：
- `ruoyi-common/ruoyi-common-chat/src/main/java/org/ruoyi/common/chat/domain/dto/request/ChatRequest.java`

新增字段：
- `enableMcpTools: Boolean = false`

作用：
- 由前端或调用方按需开启工具增强对话，避免默认行为突变。

### 2) ChatServiceFacade 增强
文件：
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java`

#### 2.1 新增 MCP/内置工具模式分支
- 在 `handleSpecialChatModes(...)` 中新增判断：
  - `enableMcpTools == true` 时进入 `handleMcpToolMode(...)`。

#### 2.2 新增 `McpBusinessDataAssistant` 接口
- 使用 `AiServices` 构建带工具能力的助手；
- 系统提示词强调“问题涉及业务数据时优先调用工具”。

#### 2.3 新增 `handleMcpToolMode(...)`
- 组合能力：
  - Provider 流式模型；
  - `ToolProviderFactory#getAllEnabledMcpToolsProvider()`（MCP 工具）；
  - `ToolProviderFactory#getAllBuiltinToolObjects()`（内置工具，如 SQL 相关工具）；
  - 会话记忆（`MessageWindowChatMemory`）。
- 通过 `StreamingOutputWrapper + OutputChannel` 将 AI 输出分流：
  - 普通内容 -> `SseMessageUtils.sendContent(...)`
  - 推理内容 -> `SseMessageUtils.sendReasoning(...)`
- 对话结束后保存助手消息、发送 `done`、关闭连接。

#### 2.4 Thinking 事件流式回传增强
- 在 `createResponseHandler(...)` 和 `createCombinedHandler(...)` 中补充：
  - `onPartialThinking(...)`
  - `onPartialThinking(..., PartialThinkingContext ...)`
- 行为：
  - 当模型返回思考片段时，发送 SSE `reasoning` 事件；
  - 外部 handler 存在时继续透传，保证跨模块兼容。

### 3) StreamingOutputWrapper 增强
文件：
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/observability/StreamingOutputWrapper.java`

改动点：
- 新增常量：`REASONING_PREFIX = "__REASONING__::"`；
- 对 `onPartialThinking` 统一输出该前缀（供上层识别并转为 reasoning 事件）；
- 在流式完成回调中显式 `channel.complete()`，保证消费端可正常收尾。

## 三、影响范围
受影响模块：
- `ruoyi-common-chat`（请求 DTO）
- `ruoyi-chat`（聊天编排与流式输出封装）

兼容性说明：
- 默认 `enableMcpTools=false`，不改变现有普通聊天默认路径；
- 仅在显式开启时启用工具增强路径；
- SSE 仍保留原 `content/done/error`，新增 `reasoning` 事件为增量能力。

## 四、关键行为说明
### 1) 业务系统数据打通路径
- 场景：用户问“我今天有多少个任务”；
- 开启 `enableMcpTools` 后，助手可调用已启用的 MCP 工具和内置工具（如 SQL 查询工具）进行数据检索再回答。

### 2) Thinking 前端展示
- 模型返回思考片段时，后端会发送 `reasoning` SSE 事件；
- 前端可单独渲染 reasoning 区域，实现“推理过程可见”体验。

## 五、验证情况
已执行：
- `git diff --check` ✅（无冲突标记/无空白错误）

未执行（环境限制）：
- `mvn ... compile` ❌
  - 原因：当前环境缺少 Maven 可执行文件（`mvn: command not found`）。

建议在 CI 或具备 Maven 的环境补充：
- `mvn -pl ruoyi-common/ruoyi-common-chat,ruoyi-modules/ruoyi-chat -am -DskipTests compile`
- 针对 `/chat/send` 的 SSE 集成联调（含 `enableMcpTools` 开关、`reasoning` 事件断言）。

## 六、风险与回滚
### 风险
- 工具增强模式依赖外部工具可用性，工具异常会导致回答降级或报错；
- 前端若未消费 `reasoning` 事件，不影响主回答，但无法展示思考过程。

### 回滚策略
- 代码级回滚：回退本 PR 提交即可恢复到原行为；
- 运行级兜底：不开启 `enableMcpTools` 时仍使用原有聊天流程。

## 七、涉及文件清单
- `ruoyi-common/ruoyi-common-chat/src/main/java/org/ruoyi/common/chat/domain/dto/request/ChatRequest.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/observability/StreamingOutputWrapper.java`
