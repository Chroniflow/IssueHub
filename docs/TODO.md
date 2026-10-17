# SimpIssueHub TODO

基于 [MAIN.md](MAIN.md) 的实现清单。技术栈确定为 **Vue 3 + TypeScript + Vite**（前端）和 **Spring Boot 3 + Java 21 + Sa-Token v1.46.0**（后端认证授权），数据库使用 MySQL，构建工具分别使用 pnpm 与 Maven。系统面向 Minecraft 服务器玩家，用于玩家反馈、功能建议和 Bug 处理；每个阶段完成后再进入下一阶段。

## 0. 项目骨架与约定

- [x] 建立 `Backend` Spring Boot 多包结构：`api`、`application`、`domain`、`infrastructure`、`cli`；包职责和依赖方向记录于 `Backend/ARCHITECTURE.md`。
- [x] 建立 `Frontend` Vue 3 + TypeScript + Vite 工程，补齐路由、状态管理、HTTP 客户端、ESLint 代码检查、可自动修复检查和 `.editorconfig` 格式约定。
- [x] 增加根目录与前端 `.env.example`、后端 `application-local.yml` 示例，并通过 `.gitignore` 禁止提交真实密码、邮件凭据和密钥。
- [x] 统一 API 响应格式、错误码、分页参数、时区（UTC 存储，客户端本地展示）和日期格式；列表与评论分页使用稳定 `PageResponse` DTO。
- [x] 配置 OpenAPI/Swagger、数据库迁移（Flyway）和本地 Docker Compose（Spring Boot 应用、MySQL、Mailpit）；Compose 通过健康检查等待数据库后启动应用。
- [x] 引入 `cn.dev33:sa-token-spring-boot3-starter:1.46.0`，并在 `docs/DEPENDENCIES.md` 记录升级策略和 Spring Boot 3 / Java 21 兼容性。
- [x] 写 README：启动、测试、配置、数据库迁移、TUI 接入和生产部署方式。

## 1. 领域模型与数据库

- [x] 设计并迁移 `users`：账号、邮箱、BCrypt 密码哈希、权限等级、启用状态、创建/更新时间；注册接口和 TUI 用户管理均使用该模型。
- [x] 设计并迁移 `issues`：标题、正文、作者、状态（`OPEN`/`CLOSED`）、关闭人/时间、创建/更新时间、版本号及软删除/幂等字段；实体和 REST 状态流转已落地。
- [x] 设计并迁移 `tags`、`issue_tags`：标签名使用 `utf8mb4_unicode_ci` 实现大小写不敏感唯一约束，工单/标签关联使用复合主键并设置级联约束。
- [x] 设计并迁移 `issue_comments`：作者、正文、编辑/删除标记、创建/更新时间；实体、分页接口及作者/管理员编辑与软删除规则已落地。
- [x] 设计并迁移 `issue_links`：源工单、目标工单、关系类型使用复合主键，数据库 CHECK 禁止自链接，实体和接口支持双向查询并阻止重复链接。
- [x] 设计并迁移 `audit_logs`：记录操作者、动作、对象、变更摘要、请求标识和时间，建立查询索引，并在审计摘要写入前对密码、Token、密钥和 Authorization 值脱敏。
- [x] 为作者、状态、更新时间、标签关联、评论和互链等常用查询建立复合索引；迁移 `V5__query_indexes.sql` 覆盖基础查询，`V8__comment_paging_index.sql` 追加评论分页的稳定排序索引，另覆盖软删除列表、反向标签关联、互链目标和 Token 列表。
- [x] 为工单创建、编辑、标签、互链、评论、删除和状态变更定义 `@Transactional` 事务边界；全局标签创建/删除与审计保持原子性，删除时锁定标签并检查使用状态；工单使用 JPA `@Version` 并通过 `saveAndFlush` 在请求内触发乐观锁冲突，统一返回 `409 VERSION_CONFLICT`。

## 2. 认证、权限与安全

