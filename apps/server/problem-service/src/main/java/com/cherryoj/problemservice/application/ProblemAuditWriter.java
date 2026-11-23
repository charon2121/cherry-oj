package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class ProblemAuditWriter {
    private final AdminProblemMapper problems;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;

    ProblemAuditWriter(AdminProblemMapper problems, UuidV7 ids, Clock clock, ObjectMapper json) {
        this.problems = problems;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
    }

    void append(String problemId, String actorUserId, String action, Map<String, Object> detail) {
        problems.insertAudit(ids.next().toString(), problemId, actorUserId, action, null, writeJson(detail), now());
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize publication audit", error);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
