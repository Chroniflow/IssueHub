# 依赖与升级策略

## 当前基线

| 组件 | 版本/范围 | 说明 |
| --- | --- | --- |
| Java | 21 | Spring Boot 3 的运行时与编译目标 |
| Spring Boot | 3.5.6 | 使用 Jakarta 命名空间和 Spring Framework 6 |
| Sa-Token | 1.46.0 | 使用 `sa-token-spring-boot3-starter` 与 `sa-token-jwt` |
| Springdoc | 2.8.13 | Spring Boot 3 WebMVC 的 OpenAPI/Swagger UI 集成 |
| Maven | 3.9+ | 后端唯一构建工具，版本和依赖由 `Backend/pom.xml` 管理 |

## 兼容性约束

- Sa-Token 必须使用 `sa-token-spring-boot3-starter`，不得替换为 Boot 2 starter。
- Java 版本保持 21；升级 Spring Boot 时先确认 Sa-Token starter、Springdoc 和 Jakarta API 的兼容性。
- 数据库驱动与 Flyway 迁移必须同时验证 MySQL 和项目使用的 MariaDB 版本。
- Sa-Token 默认使用单实例本地会话；多实例部署前需引入匹配版本的 Redis Dao，并验证踢人、过期和并发登录语义。
- 认证传输采用 Cookie + Header 并存：浏览器会话使用 `satoken` Cookie（也接受同名 Header），集成调用使用 `Authorization: Bearer sih_<token>` API Token。两者不可互换，API Token 由独立过滤器校验并按 `READ/WRITE` scope 限制。

## 升级流程

1. 在独立分支修改 `Backend/pom.xml` 的版本属性，保留可回滚的单一版本变更。
2. 执行 `mvn package -DskipTests`，确认编译、Flyway 资源和 Spring Boot 重打包均成功。
3. 检查 Sa-Token 登录、注销、活跃超时、API Token 和 TUI 管理员认证行为。
4. 更新本文件、README 和 OpenAPI 认证描述，再合并升级。

版本升级不得修改既有 API 字段语义；破坏性变更使用新的 API 版本路径。
