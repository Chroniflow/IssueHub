package com.simpissuehub.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.tui.enabled", havingValue = "true")
public class TuiCommandRunner implements CommandLineRunner {
    private final TuiCommandParser parser;
    private final TuiCommandExecutor executor;

    public TuiCommandRunner(TuiCommandParser parser, TuiCommandExecutor executor) {
        this.parser = parser;
        this.executor = executor;
    }

    @Override
    public void run(String... args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        output.println("SimpIssueHub TUI 已连接。请先 AUTH LOGIN，输入 HELP 查看命令，EXIT 退出。");
        output.print("TUI> ");
        output.flush();
        String line;
        int lineNumber = 0;
        int lastExitCode = 0;
        while ((line = input.readLine()) != null) {
            lineNumber++;
            try {
                TuiCommandResult result = executor.execute(parser.parse(line, lineNumber));
                lastExitCode = result.exitCode();
                if (!result.output().isBlank()) output.println(result.output());
                if (result.terminate()) break;
            } catch (IllegalArgumentException exception) {
                lastExitCode = 2;
                output.println("错误：" + exception.getMessage());
            }
            output.print("TUI> ");
            output.flush();
        }
        if (line == null) output.println("TUI 标准输入已关闭。");
        if (lastExitCode != 0) output.println("TUI 结束，退出码：" + lastExitCode);
    }
}
