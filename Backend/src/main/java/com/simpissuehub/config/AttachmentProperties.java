package com.simpissuehub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "app.attachments")
public class AttachmentProperties {
    private String directory = "./data/attachments";
    private long maxBytes = 10 * 1024 * 1024;
    private Set<String> allowedExtensions = new LinkedHashSet<>(Set.of("png", "jpg", "jpeg", "gif", "webp", "mp4", "webm", "zip", "pdf", "txt", "log"));

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    public Set<String> getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(Set<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
}