- [x] 实现匿名、`user`、`staff`、`admin` 四级权限，匿名不可分配；默认新账号为 `user`，并通过统一 `AuthorizationService` 集中执行管理员/工作人员/工单作者策略。
- [x] 实现密码重置流程：新增短期一次性哈希令牌、邮件发送、匿名请求防枚举；前端确认页校验两次新密码及长度；确认时锁定令牌，在同一事务中更新密码、消费该用户全部未使用重置链接并写入审计，提交后踢出旧会话；提供前端找回/重置页面。
- [x] 实现注册/登录、登出、当前用户，以及登录用户修改密码和邮箱流程；注册、修改密码和修改邮箱均在事务中保存并写入审计，提交后才建立或失效 Sa-Token 登录态；密码修改后前端清理身份并引导使用新密码重新登录；前端注册页完成表单校验并与后端联动登录。
- [x] 使用 Sa-Token 完成登录、注销、会话活跃续期、Token 校验、停用即封禁/踢人和非并发登录策略；管理员用户变更与审计在事务中提交，账号从启用变为停用后才踢出会话；管理员强制下线在审计事务提交后执行；管理员网页通过稳定分页列表管理用户并可内联确认强制指定用户下线，列表加载失败可在当前页重试，当前默认单实例本地会话，多实例部署可通过 Dao 扩展切换 Redis。
- [x] 使用 Sa-Token 路由拦截器保护 API 路由，并以兼容会话 Token 与 API Token 的 `AuthorizationService` 集中执行角色、作者和资源权限校验；拦截器仅放行明确的工单详情/评论/互链/统计及标签公开 GET/HEAD 和已通过过滤器验证的 API Token，同时保护写接口与账户接口；前端仅隐藏不可用操作，后端校验作为安全边界。
- [x] 使用 BCrypt 安全密码哈希；配置 Token 有效期、无操作超时、活跃校验、撤销/过期风险控制、CORS、安全响应头，并将 Sa-Token Cookie SameSite 默认设为 `Strict`（Secure 可通过环境变量开启）。
- [x] 注册强制验证邮箱验证码；验证码仅保存加盐哈希，具备有效期、发送冷却、尝试次数限制和过期清理。
- [x] 注册验证码发送前支持 Cloudflare Turnstile 服务端 Siteverify 校验；公私钥分离配置，未配置私钥时允许本地开发关闭。
- [x] 按 `MAIN.md` 实现：`staff+` 才能 Reopen；网页管理接口只能赋予 `USER`/`STAFF`，可管理其他管理员但不可授予 `ADMIN`，只有已认证管理员可通过 TUI 授予 `ADMIN`，并提供管理员用户启用/停用管理。
- [x] 增加登录失败限流（IP+账号组合，15 分钟最多 5 次）、敏感操作审计，并通过 SameSite Strict Cookie 与可配置 Secure 标志强化 CSRF 防护。
- [x] 增加输入校验、输出编码、可配置 CORS（来源列表会 trim 并过滤空项）、安全响应头和统一异常处理；注册用户名先规范化再做长度与重复校验，登录和找回密码的用户名/邮箱在限流或查询前统一规范化并大小写不敏感，前后端字段长度边界保持一致。
- [x] 补充权限矩阵单元与真实数据库集成测试，覆盖匿名、普通用户、工作人员、管理员，以及关闭、Reopen 和管理接口的越权请求。
- [x] 为 Sa-Token 登录态、失效/过期 Token、踢人、停用封禁、非并发登录和跨请求权限校验补充 MySQL + MockMvc 集成测试；未提供数据库环境变量时自动跳过集成类。

## 3. 工单核心闭环

