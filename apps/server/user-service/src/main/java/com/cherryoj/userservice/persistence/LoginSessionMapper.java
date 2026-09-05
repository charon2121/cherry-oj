package com.cherryoj.userservice.persistence;

import com.cherryoj.userservice.domain.LoginGrant;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginSessionMapper {

    int insert(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("grantHash") byte[] grantHash,
            @Param("sessionVersion") long sessionVersion,
            @Param("now") LocalDateTime now,
            @Param("absoluteExpiresAt") LocalDateTime absoluteExpiresAt);

    LoginGrant findActiveByGrantHash(
            @Param("grantHash") byte[] grantHash,
            @Param("now") LocalDateTime now);

    LoginGrant findActiveByGrantHashForUpdate(
            @Param("grantHash") byte[] grantHash,
            @Param("now") LocalDateTime now);

    int markUsed(
            @Param("id") String id,
            @Param("now") LocalDateTime now,
            @Param("expectedRowVersion") long expectedRowVersion);

    int revokeCurrent(
            @Param("grantHash") byte[] grantHash,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason);

    int revokeAll(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason);
}
