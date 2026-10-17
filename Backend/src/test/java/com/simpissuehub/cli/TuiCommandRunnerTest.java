package com.simpissuehub.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TuiCommandRunnerTest {
    @Test
    void consumesCommandsFromStandardInput() throws Exception {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TuiCommandExecutor executor = mock(TuiCommandExecutor.class);
        when(executor.execute(any())).thenReturn(TuiCommandResult.exit());
        try {
            System.setIn(new ByteArrayInputStream("EXIT\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            new TuiCommandRunner(new TuiCommandParser(), executor).run();
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
        verify(executor).execute(any());
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("TUI> "));
        assertTrue(rendered.contains("再见。"));
    }
}
