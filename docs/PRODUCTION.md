# 生产部署

本文面向单租户玩家反馈站点。生产部署至少包含一个后端实例、一个前端 Nginx 实例、一个独立 MySQL/MariaDB 数据库，以及可选的 SMTP/IMAP 服务。不要把本地 Compose 的示例密码、Mailpit 或 TUI 暴露到公网。

## 上线顺序

1. 准备 DNS、TLS 证书、数据库、SMTP/IMAP 和密钥管理服务。
2. 创建独立数据库账号，只授予 `simp_issue_hub` 库权限；先执行一次备份。
3. 构建并扫描后端、前端镜像，注入生产环境变量，不把凭据写入镜像或 Git。
4. 启动后端，等待 Flyway 完成迁移并确认 `/api/v1/health` 返回 `UP`。
5. 启动前端 Nginx，将 `/api/` 代理到后端；最后配置 DNS 和 HTTPS 入口。
6. 完成注册/登录、工单浏览、创建工单、评论、关闭/Reopen、管理员和邮件收发验收。

## 镜像

分别构建后端和前端镜像：

```bash
docker build -t simpissuehub-backend:latest Backend
docker build -t simpissuehub-frontend:latest Frontend
```

两个运行时镜像均使用非 root 用户。后端监听 `8080`；前端 Nginx 监听 `8080`，并将 `/api/` 转发到 Compose/Docker 网络中的 `backend:8080`。

建议给镜像打不可变版本标签，并在部署前执行：

```bash
docker build --pull -t registry.example.com/simpissuehub/backend:0.1.0 Backend
docker build --pull -t registry.example.com/simpissuehub/frontend:0.1.0 Frontend
```

## 生产配置

生产环境必须从密钥管理服务或只对服务账号可读的环境文件注入数据库、SMTP/IMAP 密码，不要烘焙进镜像。至少配置：

```dotenv
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://db.internal:3306/simp_issue_hub?serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=simp_issue_hub
DB_PASSWORD=<secret>
APP_CORS_ALLOWED_ORIGINS=https://issues.example.com
REAL_IP_HEADER=X-Real-IP
SITE_CONFIG_FILE=/var/lib/simpissuehub/site-config.properties
SITE_ICON_URL=https://issues.example.com/assets/site-icon.png
SA_TOKEN_COOKIE_SECURE=true
SA_TOKEN_COOKIE_SAME_SITE=Strict
TUI_ENABLED=false
EMAIL_VERIFICATION_REQUIRED=true
TURNSTILE_SECRET_KEY=<secret>
TURNSTILE_EXPECTED_HOSTNAME=issues.example.com
SMTP_HOST=smtp.example.com
SMTP_PASSWORD=<secret>
MAIL_FROM=no-reply@example.com
MAIL_PUBLIC_BASE_URL=https://issue.example.com
# 可选：通过环境变量锁定单个邮件模板，正文中的换行使用 \\n+MAIL_TEMPLATE_COMMENT_CREATED_SUBJECT='[{{siteName}}] 工单 #{{issueId}} 收到新评论'
MAIL_TEMPLATE_COMMENT_CREATED_BODY='你好，{{username}}：\\n\\n{{message}}\\n\\n工单编号：#{{issueId}}'
IMAP_HOST=imap.example.com
IMAP_PASSWORD=<secret>
```

前端镜像必须在构建阶段传入与私钥配套的 Turnstile 公钥：

```bash
docker build --build-arg VITE_TURNSTILE_SITE_KEY=<site-key> -t simp-issue-hub-frontend ./Frontend
```

Turnstile 私钥只能注入后端运行环境。若配置了 `TURNSTILE_SECRET_KEY`，但前端未注入公钥，注册页会明确提示配置错误并阻止发送验证码。

多实例部署时应将 Sa-Token Dao 替换为 Redis 实现；当前内存 Dao 仅适合单实例。只有需要通过受控 stdin 管理实例时才启用 TUI。

后端容器建议设置 `read_only: true`、临时目录和资源限制；至少配置 `SPRING_PROFILES_ACTIVE=prod`、`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`APP_CORS_ALLOWED_ORIGINS`、`SA_TOKEN_COOKIE_SECURE=true`。若启用 TUI，必须通过编排平台的受控 stdin 接入，并限制能够写入 stdin 的运维身份。

认证方式固定为 Cookie 与 Header 并存：浏览器登录态通过 `satoken` Cookie（SameSite 和 Secure 由环境变量控制），非浏览器客户端使用 `satoken` Header 或 API Token 的 `Authorization: Bearer sih_<token>`。API Token 不会创建 Sa-Token 会话，且每个 Token 必须配置最小 `READ/WRITE` scope；不要把 API Token 写入 Cookie 或 URL。

启用 IMAP 回复评论时，将 `TUI_ENABLED` 等运行配置与 IMAP 凭据通过密钥管理服务注入。轮询根据 `MAIL_ISSUE_REFERENCE`（`SUBJECT`、`IN_REPLY_TO` 或 `SUBJECT_OR_IN_REPLY_TO`）关联 `#工单号`，只接受能关联到本地用户的发件人；重复 `Message-ID` 会被忽略。处理结果保存在 `incoming_mail_messages`，便于审计和排查。未匹配、未验证或超限邮件不会创建评论。

邮件模板通过 `MAIL_TEMPLATE_<TYPE>_SUBJECT` 和 `MAIL_TEMPLATE_<TYPE>_BODY` 配置，也可在管理员站点配置页编辑。模板只允许白名单变量；系统会清理主题中的换行，并对 HTML 正文进行转义。`MAIL_PUBLIC_BASE_URL` 用于生成工单详情链接，必须是受信任的 HTTP/HTTPS 公网地址。

