package com.simpissuehub.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析议题搜索框中的过滤条件。
 *
 * <p>支持在自由文本中混写以下前缀式过滤条件：</p>
 * <ul>
 *   <li>{@code author:xxx} —— 作者用户名；特殊值 {@code me} 表示当前登录用户。</li>
 *   <li>{@code tag:xxx} —— 标签名称。</li>
 *   <li>{@code category:xxx} —— 所属服务器名称。</li>
 * </ul>
 *
 * <p>其余词元作为自由文本搜索词，按空格拼接后交给数据库模糊匹配。
 * 同一前缀出现多次时以最后一个为准。</p>
 */
public record IssueFilterQuery(String query, String tag, String category, String author) {
    private static final Pattern PREFIX = Pattern.compile("^(author|tag|category):(.*)$", Pattern.CASE_INSENSITIVE);

    public static IssueFilterQuery parse(String raw, String currentUsername) {
        if (raw == null || raw.isBlank()) return new IssueFilterQuery(null, null, null, null);
        List<String> terms = new ArrayList<>();
        String tag = null;
        String category = null;
        String author = null;
        for (String token : raw.trim().split("\\s+")) {
            if (token.isBlank()) continue;
            Matcher matcher = PREFIX.matcher(token);
            if (!matcher.matches()) {
                terms.add(token);
                continue;
            }
            String value = matcher.group(2).trim();
            if (value.isEmpty()) continue;
            switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
                case "author" -> author = "me".equalsIgnoreCase(value) && currentUsername != null ? currentUsername : value;
                case "tag" -> tag = value;
                case "category" -> category = value;
                default -> terms.add(token);
            }
        }
        String query = terms.isEmpty() ? null : String.join(" ", terms);
        return new IssueFilterQuery(query, tag, category, author);
    }
}