- [x] 提供工单创建、详情、列表分页、关键词搜索、按状态/标签筛选和排序；工单编辑与按作者筛选已接入，全文搜索的所有筛选组合均支持所选排序。
- [x] 工单作者或 `STAFF+` 可在详情页编辑标题和正文，后端执行同等权限校验，并统一规范化输入及幂等哈希。
- [x] 实现 `OPEN`、`CLOSED` 状态流转；工单作者可以关闭自己创建的工单，`staff+` 可以 Reopen，重复状态操作返回明确错误码。
- [x] 实现评论新增、编辑（仅作者或管理员）、软删除和按时间分页加载；查询按创建时间及 ID 稳定排序，新增与编辑会规范化正文并拒绝空白评论。
- [x] 实现标签创建、绑定、解绑、列表筛选；重复绑定返回冲突、解绑未绑定标签返回不存在，标签创建会规范化并拒绝空白名称，普通用户不可修改全局标签定义（`STAFF` 可创建，`ADMIN` 可删除），标签页统一处理网络、权限与冲突错误，初始加载失败提供重试入口。
- [x] 实现公开标签列表接口 `/api/v1/tags`，前端标签页显示服务端标签及工单数量。
- [x] 工单列表支持 `tag` 查询参数，标签页跳转可按服务端标签筛选并清除筛选。
- [x] 工单列表支持服务端分页，状态与标签筛选会保留在分页请求中，并显示服务端状态总数；关闭/Reopen 后会同步刷新统计，统计加载失败会提示并支持重试。
- [x] 工单列表支持按作者精确筛选，并与全文搜索、状态、标签和分页组合；作者用户名限制为 32 字符。
- [x] 工单列表支持按最近/最早更新或创建时间排序，并以工单 ID 作为稳定次排序键。
- [x] 工单列表支持基于 MariaDB FULLTEXT 的标题/正文全文搜索，并与状态、标签和分页组合；关键词限制为 200 字符，超限返回统一 `400` 错误。
- [x] 创建工单时从服务端动态加载、校验并绑定所选标签，详情和列表响应返回真实标签；标签服务暂时不可用时保留默认类型回退，显示统一错误提示并提供重试入口，详情页标签管理也提供加载状态与重试入口。
- [x] 实现工单互链：添加、查看、删除；接口返回正向/反向关系方向，详情页以正确语义展示双向关联，加载期间显示状态，并处理目标工单不存在的情况。
- [x] 记录工单创建/编辑、评论、状态、标签、互链及标签/评论删除等审计事件；摘要不含正文、密码或令牌。
- [x] 禁止普通用户删除自己创建的工单；仅管理员可从 Web 管理操作软删除，列表、详情、链接和评论入口隐藏，审计记录保留。

## 4. 后端 REST API

- [x] 实现 `/api/v1/auth`、`/api/v1/users/me`、`/api/v1/issues`、`/api/v1/tags`、`/api/v1/admin` 端点。
- [x] 为状态、权限、分页、过滤、校验失败、冲突和资源不存在定义稳定的 HTTP 状态与 JSON 错误结构；Sa-Token 未登录异常统一映射为 `401 UNAUTHENTICATED`。
- [x] 使用控制器层 DTO `record` 及显式 `of(...)` 映射方法隔离持久化实体；用户、工单、评论、标签、互链和 API Token 响应均不暴露密码哈希、内部配置或敏感审计字段。
- [x] 添加 OpenAPI 示例、准确区分公开读取与受保护写入的鉴权说明、统一错误响应模型和 `/api/v1` 兼容性策略；Swagger UI 可直接用于联调。
- [x] 其他写接口支持按用户隔离的 `Idempotency-Key`（评论创建/编辑/删除、标签、互链、编辑、状态和删除）；重复请求复用原资源或结果，内容不一致返回 `409`。
- [x] 创建工单支持按用户隔离的 `Idempotency-Key`，重复请求返回原工单，请求内容不一致返回 `409`。
- [x] 前端创建工单为一次表单提交复用同一 `Idempotency-Key`，成功后自动生成下一次提交的 key。
- [x] 为 `/api/v1/*` 的 GET/HEAD 响应设置 `Cache-Control: no-store`，离线时不展示浏览器缓存数据，并由界面重试重新建立连接。
- [x] 实现 API Token 的创建、轮换、撤销、过期、`READ/WRITE` 权限和最后使用时间；列表按创建时间及 ID 稳定排序，创建页限制过期时间必须晚于当前时间，Token 列表加载失败提供重试入口，创建/轮换/撤销成功但列表刷新失败时保留成功结果（无旧列表数据时提示重试），创建/轮换/撤销与审计处于同一事务，轮换时锁定原 Token、拒绝再次轮换已撤销或已过期 Token；后端过滤器更新 `lastUsedAt`，按规范大小写不敏感解析 `Bearer` 方案，无效 Token 响应包含 `WWW-Authenticate: Bearer`，网页 Token 列表展示最后使用时间，权限请求限定为 `READ|READ_WRITE`、过期时间必须为未来时间，scope 按精确值校验，Token 只展示一次并只保存哈希，网页轮换/撤销提供内联二次确认。

