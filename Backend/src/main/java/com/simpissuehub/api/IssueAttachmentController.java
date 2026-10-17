package com.simpissuehub.api;

import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.AuthorizationService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.IssueAttachmentService;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.IssueAttachment;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueAttachmentRepository;
import com.simpissuehub.repository.IssueRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/issues/{issueId}/attachments")
public class IssueAttachmentController {
    private final IssueRepository issues;
    private final IssueAttachmentRepository attachments;
    private final IssueAttachmentService storage;
    private final CurrentUserService identity;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public IssueAttachmentController(IssueRepository issues, IssueAttachmentRepository attachments,
                                     IssueAttachmentService storage, CurrentUserService identity,
                                     AuthorizationService authorization, AuditService audit) {
        this.issues = issues; this.attachments = attachments; this.storage = storage; this.identity = identity;
        this.authorization = authorization; this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AttachmentView> list(@PathVariable Long issueId) {
        findIssue(issueId);
        return attachments.findByIssue_IdOrderByCreatedAtAscIdAsc(issueId).stream().map(AttachmentView::of).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AttachmentView upload(@PathVariable Long issueId, @RequestPart("file") MultipartFile file) {
        Issue issue = findIssue(issueId);
        User user = identity.require();
        IssueAttachment attachment = storage.store(issue, user, file);
        audit.record(user, "ISSUE_ATTACHMENT_UPLOADED", "ISSUE_ATTACHMENT", attachment.id, "上传工单附件");
        return AttachmentView.of(attachment);
    }

    @GetMapping("/{attachmentId}/content")
    @Transactional(readOnly = true)
    public ResponseEntity<FileSystemResource> content(@PathVariable Long issueId, @PathVariable Long attachmentId) {
        IssueAttachment attachment = findAttachment(issueId, attachmentId);
        var path = storage.path(attachment.storageKey);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件文件不存在");
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(attachment.contentType); }
        catch (IllegalArgumentException ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        boolean inline = attachment.contentType.startsWith("image/") || attachment.contentType.startsWith("video/") || "application/pdf".equals(attachment.contentType);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(attachment.originalName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(mediaType).contentLength(attachment.sizeBytes)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long issueId, @PathVariable Long attachmentId) {
        IssueAttachment attachment = findAttachment(issueId, attachmentId);
        User user = identity.require();
        boolean uploader = attachment.uploader.id.equals(user.id);
        if (!uploader && !authorization.canManageIssue(attachment.issue, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有上传者、工单作者或工作人员可以删除附件");
        }
        storage.delete(attachment);
        audit.record(user, "ISSUE_ATTACHMENT_DELETED", "ISSUE_ATTACHMENT", attachmentId, "删除工单附件");
    }

    private Issue findIssue(Long issueId) {
        return issues.findById(issueId).filter(issue -> !issue.deleted)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在"));
    }

    private IssueAttachment findAttachment(Long issueId, Long attachmentId) {
        IssueAttachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
        if (!attachment.issue.id.equals(issueId) || attachment.issue.deleted) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        return attachment;
    }

    public record AttachmentView(Long id, String name, String contentType, long sizeBytes, String uploader,
                                 Long uploaderId, Instant createdAt, String contentUrl) {
        static AttachmentView of(IssueAttachment attachment) {
            return new AttachmentView(attachment.id, attachment.originalName, attachment.contentType,
                    attachment.sizeBytes, attachment.uploader.username, attachment.uploader.id, attachment.createdAt,
                    "/issues/" + attachment.issue.id + "/attachments/" + attachment.id + "/content");
        }
    }
}
