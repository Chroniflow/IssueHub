package com.simpissuehub.cli;

import java.util.List;

public record TuiCommand(String name, List<String> arguments, String raw) {
    public boolean is(String value) { return name.equalsIgnoreCase(value); }
}
