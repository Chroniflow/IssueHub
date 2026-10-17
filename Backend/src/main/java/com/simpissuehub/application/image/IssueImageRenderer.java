package com.simpissuehub.application.image;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.awt.FontFormatException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders a non-interactive snapshot of the public Issue detail page. */
@Component
public final class IssueImageRenderer {
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 630;
    private static final Font BASE_FONT = loadBaseFont();
    private static final Color PAGE = new Color(246, 248, 250);
    private static final Color CARD = Color.WHITE;
    private static final Color BORDER = new Color(208, 215, 222);
    private static final Color BORDER_MUTED = new Color(234, 238, 242);
    private static final Color MUTED = new Color(87, 96, 106);
    private static final Color TEXT = new Color(31, 35, 40);
    private static final Color ACCENT = new Color(26, 127, 90);
    private static final Color OPEN = new Color(154, 103, 0);
    private static final Color CLOSED = new Color(26, 127, 90);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public BufferedImage render(IssueImageView view) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(PAGE); g.fillRect(0, 0, WIDTH, HEIGHT);

            int cardX = 42, cardY = 28, cardWidth = WIDTH - 84, cardHeight = HEIGHT - 56;
            g.setColor(CARD); g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 12, 12);
            g.setColor(BORDER); g.setStroke(new BasicStroke(1f)); g.drawRoundRect(cardX, cardY, cardWidth, cardHeight, 12, 12);

            int left = cardX + 34, right = cardX + cardWidth - 34, y = cardY + 35;
            drawGlobalHeader(g, left, right, cardY);
            y = cardY + 86;
            drawRepositoryHeader(g, left, right, y);
            y += 47;
            drawRepositoryNav(g, left, right, y);
            y += 45;
            g.setFont(font(Font.PLAIN, 13)); g.setColor(ACCENT); g.drawString("SimpIssueHub / 反馈", left, y);
            y += 32;
            drawButton(g, "新建反馈", right - 100, y - 28, 100, 30, true);
            g.setColor(TEXT); g.setFont(font(Font.BOLD, 31));
            y = drawTextBlock(g, view.title(), left, y, right - left - 125, 2, 36, true) + 10;
            g.setColor(MUTED); g.setFont(font(Font.PLAIN, 13));
            g.drawString(view.author() + " 提交了这条反馈 · 更新于 " + formatDate(view.updatedAt()), left, y + 12);
            y += 31;
            y = drawMetadata(g, view, left, y, right - left) + 16;
            g.setColor(BORDER_MUTED); g.fillRect(left, y, right - left, 1); y += 18;

            int sidebarWidth = 210, mainRight = right - sidebarWidth - 28;
            drawTimeline(g, view, left, y, mainRight - left);
            drawSidebar(g, view, mainRight + 28, y, sidebarWidth);
            g.setFont(font(Font.PLAIN, 12)); g.setColor(MUTED);
            drawRight(g, "更新于 " + formatDate(view.updatedAt()), right, cardY + cardHeight - 22);
        } finally { g.dispose(); }
        return image;
    }

    public byte[] encode(IssueImageView view, Format format) throws IOException {
        BufferedImage rendered = render(view);
        BufferedImage output = format == Format.JPEG ? toRgb(rendered) : rendered;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName(format.writerName()).next();
            try {
                writer.setOutput(stream);
                if (format == Format.JPEG) {
                    ImageWriteParam params = writer.getDefaultWriteParam();
                    if (params.canWriteCompressed()) { params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); params.setCompressionQuality(0.92f); }
                    writer.write(null, new IIOImage(output, null, null), params);
                } else writer.write(output);
            } finally { writer.dispose(); }
            stream.flush(); return bytes.toByteArray();
        }
    }

    static List<CommentLine> commentPresentation(List<CommentView> comments) {
        if (comments == null || comments.isEmpty()) return List.of();
        if (comments.size() == 1) return List.of(toLine(comments.get(0)));
        List<CommentLine> result = new ArrayList<>(); result.add(toLine(comments.get(0)));
        if (comments.size() > 2) result.add(new CommentLine("", "折叠了" + (comments.size() - 2) + "条评论", true));
        result.add(toLine(comments.get(comments.size() - 1))); return List.copyOf(result);
    }

    /** The detail page treats the Issue body as timeline item 0. */
    static List<CommentLine> timelinePresentation(IssueImageView view) {
        List<CommentLine> result = new ArrayList<>(); result.add(new CommentLine(view.author(), view.body(), false));
        if (view.comments() == null || view.comments().isEmpty()) return List.copyOf(result);
        if (view.comments().size() > 1) result.add(new CommentLine("", "折叠了" + (view.comments().size() - 1) + "条评论", true));
        CommentView last = view.comments().get(view.comments().size() - 1);
        result.add(new CommentLine(last.author(), last.body(), false)); return List.copyOf(result);
    }

    static String fontFamily() { return BASE_FONT.getFamily(); }

    static boolean baseFontCanDisplay(String text) { return BASE_FONT.canDisplayUpTo(text) < 0; }

    private void drawGlobalHeader(Graphics2D g, int left, int right, int top) {
        g.setColor(CARD); g.fillRect(left - 34, top, right - left + 68, 42);
        g.setColor(ACCENT); g.fillRoundRect(left, top + 7, 27, 27, 6, 6);
        g.setColor(Color.WHITE); g.setFont(font(Font.BOLD, 17)); g.drawString("S", left + 8, top + 27);
        g.setColor(TEXT); g.setFont(font(Font.BOLD, 14)); g.drawString("SimpMC 玩家社区", left + 38, top + 25);
        drawButton(g, "登录", right - 52, top + 6, 52, 28, false);
        g.setColor(BORDER_MUTED); g.fillRect(left - 34, top + 41, right - left + 68, 1);
    }

    private void drawRepositoryHeader(Graphics2D g, int left, int right, int y) {
        g.setColor(TEXT); g.setFont(font(Font.PLAIN, 14)); g.drawString("◇  SimpMC", left, y + 15);
        g.setColor(MUTED); g.drawString("/", left + 92, y + 15);
        g.setColor(TEXT); g.setFont(font(Font.BOLD, 15)); g.drawString("SimpIssueHub", left + 110, y + 15);
        g.setColor(MUTED); g.setFont(font(Font.PLAIN, 12)); g.drawString("公开", left + 234, y + 15);
        g.drawString("Minecraft 服务器玩家反馈与问题跟踪", left, y + 36);
    }

    private void drawRepositoryNav(Graphics2D g, int left, int right, int y) {
        g.setColor(BORDER_MUTED); g.fillRect(left - 34, y + 35, right - left + 68, 1);
        g.setFont(font(Font.BOLD, 13));
        drawNavItem(g, "◉ 反馈", left, y, true);
        drawNavItem(g, "◇ 标签", left + 90, y, false);
        drawNavItem(g, "ⓘ 关于", left + 174, y, false);
    }

    private void drawNavItem(Graphics2D g, String text, int x, int y, boolean active) {
        g.setColor(active ? ACCENT : MUTED); g.drawString(text, x, y + 22);
        if (active) { g.setColor(ACCENT); g.fillRect(x, y + 31, 53, 2); }
    }

    private int drawMetadata(Graphics2D g, IssueImageView view, int x, int y, int width) {
        int cursor = x; g.setFont(font(Font.BOLD, 13)); boolean closed = "CLOSED".equalsIgnoreCase(view.state());
        cursor = drawChip(g, closed ? "✓ 已解决" : "◉ 处理中", cursor, y - 17, closed ? CLOSED : OPEN, true);
        cursor += 8; cursor = drawChip(g, view.category(), cursor, y - 17, ACCENT, false);
        for (String tag : view.tags()) { if (cursor + 100 > x + width) { drawChip(g, "…", cursor + 8, y - 17, MUTED, false); break; } cursor += 8; cursor = drawChip(g, tag, cursor, y - 17, MUTED, false); }
        return y + 17;
    }

    private void drawTimeline(Graphics2D g, IssueImageView view, int x, int y, int width) {
        int cursor = y;
        for (int index = 0; index < timelinePresentation(view).size(); index++) {
            CommentLine line = timelinePresentation(view).get(index);
            if (line.collapsed()) { g.setColor(MUTED); g.setFont(font(Font.PLAIN, 14)); g.drawString(line.body(), x + 18, cursor + 20); cursor += 36; continue; }
            drawAvatar(g, line.author(), x, cursor + 1);
            g.setColor(CARD); g.fillRoundRect(x + 54, cursor, width - 54, 92, 7, 7);
            g.setColor(BORDER); g.drawRoundRect(x + 54, cursor, width - 54, 92, 7, 7);
            g.setColor(new Color(246, 248, 250)); g.fillRoundRect(x + 55, cursor + 1, width - 56, 31, 6, 6);
            g.setFont(font(Font.BOLD, 13)); g.setColor(TEXT); g.drawString(line.author(), x + 68, cursor + 21);
            g.setFont(font(Font.PLAIN, 12)); g.setColor(MUTED); g.drawString(index == 0 ? "提交反馈" : "评论", x + 68 + g.getFontMetrics().stringWidth(line.author()) + 10, cursor + 21);
            g.setFont(font(Font.PLAIN, 15)); g.setColor(TEXT); drawTextBlock(g, line.body(), x + 68, cursor + 57, width - 82, 2, 20, true); cursor += 108;
        }
    }

    private void drawSidebar(Graphics2D g, IssueImageView view, int x, int y, int width) {
        g.setFont(font(Font.BOLD, 13)); g.setColor(MUTED); g.drawString("属于的服务器", x, y + 12);
        g.setFont(font(Font.PLAIN, 14)); g.setColor(TEXT); drawTextBlock(g, view.category(), x, y + 37, width, 1, 20, true);
        g.setFont(font(Font.BOLD, 13)); g.setColor(MUTED); g.drawString("标签", x, y + 65);
        int tagY = y + 84;
        int visibleTags = Math.min(view.tags().size(), 2);
        for (int index = 0; index < visibleTags; index++) { String tag = view.tags().get(index); g.setFont(font(Font.PLAIN, 13)); g.setColor(ACCENT); drawTextBlock(g, "#" + tag, x, tagY, width, 1, 19, true); tagY += 22; }
        if (view.tags().size() > visibleTags) { g.setFont(font(Font.PLAIN, 13)); g.setColor(MUTED); g.drawString("…", x, tagY); tagY += 22; }
        if (view.tags().isEmpty()) { g.setFont(font(Font.PLAIN, 13)); g.setColor(MUTED); g.drawString("暂无标签", x, tagY); }
        drawButton(g, "添加标签", x, tagY + 8, width, 27, false);
        g.setFont(font(Font.BOLD, 13)); g.setColor(MUTED); g.drawString("通知", x, tagY + 58);
        drawButton(g, "◉ 订阅", x, tagY + 67, width, 27, false);
        g.setFont(font(Font.BOLD, 13)); g.drawString("关联工单", x, tagY + 111);
        g.setFont(font(Font.PLAIN, 12)); g.drawString("暂无关联工单", x, tagY + 130);
        g.setFont(font(Font.BOLD, 13)); g.drawString("操作", x, tagY + 165);
        drawButton(g, "关闭反馈", x, tagY + 175, width, 27, false);
    }

    private void drawAvatar(Graphics2D g, String author, int x, int y) {
        g.setColor(new Color(221, 244, 234)); g.fillOval(x, y, 42, 42); g.setColor(ACCENT); g.setFont(font(Font.BOLD, 16));
        String initial = author == null || author.isBlank() ? "?" : author.substring(0, 1).toUpperCase(Locale.ROOT);
        g.drawString(initial, x + (42 - g.getFontMetrics().stringWidth(initial)) / 2, y + 27);
    }

    private int drawChip(Graphics2D g, String text, int x, int y, Color color, boolean filled) {
        String value = text == null || text.isBlank() ? "未指定服务器" : text; int width = g.getFontMetrics().stringWidth(value) + 20;
        g.setColor(filled ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 28) : BORDER_MUTED); g.fillRoundRect(x, y, width, 25, 8, 8);
        g.setColor(color); g.drawString(value, x + 10, y + 17); return x + width;
    }

    private int drawTextBlock(Graphics2D g, String text, int x, int y, int width, int maxLines, int lineHeight, boolean ellipsis) {
        List<String> lines = wrap(text, g.getFontMetrics(), width, maxLines, ellipsis); for (String line : lines) { g.drawString(line, x, y); y += lineHeight; }
        return y - (lines.isEmpty() ? 0 : lineHeight);
    }

    private List<String> wrap(String text, FontMetrics metrics, int width, int maxLines, boolean ellipsis) {
        String value = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim(); if (value.isEmpty()) return List.of(" ");
        List<String> lines = new ArrayList<>(); StringBuilder current = new StringBuilder(); boolean truncated = false;
        for (int index = 0; index < value.length(); index++) {
            current.append(value.charAt(index));
            if (metrics.stringWidth(current.toString()) > width) {
                current.deleteCharAt(current.length() - 1); if (current.isEmpty()) current.append(value.charAt(index)); lines.add(current.toString()); current = new StringBuilder();
                if (lines.size() == maxLines) { truncated = index + 1 < value.length(); break; }
            }
        }
        if (lines.size() < maxLines && current.length() > 0) lines.add(current.toString());
        if (ellipsis && truncated && !lines.isEmpty()) { String last = lines.get(lines.size() - 1); while (!last.isEmpty() && metrics.stringWidth(last + "…") > width) last = last.substring(0, last.length() - 1); lines.set(lines.size() - 1, last + "…"); }
        return lines;
    }

    private void drawButton(Graphics2D g, String text, int x, int y, int width, int height, boolean primary) {
        g.setColor(primary ? ACCENT : CARD); g.fillRoundRect(x, y, width, height, 6, 6);
        g.setColor(primary ? ACCENT : BORDER); g.drawRoundRect(x, y, width, height, 6, 6);
        g.setColor(primary ? Color.WHITE : TEXT); g.setFont(font(Font.PLAIN, 12));
        g.drawString(text, x + Math.max(8, (width - g.getFontMetrics().stringWidth(text)) / 2), y + 18);
    }

    private void drawRight(Graphics2D g, String text, int right, int baseline) { g.drawString(text, right - g.getFontMetrics().stringWidth(text), baseline); }
    private String formatDate(Instant instant) { return instant == null ? "" : DATE_FORMAT.format(instant); }
    private Font font(int style, int size) { return BASE_FONT.deriveFont(style, (float) size); }
    private static CommentLine toLine(CommentView comment) { return new CommentLine(comment.author(), comment.body(), false); }

    private BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB); Graphics2D g = rgb.createGraphics(); g.setColor(PAGE); g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight()); g.drawImage(source, 0, 0, null); g.dispose(); return rgb;
    }

    private static Font loadBaseFont() {
        String configured = System.getenv("IMAGE_FONT_FILE");
        if (configured != null && !configured.isBlank()) {
            try {
                for (Font candidate : Font.createFonts(new File(configured))) if (candidate.canDisplayUpTo("中文反馈") < 0) return candidate;
            } catch (FontFormatException | IOException exception) { throw new IllegalStateException("无法加载 IMAGE_FONT_FILE: " + configured, exception); }
        }
        try (InputStream stream = IssueImageRenderer.class.getResourceAsStream("/fonts/NotoSansCJKsc-Regular.otf")) {
            if (stream == null) throw new IllegalStateException("内置中文字体资源不存在: /fonts/NotoSansCJKsc-Regular.otf");
            Font embedded = Font.createFont(Font.TRUETYPE_FONT, stream);
            if (embedded.canDisplayUpTo("中文反馈") < 0) return embedded;
            throw new IllegalStateException("内置中文字体无法显示中文 glyph");
        } catch (FontFormatException | IOException exception) {
            throw new IllegalStateException("无法加载内置中文字体", exception);
        }
    }

    public enum Format {
        PNG("png"), JPEG("jpeg");
        private final String writerName;
        Format(String writerName) { this.writerName = writerName; }
        public String writerName() { return writerName; }
        public String mediaType() { return this == JPEG ? "image/jpeg" : "image/png"; }
        public static Format parse(String value) { return switch (value == null ? "png" : value.toLowerCase(Locale.ROOT)) { case "png" -> PNG; case "jpeg" -> JPEG; default -> throw new IllegalArgumentException("图片格式必须是 png 或 jpeg"); }; }
    }

    public record IssueImageView(Long id, String title, String state, String category, List<String> tags, String body, List<CommentView> comments, String author, Instant updatedAt) {
        public IssueImageView { tags = tags == null ? List.of() : List.copyOf(tags); comments = comments == null ? List.of() : List.copyOf(comments); }
    }
    public record CommentView(String author, String body, Instant createdAt) {}
    public record CommentLine(String author, String body, boolean collapsed) {}
}
