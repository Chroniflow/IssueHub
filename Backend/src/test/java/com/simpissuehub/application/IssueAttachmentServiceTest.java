package com.simpissuehub.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.simpissuehub.config.AttachmentProperties;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueAttachmentRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class IssueAttachmentServiceTest {
    @TempDir Path temporaryDirectory;
    @Test
    void detectsSupportedTypesFromFileContent() {
        assertEquals("image/png", IssueAttachmentService.detectContentType(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
        assertEquals("image/jpeg", IssueAttachmentService.detectContentType(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01}));
        assertEquals("application/pdf", IssueAttachmentService.detectContentType("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("text/plain", IssueAttachmentService.detectContentType("server log".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void doesNotTrustUnknownOrBinaryContent() {
        assertEquals("application/octet-stream", IssueAttachmentService.detectContentType(new byte[] {0, 1, 2, 3, 4}));
    }

    @Test
    void rejectsExecutableRenamedAsPng() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setDirectory(temporaryDirectory.toString());
        IssueAttachmentService service = new IssueAttachmentService(mock(IssueAttachmentRepository.class), properties);
        MockMultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[] {0x4d, 0x5a, 0x00, 0x01});

        assertThrows(ResponseStatusException.class, () -> service.store(new Issue(), new User(), file));
    }
}
