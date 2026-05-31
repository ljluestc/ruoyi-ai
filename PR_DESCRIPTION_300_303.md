close #300
close #303

# PR 描述：实现业务系统数据打通（MCP/内置工具）并完善 Thinking 流式回传
## 一、问题背景
本 PR 解决两个直接需求：
1. **#300 与业务系统打通数据**：希望聊天可基于业务数据回答问题（例如“我今天有多少个任务”）。
2. **#303 Thinking 前端展示**：希望模型思考过程可以流式下发到前端显示。

## 二、目标
- 为聊天链路增加可开关的“工具增强模式”，让模型可以调用 MCP 工具和内置工具查询业务数据；
- 将模型推理片段（thinking）转为 SSE `reasoning` 事件；
- 保持默认行为兼容，未开启功能时不影响现有对话流程；
- 增加基础单测，覆盖新增开关与流式通道关键行为。

## 三、实现方案
### 1) 请求层扩展：新增工具模式开关
文件：
- `ruoyi-common/ruoyi-common-chat/src/main/java/org/ruoyi/common/chat/domain/dto/request/ChatRequest.java`

改动：
- 新增 `enableMcpTools` 字段，默认 `false`。

效果：
- 调用方可按会话/请求显式开启工具增强，不会改变默认聊天路径。

### 2) 聊天编排增强：支持 MCP/内置工具业务数据问答
文件：
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java`

核心改动：
- 在 `handleSpecialChatModes(...)` 中新增 `enableMcpTools` 分支，进入 `handleMcpToolMode(...)`；
- 新增 `McpBusinessDataAssistant`，通过系统提示词约束“业务数据问题优先走工具”；
- 使用 `StreamingOutputWrapper + OutputChannel` 实现“工具调用 + 流式输出 + SSE 分发”；
- 在 drain 逻辑中区分：
  - 普通 token -> `SseMessageUtils.sendContent(...)`
  - 推理 token -> `SseMessageUtils.sendReasoning(...)`

本次补充的健壮性处理：
- 若当前未启用任何 MCP/内置工具，立即返回可读错误并结束 SSE，避免空工具链路下的无意义调用；
- `AiServices` 构建改为按可用能力条件注入（`chatMemory` / `toolProvider` / `tools`），避免空对象导致构建异常。

### 3) Thinking 流式输出增强
文件：
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/observability/StreamingOutputWrapper.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java`

改动：
- `StreamingOutputWrapper` 对 `onPartialThinking` 输出统一前缀 `__REASONING__::`；
- `ChatServiceFacade` 在标准 handler 与组合 handler 中补充 `onPartialThinking` 处理并转发 SSE `reasoning`。

效果：
- 前端可独立渲染思考流；
- 外部 handler 场景下仍能同步拿到 thinking 片段。

## 四、测试改动
新增单元测试：
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/common/chat/domain/dto/request/ChatRequestMcpToolsTest.java`
  - 验证 `enableMcpTools` 默认值与开关行为；
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/observability/OutputChannelTest.java`
  - 验证 `OutputChannel` 的顺序消费、完成态与错误传播行为。

## 五、验证结果
已执行：
- `git diff --check` ✅（无空白错误/无冲突标记）

受环境限制未执行：
- Maven 编译与单测命令（当前环境缺少 `mvn`）
  - `mvn: command not found`

建议在 CI 或本地具备 Maven 后执行：
- `mvn -pl ruoyi-modules/ruoyi-chat -am test`
- `mvn -pl ruoyi-common/ruoyi-common-chat,ruoyi-modules/ruoyi-chat -am -DskipTests compile`

## 六、兼容性说明
- 默认 `enableMcpTools=false`，原普通聊天路径不变；
- `reasoning` 事件为增量能力，不影响已有 `content/done/error` 消费方；
- 工具不可用时有明确错误提示和收尾，不会悬挂连接。

## 七、风险与回滚
风险：
- 工具模式依赖外部工具可用性和权限配置；
- 前端如未消费 `reasoning` 事件，只会缺少思考展示，不影响主回答流。

回滚：
- 回退本 PR 代码即可恢复原行为；
- 或运行时保持 `enableMcpTools=false` 作为快速兜底。

## 八、涉及文件
- `ruoyi-common/ruoyi-common-chat/src/main/java/org/ruoyi/common/chat/domain/dto/request/ChatRequest.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java`
- `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/observability/StreamingOutputWrapper.java`
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/common/chat/domain/dto/request/ChatRequestMcpToolsTest.java`
- `ruoyi-modules/ruoyi-chat/src/test/java/org/ruoyi/observability/OutputChannelTest.java`
