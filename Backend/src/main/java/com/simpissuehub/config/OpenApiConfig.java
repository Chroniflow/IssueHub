package com.simpissuehub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OperationCustomizer;

import java.util.Map;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI simpIssueHubOpenAPI() {
        ObjectSchema error = new ObjectSchema();
        error.addProperty("status", new IntegerSchema().example(400));
        error.addProperty("code", new StringSchema().example("VALIDATION_FAILED"));
        error.addProperty("message", new StringSchema().example("请求字段校验失败"));
        error.addProperty("path", new StringSchema().example("/api/v1/issues"));
        error.addProperty("timestamp", new StringSchema().format("date-time"));
        error.addProperty("fields", new MapSchema().additionalProperties(new StringSchema()));

        return new OpenAPI()
                .info(new Info()
                        .title("SimpIssueHub API")
                        .version("v1")
                        .description("Minecraft 服务器玩家反馈 API。兼容性策略：/api/v1 路径只新增字段和端点，不修改既有字段语义；破坏性变更使用新的版本路径。\n\n认证：浏览器可使用 Sa-Token 的 satoken Cookie/Header；脚本和集成使用 Authorization: Bearer sih_<token>。健康检查、认证接口以及工单/标签的公开读取接口无需认证，其余写入和账户管理接口需要认证。\n\n错误响应统一包含 status、code、message、path、timestamp，字段校验失败时额外包含 fields。")
                        .contact(new Contact().name("SimpIssueHub")))
                .components(new Components()
                        .addSchemas("ApiError", error)
                        .addSecuritySchemes("sessionToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("satoken")
                                .description("Sa-Token 会话令牌；浏览器通常由 satoken Cookie 自动携带。"))
                        .addSecuritySchemes("apiToken", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("sih_<token>")
                                .description("API Token 只在创建或轮换时显示一次，权限分为 READ 与 READ_WRITE。")));
    }

    @Bean
    public OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            operation.getResponses()
                    .addApiResponse("400", errorResponse("请求参数或字段校验失败", 400, "VALIDATION_FAILED", "请求字段校验失败"))
                    .addApiResponse("401", errorResponse("未认证或 API Token 无效", 401, "UNAUTHENTICATED", "请先登录"))
                    .addApiResponse("403", errorResponse("当前账号没有执行该操作的权限", 403, "FORBIDDEN", "没有操作权限"))
                    .addApiResponse("404", errorResponse("资源不存在", 404, "NOT_FOUND", "工单不存在"))
                    .addApiResponse("409", errorResponse("资源状态或唯一约束冲突", 409, "CONFLICT", "资源状态已变化"));
            return operation;
        };
    }

    private ApiResponse errorResponse(String description, int status, String code, String message) {
        Example example = new Example().value(Map.of(
                "status", status,
                "code", code,
                "message", message,
                "path", "/api/v1/issues/142",
                "timestamp", "2026-08-26T08:00:00Z"));
        MediaType mediaType = new MediaType()
                .schema(new ObjectSchema().$ref("#/components/schemas/ApiError"))
                .addExamples(code.toLowerCase(), example);
        return new ApiResponse().description(description)
                .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json", mediaType));
    }
}
