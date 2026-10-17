package com.simpissuehub.api;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

/** Stable pagination envelope independent of Spring Data's JSON representation. */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
