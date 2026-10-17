# 后端包结构

`com.simpissuehub` 按职责划分为以下边界：

- `api`：REST 控制器、请求校验和响应 DTO；不直接承载跨接口业务规则。
- `application`：用例服务、授权、审计、幂等和邮件事件编排。
- `domain`：用户、工单、评论、标签、互链及令牌实体和领域状态。
- `repository`：Spring Data 持久化端口；查询接口只表达数据访问需求。
- `infrastructure`：数据库、邮件、缓存、外部系统和运行时适配器的实现边界。
- `config`：Spring、Sa-Token、HTTP、邮件和可观测性配置。
- `cli`：同进程 stdin TUI 的解析、命令执行和输出格式。

依赖方向约定：`api`/`cli` 调用 `application`，`application` 依赖 `domain` 与端口；`infrastructure` 实现端口并由 Spring 配置装配。控制器和 TUI 不应直接访问外部客户端或拼接持久化 SQL。
