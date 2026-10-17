package com.simpissuehub.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TuiCommandParserTest {
    private final TuiCommandParser parser = new TuiCommandParser();

    @Test
    void parsesCaseInsensitiveCommandAndQuotedArguments() {
        TuiCommand command = parser.parse("issue set 42 state 'closed now'");
        assertTrue(command.is("ISSUE"));
        assertEquals(java.util.List.of("set", "42", "state", "closed now"), command.arguments());
    }

    @Test
    void parsesEscapedWhitespaceAndQuotes() {
        TuiCommand command = parser.parse("AUTH ADD Steve steve@example.com p\\ ass");
        assertEquals(java.util.List.of("ADD", "Steve", "steve@example.com", "p ass"), command.arguments());
        assertEquals("AUTH ADD Steve steve@example.com p\\ ass", command.raw());
    }

    @Test
    void reportsUnclosedQuoteWithLineNumber() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("TAG ADD 'bug", 7));
        assertTrue(error.getMessage().contains("第 7 行"));
    }

    @Test
    void blankInputProducesEmptyCommand() {
        assertEquals("", parser.parse("   ").name());
        assertTrue(parser.parse(null).arguments().isEmpty());
    }
}
