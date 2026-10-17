# SimpIssueHub

> 对公工单系统

## 工单功能

参照GitHub中的Issue模块。

允许如下功能：

- Tags
- State(open, close)
- Reopen [Permission: staff+]
- Comment
- Link to another issue

## 权限系统

权限等级：`anonymous` < `user` < `staff` < `admin`

`anonymous`为最低等级，此等级不可分配，为未登录游客等级。

`user`为一般用户。未表明`[Permission]`的功能为此等级可使用

`staff`为工作人员。

`admin`为站点管理员。

工单作者可以编辑自己创建的工单和评论；`staff+` 可以编辑任意工单，`admin` 可以编辑任意评论。工作人员可以创建全局标签，只有管理员可以删除未被使用的全局标签；普通用户不能修改全局标签定义。

网页管理接口中的`admin`可以将`user`提升到`staff`，但是不可以提升到`admin`；已认证的 TUI 管理员可通过 TUI 授予`ADMIN`。

## 后端TUI

后端需要有命令行工具。

在实例启动后可以接入其进程tty，并使用自带的命令。详细如下表：

- `GRANT <user> WITH PERMISSION <perm>`
- `AUTH DELETE <user>`（停用用户）
- `AUTH EMAIL SET <user> <email>`
- `AUTH ADD <user> <email> <password>`
- `AUTH PASSWD <user> <password>`
- `SHOW ISSUES`
- `SHOW TAGS`
- `ISSUE SET <issueId> STATE <state>`
- `ISSUE DELETE <issueId>`
- `ISSUE SET <issueId> AUTHOR <user>`
- `TAG ADD <tag>`
- `TAG DELETE <tag>`
- `TAG MV <oldTag> <newTag>`
- `CONFIG <configNode> <configValue>`（仅允许 `app.cors.allowed-origins`、`sa-token.timeout`、`sa-token.active-timeout`）
- `STATS` # 统计

以上为我所想到的功能，你可以进行补充。保证风格一致(SQL语句风格)

## 邮件系统

邮件使用IMAP/SMTP模式。

具体服务器地址、端口、用户名、邮件、密码、SSL可配置。

## 范围与内容格式

当前部署面向单个 Minecraft 服务器社区，不引入组织/租户模型。未删除的工单、评论、标签和作者名称在登录后可读；软删除工单不再出现在读取接口中。工单和评论正文使用纯文本，不执行 HTML/脚本；工单支持登录用户上传附件，附件类型、大小和持久化目录由配置控制，邮件收信附件仍按邮件附件大小与处理策略独立执行。账号仅保留用户名、邮箱、密码哈希、角色和启用状态等运行必需字段，不收集游戏外的额外隐私字段。