## 5. Vue 前端

- [x] 工单列表已接入 `/api/v1/issues` 分页接口，包含加载状态、服务端错误提示、演示数据降级和保留当前筛选条件的重试入口；列表展示全部真实标签，筛选请求使用序列校验丢弃过期响应，离开列表页时会清理防抖筛选请求。
- [x] 工单详情已接入 `/api/v1/issues/{id}` 接口，服务端 `404` 会移除本地同编号演示数据并显示“反馈不存在”，其他服务错误保留正文降级提示并提供重试入口；路由参数切换时会重新加载详情、评论、标签和互链并通过序列校验丢弃旧请求结果，评论加载状态只由最新请求更新，清理旧操作状态及表单提示。
- [x] 创建工单表单已接入 `POST /api/v1/issues`，包含登录要求、提交中和服务端错误提示；后端会规范化首尾空白并拒绝规范化后的空标题/正文。
- [x] 详情页评论已接入 `POST /api/v1/issues/{id}/comments`，包含登录要求、提交中和服务端错误提示；评论分页加载失败会显示可重试反馈。
- [x] 详情页关闭工单已接入 `POST /api/v1/issues/{id}/close`，包含登录、权限、冲突和提交中状态提示。
- [x] 列表页状态按钮已接入关闭/Reopen API，成功后按当前筛选重新加载列表；关闭允许作者或工作人员，Reopen 仅显示给 `STAFF+`，并显示权限与冲突反馈。
- [x] 建立登录、注册接口调用、登出和当前用户状态管理；共享 HTTP 客户端在非认证请求返回 `401` 时清理过期身份，退出请求失败也会清理本地状态并提示用户，受保护页面跳转登录并在登录或注册完成后返回原地址；路由守卫只负责体验，权限仍以后端为准。
- [x] 实现工单列表、筛选栏、分页、创建/编辑表单、详情页、状态操作、评论区、标签和互链组件；受保护页面统一由路由守卫检查登录与管理员权限（匿名管理员页先登录，已登录非管理员再拒绝），未知路径显示统一 404 页面。
- [x] 为 `OPEN`/`CLOSED`、加载、空数据、错误、无权限、冲突和提交中状态提供可理解的界面；列表、详情、评论、互链、状态操作和表单均提供对应反馈及重试入口，详情页加载期间不会误显示不存在面板。
- [x] 根据权限显示 Reopen、关闭、管理和删除操作；工单与标签危险操作均增加内联确认和结果反馈。
- [x] 支持键盘可用性、响应式布局、语义化表单、焦点管理和基础无障碍检查；自定义选择框打开后自动聚焦当前选项，并支持 Enter/Space 打开与确认、方向键、Home/End、Escape、点击外部关闭及按选项文字快速定位，路由切换后提供可聚焦的页面变更提示。
- [x] 统一表单校验和日期/时区展示；共享格式化与字段校验工具，错误提示不暴露后端堆栈。
- [x] 建立共享 API 错误解析器，统一处理网络错误、HTTP 错误和字段校验错误，并接入登录、注册、密码重置、账户安全及工单核心流程。
- [x] 添加前端工具/API 单元测试、自定义选择框浏览器组件交互、公共路由烟测和关键流程 E2E，覆盖登录、创建工单、评论、关闭、Staff Reopen 与管理员路由越权。

