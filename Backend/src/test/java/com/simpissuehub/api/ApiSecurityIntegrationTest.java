package com.simpissuehub.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueRepository;
import com.simpissuehub.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
class ApiSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private IssueRepository issues;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private User author;
    private User other;
    private User staff;
    private User admin;
    private Issue issue;

    @BeforeEach
    void setUp() {
        author = saveUser("test_author", User.Role.USER, true);
        other = saveUser("test_other", User.Role.USER, true);
        staff = saveUser("test_staff", User.Role.STAFF, true);
        admin = saveUser("test_admin", User.Role.ADMIN, true);
        issue = new Issue();
        issue.title = "Integration permission matrix";
        issue.body = "Created by the integration test and rolled back afterwards.";
        issue.author = author;
        issue = issues.saveAndFlush(issue);
    }

    @AfterEach
    void clearSessions() {
        for (User user : java.util.List.of(author, other, staff, admin)) {
            StpUtil.getStpLogic().logout(user.id);
            StpUtil.getStpLogic().untieDisable(user.id);
        }
    }

    @Test
    void healthChecksDatabaseAndProtectedAccountRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void registrationConfigIsPublicAndReturnsFeatureFlags() throws Exception {
        mockMvc.perform(get("/api/v1/auth/registration/config"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.emailVerificationRequired").isBoolean())
                .andExpect(jsonPath("$.turnstileEnabled").isBoolean());
    }

    @Test
    void issueCountsFollowTheSameFiltersAsTheIssueList() throws Exception {
        String token = createSession(author);
        mockMvc.perform(get("/api/v1/issues/counts").header("satoken", token).param("category", "不存在的分类"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.all").value(0))
                .andExpect(jsonPath("$.open").value(0))
                .andExpect(jsonPath("$.closed").value(0));

        mockMvc.perform(get("/api/v1/issues/counts").header("satoken", token).param("category", issue.category).param("author", author.username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.all").value(1))
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.closed").value(0));
    }

    @Test
    void siteConfigIsPublicAndDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/site/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iconUrl").value(nullValue()));
    }

    @Test
    void issueContentRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/issues")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/issues/counts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/issues/{id}", issue.id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/issues/{id}/comments", issue.id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/issues/{id}/image", issue.id)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionSurvivesAcrossRequestsAndKickoutInvalidatesIt() throws Exception {
        String token = createSession(author);
        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(author.username));
        me(token).andExpect(status().isOk());

        StpUtil.getStpLogic().kickout(author.id);
        me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expiredSessionIsRejected() throws Exception {
        String expired = StpUtil.getStpLogic().createLoginSession(author.id,
                SaLoginParameter.create().setTimeout(1).setIsShare(false));
        Thread.sleep(1_200);
        me(expired).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountSessionIsRejected() throws Exception {
        String disabled = createSession(other);
        other.enabled = false;
        users.saveAndFlush(other);
        me(disabled).andExpect(status().isUnauthorized());
    }

    @Test
    void secondLoginReplacesFirstSessionWhenConcurrencyIsDisabled() throws Exception {
        String first = createSession(staff);
        String second = createSession(staff);
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void permissionMatrixRejectsUnauthorizedAndAllowsExpectedRoles() throws Exception {
        mockMvc.perform(post("/api/v1/issues/{id}/close", issue.id))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/issues/{id}/close", issue.id).header("satoken", createSession(other)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/issues/{id}/close", issue.id).header("satoken", createSession(author)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"));

        mockMvc.perform(post("/api/v1/issues/{id}/reopen", issue.id).header("satoken", createSession(author)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/issues/{id}/reopen", issue.id).header("satoken", createSession(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("OPEN"));

        mockMvc.perform(post("/api/v1/issues/{id}/close", issue.id).header("satoken", createSession(admin))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"));
        mockMvc.perform(post("/api/v1/issues/{id}/reopen", issue.id).header("satoken", createSession(admin))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("OPEN"));

        mockMvc.perform(get("/api/v1/admin/users").header("satoken", createSession(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").header("satoken", createSession(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyStaffCanPinAndPinnedIssuesSortFirst() throws Exception {
        mockMvc.perform(patch("/api/v1/issues/{id}/pinned", issue.id)
                        .header("satoken", createSession(author)).contentType(APPLICATION_JSON).content("{\"pinned\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/issues/{id}/pinned", issue.id)
                        .header("satoken", createSession(staff)).contentType(APPLICATION_JSON).content("{\"pinned\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(true));
        mockMvc.perform(get("/api/v1/issues").header("satoken", createSession(staff)).param("state", "OPEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(issue.id))
                .andExpect(jsonPath("$.content[0].pinned").value(true));
    }

    @Test
    void closeSupportsNotPlannedAndDuplicateReasons() throws Exception {
        Issue duplicateTarget = new Issue();
        duplicateTarget.title = "已有的目标反馈";
        duplicateTarget.body = "用于验证重复关闭目标。";
        duplicateTarget.author = author;
        duplicateTarget = issues.saveAndFlush(duplicateTarget);

        mockMvc.perform(post("/api/v1/issues/{id}/close", issue.id)
                        .header("satoken", createSession(author))
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"DUPLICATE\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/issues/{id}/close", issue.id)
                        .header("satoken", createSession(author))
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"DUPLICATE\",\"duplicateOfIssueId\":" + duplicateTarget.id + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.closeReason").value("DUPLICATE"))
                .andExpect(jsonPath("$.duplicateOfIssueId").value(duplicateTarget.id));
    }

    @Test
    void invalidSessionTokenIsRejected() throws Exception {
        me("definitely-not-a-valid-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void issueSubscriptionIsAuthenticatedAndIdempotent() throws Exception {
        mockMvc.perform(get("/api/v1/issues/{id}/subscription", issue.id))
                .andExpect(status().isUnauthorized());

        String token = createSession(other);
        mockMvc.perform(put("/api/v1/issues/{id}/subscription", issue.id).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(1));
        mockMvc.perform(put("/api/v1/issues/{id}/subscription", issue.id).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriberCount").value(1));

        mockMvc.perform(delete("/api/v1/issues/{id}/subscription", issue.id).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false))
                .andExpect(jsonPath("$.subscriberCount").value(0));
        mockMvc.perform(delete("/api/v1/issues/{id}/subscription", issue.id).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriberCount").value(0));
    }

    @Test
    void creatingIssueAndCommentingAutomaticallySubscribeTheActor() throws Exception {
        String otherToken = createSession(other);
        mockMvc.perform(post("/api/v1/issues/{id}/comments", issue.id)
                        .header("satoken", otherToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"body\":\"自动订阅测试评论\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/issues/{id}/subscription", issue.id).header("satoken", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(1));

        String request = "{\"title\":\"自动订阅创建测试\",\"body\":\"创建后应自动订阅。\",\"tag\":\"反馈\",\"category\":\"其他\"}";
        String authorToken = createSession(author);
        String response = mockMvc.perform(post("/api/v1/issues")
                        .header("satoken", authorToken)
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long createdIssueId = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/v1/issues/{id}/subscription", createdIssueId).header("satoken", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(1));
    }

    @Test
    void issuePollSupportsCustomMultipleChoiceReplacementAndWithdrawal() throws Exception {
        String authorToken = createSession(author);
        String staffToken = createSession(staff);
        mockMvc.perform(get("/api/v1/issues/{id}/poll", issue.id).header("satoken", authorToken)).andExpect(status().isNotFound());

        String body = "{\"question\":\"下次活动主题？\",\"multipleChoice\":true,\"options\":[\"建筑\",\"探索\",\"生存\"]}";
        mockMvc.perform(post("/api/v1/issues/{id}/poll", issue.id)
                        .header("satoken", createSession(other)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/issues/{id}/poll", issue.id)
                        .header("satoken", authorToken).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        String response = mockMvc.perform(post("/api/v1/issues/{id}/poll", issue.id)
                        .header("satoken", staffToken).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creatorId").value(staff.id))
                .andExpect(jsonPath("$.question").value("下次活动主题？"))
                .andExpect(jsonPath("$.options.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        JsonNode options = objectMapper.readTree(response).get("options");
        long first = options.get(0).get("id").asLong();
        long second = options.get(1).get("id").asLong();

        mockMvc.perform(get("/api/v1/issues/{id}/poll", issue.id).header("satoken", authorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantCount").value(0));
        mockMvc.perform(get("/api/v1/issues/{id}/poll/ballot", issue.id))
                .andExpect(status().isUnauthorized());

        String otherToken = createSession(other);
        mockMvc.perform(put("/api/v1/issues/{id}/poll/ballot", issue.id)
                        .header("satoken", otherToken).contentType(APPLICATION_JSON)
                        .content("{\"optionIds\":[" + first + "," + second + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.options[0].voteCount").value(1))
                .andExpect(jsonPath("$.options[1].voteCount").value(1));
        mockMvc.perform(get("/api/v1/issues").header("satoken", otherToken).param("query", issue.title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pollParticipants").value(1));
        mockMvc.perform(delete("/api/v1/issues/{id}/poll", issue.id).header("satoken", staffToken))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/issues/{id}/poll/ballot", issue.id)
                        .header("satoken", otherToken).contentType(APPLICATION_JSON)
                        .content("{\"optionIds\":[" + second + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.options[0].voteCount").value(0))
                .andExpect(jsonPath("$.options[1].voteCount").value(1));
        mockMvc.perform(get("/api/v1/issues/{id}/poll/ballot", issue.id).header("satoken", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.optionIds[0]").value(second));

        mockMvc.perform(delete("/api/v1/issues/{id}/poll/ballot", issue.id).header("satoken", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantCount").value(0));
        mockMvc.perform(delete("/api/v1/issues/{id}/poll/ballot", issue.id).header("satoken", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantCount").value(0));

        mockMvc.perform(delete("/api/v1/issues/{id}/poll", issue.id).header("satoken", otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/issues/{id}/poll", issue.id).header("satoken", authorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/issues/{id}/poll", issue.id).header("satoken", staffToken))
                .andExpect(status().isNoContent());

        String singleChoice = mockMvc.perform(post("/api/v1/issues/{id}/poll", issue.id)
                        .header("satoken", staffToken).contentType(APPLICATION_JSON)
                        .content("{\"question\":\"只选一个\",\"multipleChoice\":false,\"options\":[\"A\",\"B\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode singleOptions = objectMapper.readTree(singleChoice).get("options");
        mockMvc.perform(put("/api/v1/issues/{id}/poll/ballot", issue.id)
                        .header("satoken", otherToken).contentType(APPLICATION_JSON)
                        .content("{\"optionIds\":[" + singleOptions.get(0).get("id").asLong() + "," + singleOptions.get(1).get("id").asLong() + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issueCreationCanAtomicallyIncludePoll() throws Exception {
        String request = """
                {"title":"投票入口测试","body":"创建反馈时同时创建投票。","tag":"反馈","category":"其他",
                 "poll":{"question":"选择处理方式","multipleChoice":false,"options":["方案 A","方案 B"]}}
                """;
        mockMvc.perform(post("/api/v1/issues")
                        .header("satoken", createSession(author))
                        .header("Idempotency-Key", "user-create-with-poll")
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());

        String staffToken = createSession(staff);
        String response = mockMvc.perform(post("/api/v1/issues")
                        .header("satoken", staffToken)
                        .header("Idempotency-Key", "create-with-poll")
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long issueId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/v1/issues/{id}/poll", issueId).header("satoken", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId").value(staff.id))
                .andExpect(jsonPath("$.question").value("选择处理方式"))
                .andExpect(jsonPath("$.multipleChoice").value(false))
                .andExpect(jsonPath("$.options.length()").value(2));

        mockMvc.perform(post("/api/v1/issues")
                        .header("satoken", staffToken)
                        .header("Idempotency-Key", "create-with-poll")
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(issueId));
    }

    @Test
    void subIssuesAreHierarchicalAuthorScopedAndIdempotent() throws Exception {
        String authorToken = createSession(author);
        mockMvc.perform(get("/api/v1/issues/{id}/sub-issues", issue.id).header("satoken", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        String otherToken = createSession(other);
        mockMvc.perform(post("/api/v1/issues/{id}/sub-issues", issue.id)
                        .header("satoken", otherToken).contentType(APPLICATION_JSON)
                        .content("{\"title\":\"越权子议题\",\"body\":\"不应创建\"}"))
                .andExpect(status().isForbidden());

        String request = "{\"title\":\"拆分后的问题\",\"body\":\"这是父议题中的独立问题。\"}";
        String response = mockMvc.perform(post("/api/v1/issues/{id}/sub-issues", issue.id)
                        .header("satoken", authorToken).header("Idempotency-Key", "sub-issue-create-1")
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentIssueId").value(issue.id))
                .andReturn().getResponse().getContentAsString();
        long childId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/v1/issues/{id}/sub-issues", issue.id)
                        .header("satoken", authorToken).header("Idempotency-Key", "sub-issue-create-1")
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(childId));
        mockMvc.perform(get("/api/v1/issues/{id}/sub-issues", issue.id).header("satoken", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(childId));
        mockMvc.perform(get("/api/v1/issues/{id}", childId).header("satoken", authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentIssueId").value(issue.id))
                .andExpect(jsonPath("$.parentIssueTitle").value(issue.title));
    }

    private org.springframework.test.web.servlet.ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header("satoken", token));
    }

    private String createSession(User user) {
        return StpUtil.getStpLogic().createLoginSession(user.id);
    }

    private User saveUser(String username, User.Role role, boolean enabled) {
        User user = new User();
        user.username = username;
        user.email = username + "@example.test";
        user.passwordHash = passwordEncoder.encode("integration-password");
        user.role = role;
        user.enabled = enabled;
        return users.saveAndFlush(user);
    }
}
