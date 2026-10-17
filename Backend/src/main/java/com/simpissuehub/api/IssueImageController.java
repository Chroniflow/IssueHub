package com.simpissuehub.api;

import com.simpissuehub.application.image.IssueImageRenderer;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.repository.IssueCommentRepository;
import com.simpissuehub.repository.IssueRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/issues")
public class IssueImageController {
    private final IssueRepository issues;
    private final IssueCommentRepository comments;
    private final IssueImageRenderer renderer;

    public IssueImageController(IssueRepository issues, IssueCommentRepository comments, IssueImageRenderer renderer) {
        this.issues = issues;
        this.comments = comments;
        this.renderer = renderer;
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @RequestParam(defaultValue = "png") String format) {
        IssueImageRenderer.Format imageFormat;
        try {
            imageFormat = IssueImageRenderer.Format.parse(format);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
        Issue issue = issues.findById(id).filter(value -> !value.deleted)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "工单不存在"));
        var commentViews = comments.findByIssueIdAndDeletedFalseOrderByCreatedAtAscIdAsc(id).stream()
                .map(comment -> new IssueImageRenderer.CommentView(comment.author.username, comment.body, comment.createdAt))
                .toList();
        var view = new IssueImageRenderer.IssueImageView(issue.id, issue.title, issue.state.name(), issue.category,
                issue.tags.stream().map(tag -> tag.name).toList(), issue.body, commentViews, issue.author.username, issue.updatedAt);
        try {
            byte[] body = renderer.encode(view, imageFormat);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(imageFormat.mediaType()))
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=issue-" + id + "." + imageFormat.writerName())
                    .body(body);
        } catch (IOException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "图片生成失败", exception);
        }
    }
}