## 6. 后端 TUI

- [x] 提供可通过 `TUI_ENABLED=true` 接入实例进程 stdin 的命令入口，并将命令解析、鉴权占位、执行和输出格式分层；支持 `HELP`、`VERSION`、`EXIT`。
- [x] Compose 为后端保持 stdin 与 TTY 开放，TUI 提示符主动刷新并在 stdin 关闭时给出诊断信息。
- [x] 通过同进程 `stdin` 接入 TUI；不复用浏览器 Token，`AUTH LOGIN/LOGOUT/STATUS` 完成管理员身份校验，认证状态仅保存在进程内，每次管理命令从数据库刷新管理员状态，具体运行环境访问控制由部署方负责。
- [x] 实现 `AUTH ADD <user> <email> <password>`、`AUTH DELETE <user>`（停用）、`AUTH EMAIL SET <user> <email>` 和 `AUTH PASSWD <user> <password>`；仅已认证 TUI 管理员可执行，停用/改密在事务提交后踢出目标会话，全部写入审计日志。
- [x] 实现管理员 TUI 命令 `GRANT <user> WITH PERMISSION USER|STAFF|ADMIN`；已认证管理员可通过 TUI 授予 `ADMIN`，网页管理接口仍禁止提升为 `ADMIN`，并写入审计日志。
- [x] 实现管理员 TUI 命令 `ISSUE SET <id> AUTHOR <user>`，目标用户需已启用并写入审计日志。
- [x] 实现管理员 TUI 命令 `ISSUE DELETE <id> --force`，执行软删除并写入审计日志。
- [x] 实现管理员 TUI 命令 `ISSUE SET <id> STATE OPEN|CLOSED`，重复状态返回错误并写入审计日志。
- [x] 实现白名单配置命令 `CONFIG <node> <value>`，支持 CORS 来源和 Sa-Token 会话时长；配置在当前进程即时生效，拒绝端口、数据库、凭据等敏感配置，并写入审计日志。
- [x] 实现管理员 TUI `STATS`，覆盖用户、启用用户、工单状态、标签、未删除评论和审计事件计数。
- [x] 实现管理员 TUI 命令 `TAG ADD/DELETE --force/MV`，检查名称冲突、使用中的标签和审计记录。
- [x] 补充只读命令 `SHOW USERS`、`SHOW TAGS`、`SHOW ISSUES`、脱敏 `SHOW CONFIG`（仅显示白名单配置，不输出端口、数据库或邮件凭据）、`HELP`、`VERSION` 和退出命令；`SHOW ISSUES` 按更新时间及 ID 稳定排序。
- [x] 采用接近 SQL 的大小写不敏感语法，支持引号、空格和反斜杠转义；解析错误返回行号与用法，命令结果保留非零退出码。
- [x] 所有账号、工单和标签删除类危险命令要求显式 `--force`，所有 TUI 写操作写入审计日志；解析器提供引号、转义和行号错误信息。

## 7. 邮件系统（IMAP/SMTP）

- [x] 将 SMTP/IMAP 服务器地址、端口、用户名、发件邮箱、密码/密钥、SSL、超时和轮询周期纳入配置；凭据通过环境变量注入。
- [x] 实现 `MailGateway` 及 SMTP 发信、IMAP 收信适配器；Mailpit 通过 SMTP 配置切换为本地测试实现，业务层可替换网关 Bean。
- [x] 定义 `MailEventType` 并发布注册、工单创建、评论、状态变更和被提及事件；工单/评论中的 `@用户名` 会解析为启用用户并去重通知，通知开关与收件人策略由 `MailProperties` 和投递服务执行。
- [x] 设计并实现可配置的收信规则：同进程定时轮询 IMAP，按 `SUBJECT`、`IN_REPLY_TO` 或两者关联工单号，校验发件人、按配置持久化/去重 Message-ID 并将合规回复追加为评论；IMAP 提取文本及二进制附件大小，`REJECT` 模式按配置上限拒绝超限邮件（记录 `ATTACHMENT_TOO_LARGE`，不创建评论），其他模式忽略附件并保留正文。
- [x] 增加事务提交后的异步发送队列、指数退避重试、失败记录、有界死信队列和投递计数；邮件失败不会回滚已成功的工单写入。
- [x] 增加可配置的通知开关/收件人：投递服务按工单创建、评论和状态变更开关过滤，并向去重后的额外收件人独立投递；附件大小与处理策略、邮件保留周期也可配置，收信轮询会按 `incoming-days` 清理过期收信记录，死信清理任务会按 `outgoing-days` 清理过期出站失败记录。

