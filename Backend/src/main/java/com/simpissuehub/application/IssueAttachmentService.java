package com.simpissuehub.application;

import com.simpissuehub.config.AttachmentProperties;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.IssueAttachment;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueAttachmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IssueAttachmentService {
    private static final Map<String, Set<String>> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")), Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("gif", Set.of("image/gif")), Map.entry("webp", Set.of("image/webp")),
            Map.entry("mp4", Set.of("video/mp4")), Map.entry("webm", Set.of("video/webm")),
            Map.entry("zip", Set.of("application/zip")), Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("txt", Set.of("text/plain")), Map.entry("log", Set.of("text/plain"))
    );

    private final IssueAttachmentRepository attachments;
    private final AttachmentProperties properties;
    private final Path root;

    public IssueAttachmentService(IssueAttachmentRepository attachments, AttachmentProperties properties) {
        this.attachments = attachments;
        this.properties = properties;
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
    }

    @Transactional
    public IssueAttachment store(Issue issue, User uploader, MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("请选择非空文件");
        if (file.getSize() > properties.getMaxBytes()) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过允许的大小限制");
        String originalName = normalizeName(file.getOriginalFilename());
        String extension = extension(originalName);
        Set<String> allowed = properties.getAllowedExtensions().stream().map(value -> value.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "")).collect(java.util.stream.Collectors.toSet());
        if (!allowed.contains(extension) || !MIME_BY_EXTENSION.containsKey(extension)) throw badRequest("不允许上传该文件类型");

        byte[] content;
        try { content = file.getBytes(); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取上传文件"); }
        String detectedType = detectContentType(content);
        if (!MIME_BY_EXTENSION.get(extension).contains(detectedType)) throw badRequest("文件内容与扩展名不匹配");

        String storageKey = UUID.randomUUID().toString();
        Path target = path(storageKey);
        try {
            Files.createDirectories(root);
            Path temporary = Files.createTempFile(root, "upload-", ".tmp");
            try {
                Files.write(temporary, content);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "附件存储失败");
        }

        IssueAttachment attachment = new IssueAttachment();
        attachment.issue = issue; attachment.uploader = uploader; attachment.storageKey = storageKey;
        attachment.originalName = originalName; attachment.contentType = detectedType; attachment.sizeBytes = content.length;
        try {
            IssueAttachment saved = attachments.saveAndFlush(attachment);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) try { Files.deleteIfExists(target); } catch (IOException ignored) {}
                    }
                });
            }
            return saved;
        }
        catch (RuntimeException exception) { try { Files.deleteIfExists(target); } catch (IOException ignored) {} throw exception; }
    }

    public Path path(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.getParent().equals(root)) throw new IllegalArgumentException("无效的附件存储键");
        return resolved;
    }

    @Transactional
    public void delete(IssueAttachment attachment) {
        attachments.delete(attachment);
        Path target = path(attachment.storageKey);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { try { Files.deleteIfExists(target); } catch (IOException ignored) {} }
            });
        } else {
            try { Files.deleteIfExists(target); }
            catch (IOException exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "附件文件删除失败"); }
        }
    }

    static String detectContentType(byte[] bytes) {
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")) return "image/gif";
        if (ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        if (bytes.length >= 12 && ascii(bytes, 4, "ftyp")) return "video/mp4";
        if (startsWith(bytes, 0x1A, 0x45, 0xDF, 0xA3)) return "video/webm";
        if (startsWith(bytes, 0x50, 0x4B, 0x03, 0x04) || startsWith(bytes, 0x50, 0x4B, 0x05, 0x06) || startsWith(bytes, 0x50, 0x4B, 0x07, 0x08)) return "application/zip";
        if (ascii(bytes, 0, "%PDF-")) return "application/pdf";
        if (isUtf8Text(bytes)) return "text/plain";
        return "application/octet-stream";
    }

    private static boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try { StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)); return true; }
        catch (CharacterCodingException exception) { return false; }
    }
    private static boolean ascii(byte[] bytes, int offset, String value) { return bytes.length >= offset + value.length() && value.equals(new String(bytes, offset, value.length(), StandardCharsets.US_ASCII)); }
    private static boolean startsWith(byte[] bytes, int... prefix) { if (bytes.length < prefix.length) return false; for (int i = 0; i < prefix.length; i++) if ((bytes[i] & 0xff) != prefix[i]) return false; return true; }
    private static String normalizeName(String raw) { String name = raw == null ? "" : Path.of(raw.replace('\\', '/')).getFileName().toString().trim(); if (name.isEmpty() || name.length() > 255 || name.chars().anyMatch(ch -> ch < 32)) throw badRequest("文件名无效"); return name; }
    private static String extension(String name) { int index = name.lastIndexOf('.'); if (index < 1 || index == name.length() - 1) throw badRequest("文件必须包含扩展名"); return name.substring(index + 1).toLowerCase(Locale.ROOT); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
