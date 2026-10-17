package com.simpissuehub.application.mail;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FlagTerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class SmtpImapMailGateway implements MailGateway {
    private final JavaMailSender sender;
    private final String from;
    private final String imapHost;
    private final int imapPort;
    private final String imapUsername;
    private final String imapPassword;
    private final String imapProtocol;
    private final boolean imapSsl;

    public SmtpImapMailGateway(JavaMailSender sender,
                               @Value("${app.mail.from:no-reply@localhost}") String from,
                               @Value("${app.mail.imap.host:localhost}") String imapHost,
                               @Value("${app.mail.imap.port:143}") int imapPort,
                               @Value("${app.mail.imap.username:}") String imapUsername,
                               @Value("${app.mail.imap.password:}") String imapPassword,
                               @Value("${app.mail.imap.protocol:imap}") String imapProtocol,
                               @Value("${app.mail.imap.ssl:false}") boolean imapSsl) {
        this.sender = sender; this.from = from; this.imapHost = imapHost; this.imapPort = imapPort; this.imapUsername = imapUsername; this.imapPassword = imapPassword; this.imapProtocol = imapProtocol; this.imapSsl = imapSsl;
    }

    @Override
    public void send(String recipient, String subject, String text, String html) {
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, java.nio.charset.StandardCharsets.UTF_8.name());
            helper.setFrom(from); helper.setTo(recipient); helper.setSubject(subject); helper.setText(text, html);
            sender.send(message);
        } catch (jakarta.mail.MessagingException exception) {
            throw new IllegalStateException("SMTP 邮件构建失败", exception);
        }
    }

    @Override
    public List<IncomingMail> fetchUnread() {
        if (imapUsername.isBlank() || imapPassword.isBlank()) return List.of();
        Properties properties = new Properties();
        properties.put("mail." + imapProtocol + ".host", imapHost);
        properties.put("mail." + imapProtocol + ".port", Integer.toString(imapPort));
        properties.put("mail." + imapProtocol + ".ssl.enable", Boolean.toString(imapSsl));
        List<IncomingMail> result = new ArrayList<>();
        try (Store store = Session.getInstance(properties).getStore(imapProtocol)) {
            store.connect(imapHost, imapPort, imapUsername, imapPassword);
            try (Folder inbox = store.getFolder("INBOX")) {
                inbox.open(Folder.READ_WRITE);
                for (Message message : inbox.search(new FlagTerm(new jakarta.mail.Flags(jakarta.mail.Flags.Flag.SEEN), false))) {
                    String[] messageIds = message.getHeader("Message-ID");
                    String[] inReplyTo = message.getHeader("In-Reply-To");
                    ParsedContent content = extractContent(message.getContent(), false);
                    result.add(new IncomingMail(messageIds == null || messageIds.length == 0 ? "" : messageIds[0], message.getFrom() == null || message.getFrom().length == 0 ? "" : message.getFrom()[0].toString(), message.getSubject(), inReplyTo == null || inReplyTo.length == 0 ? "" : inReplyTo[0], content.text(), content.attachmentBytes()));
                    message.setFlag(jakarta.mail.Flags.Flag.SEEN, true);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("IMAP 收信失败", exception);
        }
        return result;
    }

    private ParsedContent extractContent(Object content, boolean attachment) throws Exception {
        if (content instanceof String text) return new ParsedContent(attachment ? "" : text, attachment ? text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length : 0);
        if (content instanceof byte[] bytes) return new ParsedContent("", attachment ? bytes.length : 0);
        if (content instanceof java.io.InputStream stream) {
            long bytes = 0; byte[] buffer = new byte[8192]; int read;
            while ((read = stream.read(buffer)) != -1) bytes += read;
            return new ParsedContent("", attachment ? bytes : 0);
        }
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder(); long attachmentBytes = 0;
            for (int index = 0; index < multipart.getCount(); index++) {
                jakarta.mail.BodyPart part = multipart.getBodyPart(index);
                boolean partAttachment = part.getDisposition() != null && jakarta.mail.Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null;
                ParsedContent nested = extractContent(part.getContent(), partAttachment);
                if (!partAttachment && text.isEmpty() && !nested.text().isBlank()) text.append(nested.text());
                attachmentBytes += nested.attachmentBytes();
                if (partAttachment && nested.attachmentBytes() == 0 && part.getSize() > 0) attachmentBytes += part.getSize();
            }
            return new ParsedContent(text.toString(), attachmentBytes);
        }
        return new ParsedContent("", 0);
    }

    private record ParsedContent(String text, long attachmentBytes) {}
}