## 8. 可观测性与运维

- [x] 接入结构化日志、`X-Request-ID`/MDC/审计关联及 Micrometer Prometheus 指标；公开 `health/info/prometheus`，外部告警平台可据此配置规则。
- [x] 提供包含数据库探测的 `/api/v1/health`，数据库异常返回 503，前端区分服务/数据库故障。
- [x] `STATS` 补充邮件成功、失败和死信队列统计。
- [x] 配置备份/恢复脚本（`ops/db-backup.sh`、`ops/db-restore.sh`）、数据库迁移回滚预案、日志保留周期和需审批的隐私清理策略，并记录于 `docs/PRODUCTION.md`。
- [x] 编写后端与前端 Docker 镜像、生产配置模板、Nginx 反向代理/TLS 部署说明和最小权限运行方案；运行时镜像均使用非 root 用户。
- [x] 在 GitHub Actions CI 中执行后端 Maven 构建/测试、前端锁定依赖安装与类型检查/构建、MariaDB Flyway 迁移校验、依赖漏洞扫描和前后端镜像扫描。

## 9. 验收顺序

1. **MVP**：认证、权限、工单 CRUD、状态、评论、标签、REST API、Vue 基础页面。
2. **可运营**：互链、审计、管理员用户管理、TUI 全部核心命令、OpenAPI、日志和指标。
3. **通知与上线**：SMTP/IMAP、异步重试、部署脚本、备份恢复、CI/CD、E2E 与安全验收。

每个里程碑必须满足：迁移可重复执行，核心行为有自动化测试，API 文档同步，越权和错误路径已验证，且可按 README 在干净环境启动。

## 10. 管理面板与站点功能配置

