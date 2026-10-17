package com.simpissuehub.cli;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TuiCommandParser {
    public TuiCommand parse(String line) {
        return parse(line, 1);
    }

    public TuiCommand parse(String line, int lineNumber) {
        if (line == null || line.isBlank()) return new TuiCommand("", List.of(), line == null ? "" : line);
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        char quote = 0;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (escaped) { token.append(current); escaped = false; continue; }
            if (current == '\\') { escaped = true; continue; }
            if (quoted && current == quote) { quoted = false; continue; }
            if (!quoted && (current == '\'' || current == '"')) { quoted = true; quote = current; continue; }
            if (!quoted && Character.isWhitespace(current)) {
                if (!token.isEmpty()) { tokens.add(token.toString()); token.setLength(0); }
            } else token.append(current);
        }
        if (escaped) token.append('\\');
        if (quoted) throw new IllegalArgumentException("第 " + lineNumber + " 行：引号未闭合；用法：文本包含空格时使用单引号或双引号");
        if (!token.isEmpty()) tokens.add(token.toString());
        return new TuiCommand(tokens.get(0), List.copyOf(tokens.subList(1, tokens.size())), line);
    }
}
