package com.simpissuehub.api;

import com.simpissuehub.application.image.IssueImageRenderer;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.IssueComment;
import com.simpissuehub.domain.Tag;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueCommentRepository;
import com.simpissuehub.repository.IssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IssueImageControllerTest {
    @Mock private IssueRepository issues;
    @Mock private IssueCommentRepository comments;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IssueImageController(issues, comments, new IssueImageRenderer())).build();
    }

    @Test
    void returnsPngByDefaultAndJpegWhenRequested() throws Exception {
        when(issues.findById(42L)).thenReturn(Optional.of(issue()));
        when(comments.findByIssueIdAndDeletedFalseOrderByCreatedAtAscIdAsc(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/issues/42/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/api/v1/issues/42/image").param("format", "jpeg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void rejectsUnknownFormatAndMissingIssue() throws Exception {
        mockMvc.perform(get("/api/v1/issues/42/image").param("format", "gif"))
                .andExpect(status().isBadRequest());

        when(issues.findById(404L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/issues/404/image"))
                .andExpect(status().isNotFound());
    }

    private Issue issue() {
        var issue = new Issue();
        issue.id = 42L;
        issue.title = "测试反馈";
        issue.body = "测试正文";
        issue.category = "建议";
        issue.state = Issue.State.OPEN;
        issue.author = new User();
        issue.author.username = "Alex";
        issue.tags.add(tag("地图"));
        issue.updatedAt = Instant.parse("2026-09-12T10:15:30Z");
        return issue;
    }

    private Tag tag(String name) {
        var tag = new Tag();
        tag.name = name;
        return tag;
    }
}