- [x] 为 `ADMIN` 提供网页管理面板入口与后端权限保护，普通用户不可访问。
- [x] 将站点功能配置持久化到独立 `.properties` 配置文件；启动时读取环境变量并覆盖写入文件，网页管理仅修改文件。
- [x] 提供配置项白名单、类型校验、敏感配置保护和并发写入安全，明确环境变量存在时网页修改不会持久化。
- [x] 支持管理面板读取、修改并即时展示注册验证、Turnstile、邮件通知/收信策略、CORS 与会话时长等站点功能配置。
- [x] 为配置读取/修改增加审计记录、API 错误处理及前端保存状态，并通过后端/前端编译检查。
- [x] [#16 站点图标](https://github.com/NovaThaum/SimpIssueHub/issues/16)：支持通过 `SITE_ICON_URL`、站点 `.properties` 配置文件或管理员控制面板设置图标 URL；favicon 和站点头部使用配置图标并保留内置 SVG 回退。
- [x] [#19 Bug | 统计与筛选条件不符](https://github.com/NovaThaum/SimpIssueHub/issues/19)：顶部反馈统计复用分类、标签、作者和搜索筛选条件，统计结果与当前列表保持一致。

## 已确定的产品决策

- [x] 数据库使用 MySQL；暂不要求兼容其他数据库。
- [x] 当前没有 SSO/OIDC，认证方案预留后期通过 Sa-Token 扩展接入的空间。
- [x] 工单作者可以关闭自己创建的工单，但不能删除；`staff+` 可以 Reopen。
- [x] 系统面向 Minecraft 服务器玩家，用于玩家反馈、功能建议和 Bug 处理，不承担内部团队工单场景。
- [x] 邮件回复创建评论规则、通知对象、附件大小和保留周期均可配置。
- [x] TUI 通过同进程 `stdin` 接入；运行环境访问控制由部署方负责。
- [x] 需要全文搜索和 API Token；不实现 Webhook，暂不安排导入导出及额外数据保留/删除合规功能。

## 待确认决策

- [x] Sa-Token 采用 Cookie 与 Header 两者并存：浏览器优先使用 `satoken` Cookie，也兼容 `satoken` Header；API Token 使用 `Authorization: Bearer sih_<token>`，当前单实例使用内存会话，多实例再切换 Redis Dao。
- [x] 普通 `user` 可以编辑自己创建的工单和评论；`staff+` 可编辑任意工单，`admin` 可编辑任意评论；`STAFF` 可创建全局标签，只有 `ADMIN` 可删除未被使用的全局标签。
- [x] 当前为单个 Minecraft 服务器的单租户社区：未删除内容登录后可读；正文采用纯文本，不支持 Markdown/富文本；工单支持网页上传附件，附件类型、大小和持久化目录由独立配置控制；邮件附件继续使用独立大小与处理策略配置；仅保留账号运行必需字段，不收集额外玩家隐私字段。

## 11. GitHub Issue 跟踪（2026-09-05）

以下条目根据 [NovaThaum/SimpIssueHub Issues](https://github.com/NovaThaum/SimpIssueHub/issues) 当前状态整理。开放 Issue 保留为后续工作，关闭 Issue 仅作为已完成需求的追溯记录。

### 高优先级

- [x] [#8 FR | 管理面板](https://github.com/NovaThaum/SimpIssueHub/issues/8)：管理员导航提供统一管理面板，集中进入用户、分类和站点配置管理。
- [x] [#13 FR | 默认过滤author:me](https://github.com/NovaThaum/SimpIssueHub/issues/13)：登录用户默认查看自己创建的开放工单，并可切换回全部开放工单。
- [x] [#17 Bug | 缓存控制](https://github.com/NovaThaum/SimpIssueHub/issues/17)：API 禁止浏览器保存响应，离线时显示连接失败并允许直接重试。
- [x] [#22 FR | Page Titles](https://github.com/NovaThaum/SimpIssueHub/issues/22)：GitHub 风格前端按当前页面更新浏览器标题。
- [x] [#23 FR | 自动订阅](https://github.com/NovaThaum/SimpIssueHub/issues/23)：创建工单或通过网页、邮件留下评论时自动订阅对应工单。
- [x] [#27 Bug | 侧边栏数量统计过滤open](https://github.com/NovaThaum/SimpIssueHub/issues/27)：分类侧栏数量只统计未删除的开放工单，删除保护仍统计分类下全部工单。
- [x] [#11 FR | gh式UI](https://github.com/NovaThaum/SimpIssueHub/issues/11)：GitHub 风格前端作为正式 UI，列表、详情、筛选、时间线和管理页面统一使用 GitHub 风格布局。
- [x] [#20 FR | 增加关闭选项](https://github.com/NovaThaum/SimpIssueHub/issues/20)：关闭原因支持已完成、非计划、无意义和重复，并保留重复目标校验。
- [x] [#25 移除标签页面](https://github.com/NovaThaum/SimpIssueHub/issues/25)：移除公开标签页入口，将标签改为反馈列表筛选条件；标签维护保留在管理员管理面板。
- [x] [#29 FR | 投票功能独立](https://github.com/NovaThaum/SimpIssueHub/issues/29)：新增独立社区投票区域和 `/api/v1/polls` API；历史 Issue 投票继续兼容读取。
- [x] [#30 FR | 更美观的管理面板](https://github.com/NovaThaum/SimpIssueHub/issues/30)：管理面板增加分组导航、卡片布局、移动端适配和配置锁定提示。
- [x] [#31 FR | 分类修改](https://github.com/NovaThaum/SimpIssueHub/issues/31)：管理员可重命名分类并在同一事务中迁移已有反馈，界面提供编辑、取消和明确错误提示。
- [x] [#33 FR | 置顶issue](https://github.com/NovaThaum/SimpIssueHub/issues/33)：工作人员可置顶或取消置顶反馈，置顶反馈在全部筛选和分页列表中优先显示。

- [x] [#12 FR | 默认 filter open](https://github.com/NovaThaum/SimpIssueHub/issues/12)：列表默认只展示 `OPEN` 工单；刷新后仍使用该默认筛选，并允许用户切换到全部/已关闭。
- [x] [#9 FR | Issue editing](https://github.com/NovaThaum/SimpIssueHub/issues/9)：详情页已提供作者/Staff 编辑入口，保存调用 PATCH 接口并处理权限、校验及版本冲突；保存后保留讨论内容并刷新反馈详情。
- [x] [#10 FR | Subcribe](https://github.com/NovaThaum/SimpIssueHub/issues/10)：已增加工单订阅/取消订阅及人数展示，订阅关系通过唯一约束和幂等 REST 接口维护；评论、邮件回复评论和状态变更会通知订阅者，并排除操作者及已有主收件人。

### 中优先级

- [x] [#20 FR | 增加关闭选项](https://github.com/NovaThaum/SimpIssueHub/issues/20)：关闭反馈支持“已完成”“非计划”“重复”三种原因；重复关闭要求目标工单，详情显示关闭原因，前端采用 GitHub 风格下拉菜单并保留旧接口兼容性。
- [x] [#14 子议题](https://github.com/NovaThaum/SimpIssueHub/issues/14)：支持父工单创建子议题、父子层级展示、子议题完成进度和父工单回链；子议题创建沿用标签/分类、权限校验、幂等键、审计和通知。
- [x] [#3 FR | 投票](https://github.com/NovaThaum/SimpIssueHub/issues/3)：已支持由工单发起者创建自定义问题和 2-10 个选项，可配置单选/多选；用户可提交、更新或撤销选择，详情展示每项人数与比例，列表展示参与人数，并覆盖权限、校验、撤销和并发测试。
- [x] [#7 FR | Mail Template](https://github.com/NovaThaum/SimpIssueHub/issues/7)：已统一注册、验证码、密码重置、工单、评论、状态变更和提及通知的纯文本/HTML 模板；管理员可在网页配置主题与正文，环境变量优先，模板仅支持白名单变量并对 HTML 内容转义，不暴露密码、邮箱或 SMTP 凭据。
- [x] [#5 FR | Avatar](https://github.com/NovaThaum/SimpIssueHub/issues/5)：已支持用户设置或清空 HTTP/HTTPS 头像地址；后端只保存地址、不抓取或代理图片，公开工单/评论/用户资料仅返回头像地址且不泄露邮箱；浏览器负责缓存，图片加载失败回退为用户名首字母并使用 `no-referrer`，匿名访问不受影响。

### 体验与视觉

- [x] [#11 FR | gh-liked UI](https://github.com/NovaThaum/SimpIssueHub/issues/11)：参照 GitHub 当前 Issues 界面完成前端重构，包括深色全局栏、仓库身份与导航、Issues 侧栏、紧凑筛选列表、详情时间线和右侧元数据；表单与管理页共用同一视觉系统，并保留无障碍、自定义选择框及移动端适配。

### 已关闭需求追溯

- [x] [#6 移除预设数据](https://github.com/NovaThaum/SimpIssueHub/issues/6)：后端不可用时不再展示演示工单，显示服务器离线提示。
- [x] [#4 要分区~](https://github.com/NovaThaum/SimpIssueHub/issues/4)：已实现工单分类，并支持分类筛选及管理员维护。
- [x] [#2 注册时，没有验证邮箱](https://github.com/NovaThaum/SimpIssueHub/issues/2)：已实现邮箱验证码注册流程。
- [x] [#1 注册时没有人机验证](https://github.com/NovaThaum/SimpIssueHub/issues/1)：已接入 Cloudflare Turnstile，并按请求频率条件触发。