## TLS 与反向代理

公网入口必须由负载均衡器或独立反向代理终止 TLS，仅暴露 HTTPS。将 HTTP 永久重定向到 HTTPS，并转发 `Host`、`X-Forwarded-Proto`、`X-Forwarded-For` 和 `X-Request-ID`。证书应由 ACME 或组织证书系统自动续期。

后端、数据库和邮件调试端口不得直接暴露公网；公网只访问前端代理。生产环境不要部署 Mailpit。

前端容器可直接使用仓库中的 `Frontend/deploy/nginx.conf`。若后端不在名为 `backend` 的 Docker 服务中，需在部署镜像或编排配置中替换 `proxy_pass` 目标，并确保 SPA fallback 仍指向 `/index.html`。

## 最小权限

- 容器保持只读根文件系统，按需挂载独立临时目录；禁止 privileged、host network 和 Docker socket。
- 后端和前端使用镜像内置非 root UID/GID；数据库使用仅限 `simp_issue_hub` 库的独立账号。
- 只允许前端访问后端端口，只允许后端访问数据库及 SMTP/IMAP；数据库应通过网络策略限制来源。
- 为容器设置 CPU、内存、进程数限制，并在编排平台启用 `no-new-privileges` 和删除全部非必要 Linux capabilities。
- 生产 CORS 仅配置实际 HTTPS 域名，`SA_TOKEN_COOKIE_SECURE=true`，并限制管理指标端点的网络访问。

## 上线检查

启动前备份数据库，确认 Flyway 待执行版本和回滚预案；启动后检查 `/api/v1/health`、`/actuator/health`、登录、创建/查看工单、评论、关闭/Reopen、API Token、邮件投递及 Prometheus 指标。日志输出到 stdout，由平台采集并按保留策略清理。确认匿名仅能访问公开的站点配置与认证/健康检查端点、无法读取工单内容，普通用户不能访问管理员接口，网页不能授予 `ADMIN`。

## 备份、恢复与迁移回滚

工单附件保存在 `ATTACHMENT_STORAGE_DIRECTORY`。容器部署必须将该目录挂载到持久卷；仓库 Compose 使用 `backend-data:/app/data`。数据库备份与附件目录快照需要在同一维护窗口完成，并一起恢复，否则数据库中的附件元数据可能指向不存在的文件。不要允许 Web 服务账号之外的进程写入附件目录。

仓库中的 `ops/db-backup.sh` 使用 `mariadb-dump --single-transaction` 创建一致性压缩备份，并生成 SHA-256 校验文件。备份目录必须由服务账号独占，且上传对象存储时启用服务端加密和版本保留：

```bash
DB_HOST=db.internal DB_PORT=3306 DB_NAME=simp_issue_hub \
  DB_USERNAME=simp_issue_hub DB_PASSWORD='从密钥管理服务读取' \
  ./ops/db-backup.sh /var/backups/simpissuehub
```

恢复前停止后端写入、核对校验文件并确认目标库；`ops/db-restore.sh` 要求交互输入 `RESTORE`，避免误覆盖：

```bash
DB_HOST=db.internal DB_NAME=simp_issue_hub \
  DB_USERNAME=simp_issue_hub DB_PASSWORD='从密钥管理服务读取' \
  ./ops/db-restore.sh /var/backups/simpissuehub/simp_issue_hub_20260826T000000Z.sql.gz
```

Flyway 迁移采用只前进策略，不在生产环境手工删除 `flyway_schema_history` 或修改已成功执行的迁移。迁移前保留备份；若新版本失败，先停止发布并确认该版本的 `success = 0` 且没有留下结构变更。像 V15 这种确认未执行成功的迁移，可以在修复脚本后只删除对应的失败历史行，让 Flyway 重新执行；已成功版本必须通过新的版本号修复。恢复后检查 `SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5`，然后访问 `/api/v1/health`。

## 日志与隐私清理

应用日志只输出 stdout，不写入容器持久卷。日志平台应按组织策略设置保留期、访问审计和加密；建议生产日志保留 30 天，安全审计日志单独保留 180 天。应用不会自动清理工单正文、评论或审计记录。

当运营方依据隐私政策确定保留期限后，在维护窗口执行经过审批的清理语句。以下示例只清理已使用/过期的短期令牌、幂等记录和密码重置令牌，不删除玩家工单或审计证据：

```sql
DELETE FROM password_reset_tokens WHERE expires_at < UTC_TIMESTAMP(6) OR used_at IS NOT NULL;
DELETE FROM idempotency_records WHERE created_at < UTC_TIMESTAMP(6) - INTERVAL 30 DAY;
```

如需清理审计日志，必须先导出加密归档并取得合规审批；工单、评论、用户账号和 API Token 的删除/匿名化策略应由业务方另行确认后制定迁移脚本。每次清理记录操作者、时间范围、行数和归档位置，禁止使用无条件 `DELETE`。

## 故障处理

- `/api/v1/health` 返回 503：先检查数据库网络、账号权限、连接池和 Flyway 日志；不要通过修改 `ddl-auto` 绕过结构校验。
- 登录态全部失效：单实例检查会话配置；多实例确认所有实例使用同一 Redis Dao 和一致的 Cookie 域名、Secure、SameSite 设置。
- 邮件积压：检查 SMTP/IMAP 连通性、失败记录和死信计数；不要直接删除失败记录，先保留审计证据。
- Flyway 迁移失败：停止发布，保留日志和数据库备份，修复后使用新版本迁移；不要修改已经执行的 `V*` 文件。
