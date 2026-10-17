package com.simpissuehub.application.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IssueImageRendererTest {
    private final IssueImageRenderer renderer = new IssueImageRenderer();

    @Test
    void rendersFixedSizeAndEncodesPngAndJpeg() throws Exception {
        var view = new IssueImageRenderer.IssueImageView(142L, "建议增加夜间模式的地图标记", "OPEN", "建议", List.of("地图", "UI"),
                "希望在夜间探索时可以更容易区分已探索区域。", List.of(), "Alex", Instant.parse("2026-09-12T10:15:30Z"));

        BufferedImage image = renderer.render(view);

        assertEquals(1200, image.getWidth());
        assertEquals(630, image.getHeight());
        byte[] png = renderer.encode(view, IssueImageRenderer.Format.PNG);
        byte[] jpeg = renderer.encode(view, IssueImageRenderer.Format.JPEG);
        assertEquals(1200, ImageIO.read(new ByteArrayInputStream(png)).getWidth());
        assertEquals(1200, ImageIO.read(new ByteArrayInputStream(jpeg)).getWidth());
    }

    @Test
    void presentsFirstLastAndCollapsedMiddleComments() {
        var comments = List.of(
                new IssueImageRenderer.CommentView("Alex", "第一条评论", Instant.parse("2026-09-10T10:00:00Z")),
                new IssueImageRenderer.CommentView("Mori", "中间评论一", Instant.parse("2026-09-10T11:00:00Z")),
                new IssueImageRenderer.CommentView("Staff", "中间评论二", Instant.parse("2026-09-10T12:00:00Z")),
                new IssueImageRenderer.CommentView("Alex", "最后一条评论", Instant.parse("2026-09-10T13:00:00Z"))
        );

        var presentation = IssueImageRenderer.commentPresentation(comments);

        assertEquals(List.of(
                new IssueImageRenderer.CommentLine("Alex", "第一条评论", false),
                new IssueImageRenderer.CommentLine("", "折叠了2条评论", true),
                new IssueImageRenderer.CommentLine("Alex", "最后一条评论", false)
        ), presentation);
    }

    @Test
    void presentsSingleCommentWithoutCollapse() {
        var comments = List.of(new IssueImageRenderer.CommentView("Alex", "唯一评论", Instant.now()));

        assertEquals(List.of(new IssueImageRenderer.CommentLine("Alex", "唯一评论", false)), IssueImageRenderer.commentPresentation(comments));
    }

    @Test
    void treatsIssueBodyAsTimelineItemZeroAndCollapsesEarlierComments() {
        var view = new IssueImageRenderer.IssueImageView(12L, "标题", "OPEN", "建议", List.of(), "Issue 正文",
                List.of(new IssueImageRenderer.CommentView("Mori", "中间评论", Instant.now()),
                        new IssueImageRenderer.CommentView("Staff", "最后评论", Instant.now())), "Alex", Instant.now());

        assertEquals(List.of(
                new IssueImageRenderer.CommentLine("Alex", "Issue 正文", false),
                new IssueImageRenderer.CommentLine("", "折叠了1条评论", true),
                new IssueImageRenderer.CommentLine("Staff", "最后评论", false)
        ), IssueImageRenderer.timelinePresentation(view));
    }

    @Test
    void selectsAFontThatCanDisplayChineseGlyphs() {
        assertTrue(IssueImageRenderer.baseFontCanDisplay("中文反馈"));
    }
}
