package com.simpissuehub.cli;

public record TuiCommandResult(int exitCode, String output, boolean terminate) {
    public static TuiCommandResult ok(String output) { return new TuiCommandResult(0, output, false); }
    public static TuiCommandResult error(String output) { return new TuiCommandResult(2, output, false); }
    public static TuiCommandResult exit() { return new TuiCommandResult(0, "再见。", true); }
}
