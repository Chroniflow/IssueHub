# SimpIssueHub

SimpIssueHub 是面向 Minecraft 服务器玩家的反馈系统，用于提交 Bug、功能建议和一般反馈。站点内容默认登录可见，系统提供工单浏览、评论讨论、附件上传、标签筛选、状态流转、关闭原因（已完成/非计划/重复）、全文搜索、工单互链、API Token、管理员用户管理和同进程 TUI。

玩家社区与内部团队工单分离：内部协作继续使用 GitLab/Gitea 等系统，本项目只处理玩家侧反馈。

访问控制：工单、评论、标签与服务器等内容的读取接口均需登录（会话或 API Token）；仅站点配置 `/api/v1/site/config`、认证 `/api/v1/auth/**` 与健康检查 `/api/v1/health` 对外公开。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router
- 后端：Spring Boot 3.5、Java 21、Maven、Sa-Token 1.46.0
- 数据库：MySQL 8 / MariaDB 兼容模式，Flyway 迁移
- 邮件联调：Mailpit（Docker Compose）

依赖基线与升级流程见 [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md)。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 20+ 与 pnpm 10+
- MySQL 或 MariaDB（默认端口 3306）
- Docker Compose（仅使用容器联调时需要）

## 本地启动

先创建 `simp_issue_hub` 数据库，并为运行账号授予该库的建表、迁移和读写权限。`docs/DEV_DATABASE.sql` 只创建数据库，不创建业务表；业务表由 Flyway 在后端首次启动时自动迁移。

```bash
cd Backend
# 按实际环境设置 DB_URL、DB_USERNAME、DB_PASSWORD 等变量
export DB_URL='jdbc:mysql://127.0.0.1:3306/simp_issue_hub?serverTimezone=UTC&characterEncoding=UTF-8'
export DB_USERNAME='simp_issue_hub'
export DB_PASSWORD='change-me'
mvn spring-boot:run
```

另开终端启动前端：

```bash
cd Frontend
pnpm install
cp .env.example .env.local
pnpm dev
```

默认地址：前端 `http://localhost:5173`，后端 `http://localhost:8080`，Swagger UI 为 `http://localhost:8080/swagger-ui.html`，OpenAPI JSON 为 `http://localhost:8080/v3/api-docs`，健康检查为 `http://localhost:8080/api/v1/health`。

Issue 分享图片：后端提供需登录的只读端点 `GET /api/v1/issues/{id}/image`，服务端按 Issue 详情页风格渲染固定 1200×630 图片，包含标题、状态、所属服务器、标签、正文（第 0 条时间线内容）和最后一条评论；中间评论会折叠显示数量，登录、订阅、添加标签、操作和添加评论等交互控件不会出现在图片中。请求需携带登录会话或 API Token。默认返回 PNG，也可通过 `?format=jpeg` 返回 JPEG：

```text
GET /api/v1/issues/142/image
GET /api/v1/issues/142/image?format=jpeg
```

图片渲染使用随 JAR 内置的 Noto Sans CJK 简体中文字体，不依赖生产服务器字体或 Debian APT 源；`IMAGE_FONT_FILE` 可选地指定其它 TTF/OTF/TTC 字体文件。

使用 OpenResty 托管 `Frontend/dist` 时，请配置 SPA fallback，参见 [OpenResty 部署指南](docs/OPENRESTY.md)。

## Docker Compose

Compose 会启动 Spring Boot、MySQL 和 Mailpit，并等待 MySQL 健康检查通过后再启动后端：

```bash
docker compose up --build
```

后端地址为 `http://localhost:8080`，Mailpit 管理界面为 `http://localhost:8025`。数据保存在 Compose volume `mysql-data` 中。示例密码仅用于本地开发，生产环境必须通过环境变量或密钥管理服务替换。

## 配置

后端示例配置见 `.env.example` 和 `Backend/src/main/resources/application-local.yml`，前端 API 地址见 `Frontend/.env.example`。常用变量：

| 变量 | 说明 |
| --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 数据库连接 |
| `SERVER_PORT` | 后端监听端口 |
| `APP_CORS_ALLOWED_ORIGINS` | 逗号分隔的前端来源 |
| `TUI_ENABLED` | 是否接入进程 stdin TUI |
| `SA_TOKEN_TIMEOUT` | 登录态最长有效期（秒） |
| `SA_TOKEN_ACTIVE_TIMEOUT` | 无操作超时（秒） |
| `SA_TOKEN_LASTING_COOKIE` | 是否写入持久登录 Cookie，默认 `true`；关闭浏览器后仍保持登录 |
| `VITE_API_BASE_URL` | 前端 API 根地址 |
| `SMTP_*` / `IMAP_*` | 邮件发送、收信服务器与凭据 |
| `MAIL_*` | 邮件通知、回复、附件和保留策略 |
| `EMAIL_VERIFICATION_*` | 注册邮箱验证码开关、有效期、冷却和尝试次数 |
| `TURNSTILE_SECRET_KEY` / `TURNSTILE_EXPECTED_HOSTNAME` | Turnstile 服务端密钥和允许的站点域名 |
| `REAL_IP_HEADER` | 可信反向代理注入的客户端 IP 请求头，例如 `X-Real-IP` 或 `X-Forwarded-For` |
| `SITE_CONFIG_FILE` | 站点功能配置文件路径，默认 `./data/site-config.properties`；对应环境变量存在时环境变量优先且网页不可修改 |
| `SITE_ICON_URL` | 站点图标图片 URL（仅支持 `http`/`https`）；也可在站点配置文件或管理员控制面板中设置 |
| `SITE_ABOUT_CONTENT` | “关于”页面内容；也可在管理员控制面板中编辑，环境变量优先 |
| `ATTACHMENT_STORAGE_DIRECTORY` | 工单附件本地存储目录，默认 `./data/attachments`；生产环境必须挂载持久卷并纳入备份 |
| `ATTACHMENT_MAX_BYTES` / `ATTACHMENT_MAX_REQUEST_BYTES` | 单文件和 multipart 请求大小上限，默认分别为 10 MiB 和 11 MiB |
| `ATTACHMENT_ALLOWED_EXTENSIONS` | 允许上传的扩展名；服务端还会按文件签名/内容复核真实类型 |
| `VITE_TURNSTILE_SITE_KEY` | 前端构建时注入的 Turnstile 公钥 |

