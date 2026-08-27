package com.cherryoj.userservice.persistence;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditEventMapper {

    int insert(
            @Param("id") String id,
            @Param("actorUserId") String actorUserId,
            @Param("targetUserId") String targetUserId,
            @Param("action") String action,
            @Param("subjectDigest") byte[] subjectDigest,
            @Param("traceId") String traceId,
            @Param("detailJson") String detailJson,
            @Param("createdAt") LocalDateTime createdAt);
}
