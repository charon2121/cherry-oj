package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.ProblemApiException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
final class ProblemCursorCodec {

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private final ObjectMapper objectMapper;

    ProblemCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String encode(String sort, String key, String id, String filterFingerprint) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(new CursorPayload(sort, key, id, filterFingerprint));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        }
        catch (Exception error) {
            throw new IllegalStateException("Could not encode problem cursor", error);
        }
    }

    CursorPosition decode(String cursor, String expectedSort, String expectedFilterFingerprint) {
        try {
            if (cursor == null) {
                return null;
            }
            if (cursor.isBlank() || cursor.length() > 2048) {
                throw invalid();
            }
            byte[] json = Base64.getUrlDecoder().decode(cursor.getBytes(StandardCharsets.US_ASCII));
            CursorPayload payload = objectMapper.readValue(json, CursorPayload.class);
            if (!expectedSort.equals(payload.sort())
                    || !expectedFilterFingerprint.equals(payload.filterFingerprint())
                    || payload.key() == null || payload.key().isBlank() || payload.key().length() > 512) {
                throw invalid();
            }
            UUID.fromString(payload.id());
            if (!"TITLE_ASC".equals(payload.sort())) {
                LocalDateTime.parse(payload.key(), DATE_TIME);
            }
            return new CursorPosition(payload.key(), payload.id());
        }
        catch (ProblemApiException error) {
            throw error;
        }
        catch (Exception error) {
            throw invalid();
        }
    }

    private static ProblemApiException invalid() {
        return new ProblemApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "题库游标无效或已与筛选条件失配。");
    }

    record CursorPosition(String key, String id) {
    }

    private record CursorPayload(String sort, String key, String id, String filterFingerprint) {
    }
}