不要提交 `.env.local`、真实密码、Token 或生产密钥。

站点图标也可以写入 `SITE_CONFIG_FILE` 指向的 properties 文件（例如 `data/site-config.properties`）：

```properties
SITE_ICON_URL=https://issues.example.com/assets/site-icon.png
```

管理员在“站点配置”页面保存的值会写入该文件；如果设置了 `SITE_ICON_URL` 环境变量，则环境变量优先且页面显示为锁定状态。

## 数据库迁移

迁移文件位于 `Backend/src/main/resources/db/migration`，按 `V1__...`、`V2__...` 顺序执行。不要手工修改已执行的迁移；新增结构请创建下一个版本文件。应用启动时 `spring.flyway.enabled=true`，JPA 使用 `ddl-auto=validate` 检查结构而不自动改表。

## TUI

设置 `TUI_ENABLED=true` 后，后端进程从 stdin 读取命令。启动后先认证：

```text
AUTH LOGIN <管理员用户名> <密码>
HELP
```

支持用户管理、`GRANT`、工单状态/作者/软删除、标签、`CONFIG`、`STATS` 和只读查询。TUI 可授予 `ADMIN`；网页管理接口禁止将用户提升为 `ADMIN`。TUI 不复用浏览器 Token，stdin 的访问控制由部署环境负责。

TUI 命令示例：

```text
AUTH LOGIN admin <密码>
SHOW USERS
GRANT player WITH PERMISSION STAFF
ISSUE SET 42 STATE CLOSED
AUTH LOGOUT
EXIT
```

Compose 后台启动时需通过 `docker compose attach backend` 连接后端 stdin；按 `Ctrl-P`、`Ctrl-Q` 可退出 attach 而不停止容器。仓库的 Compose 已为后端启用 `stdin_open` 与 TTY。

## 注册验证

注册默认要求邮箱验证码。验证码通过已配置的 SMTP 服务发送，默认 10 分钟有效、60 秒内不可重复发送、最多尝试 5 次。生产环境同时配置后端 `TURNSTILE_SECRET_KEY`、`TURNSTILE_EXPECTED_HOSTNAME`，并在前端构建时通过 `VITE_TURNSTILE_SITE_KEY` 注入对应公钥；后端未配置私钥时 Turnstile 自动关闭，便于本地使用 Mailpit 调试。

## 邮件模板

系统统一使用 `app.mail.templates.*` 模板发送注册成功、验证码、密码重置、工单创建、评论、状态变更和提及通知。管理员可在网页“站点配置”中调整主题和正文；环境变量（例如 `MAIL_TEMPLATE_COMMENT_CREATED_BODY`）优先级最高。正文模板支持 `{{siteName}}`、`{{username}}`、`{{issueId}}`、`{{commentId}}`、`{{message}}`、`{{actionUrl}}`、`{{verificationCode}}` 和 `{{expiresMinutes}}` 等白名单变量，未知变量会原样保留。系统同时发送纯文本和安全转义后的 HTML 版本，不会把密码、邮箱或 SMTP 凭据提供给模板。

## 构建与检查

```bash
cd Backend && mvn package -DskipTests
cd Frontend && pnpm build
```

测试命令：

```bash
cd Backend && mvn test
cd Frontend && pnpm test
cd Frontend && pnpm test:e2e
```

后端集成测试需要可访问的 MySQL/MariaDB，并通过 `DB_USERNAME`、`DB_PASSWORD` 提供凭据；测试使用事务回滚，不应连接生产库。前端 E2E 默认启动 Vite，可通过 `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` 指定本机 Chromium。

## 生产部署提示

生产环境应使用 HTTPS 反向代理，限制 CORS 来源，替换所有示例凭据，使用独立数据库账号，配置外部会话存储（多实例时接入 Sa-Token Redis Dao），并为数据库、日志和迁移建立备份与回滚预案。不要把 Mailpit 暴露到公网；SMTP/IMAP 参数和邮件保留策略需通过部署配置提供。

完整的镜像构建、生产配置、反向代理/TLS、备份恢复和最小权限运行方案见 [docs/PRODUCTION.md](docs/PRODUCTION.md)。
