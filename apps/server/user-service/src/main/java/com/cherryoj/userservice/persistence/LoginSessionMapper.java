package com.cherryoj.userservice.persistence;

import com.cherryoj.userservice.domain.LoginSession;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginSessionMapper {

    int insert(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("hash") byte[] hash,
            @Param("sessionVersion") long sessionVersion,
            @Param("now") LocalDateTime now,
            @Param("absoluteExpiresAt") LocalDateTime absoluteExpiresAt);

    LoginSession findActiveByHash(@Param("hash") byte[] hash, @Param("now") LocalDateTime now);

    int touch(@Param("id") String id, @Param("now") LocalDateTime now);

    int revokeCurrent(@Param("hash") byte[] hash, @Param("now") LocalDateTime now, @Param("reason") String reason);

    int revokeAll(@Param("userId") String userId, @Param("now") LocalDateTime now, @Param("reason") String reason);
}
