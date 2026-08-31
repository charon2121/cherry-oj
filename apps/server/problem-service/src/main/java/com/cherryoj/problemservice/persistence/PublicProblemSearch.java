package com.cherryoj.problemservice.persistence;

import java.util.List;

public record PublicProblemSearch(
        String q,
        String difficulty,
        List<String> tags,
        String codeMode,
        String language,
        String sort,
        String cursorKey,
        String cursorId,
        int limit) {
}
