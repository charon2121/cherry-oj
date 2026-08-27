package com.cherryoj.userservice.persistence;

import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {

    UserAccount findByNormalizedUsernameForUpdate(String usernameNormalized);

    UserAccount findById(String id);

    UserAccount findByIdForUpdate(String id);

    long countAdmins();

    long countUsers();

    List<UserAccount> listUsers(@Param("offset") long offset, @Param("limit") int limit);

    int insert(UserAccount account);

    int recordLoginFailure(
            @Param("id") String id,
            @Param("now") LocalDateTime now,
            @Param("lockedUntil") LocalDateTime lockedUntil);

    int recordLoginSuccess(@Param("id") String id, @Param("now") LocalDateTime now);

    int updatePassword(
            @Param("id") String id,
            @Param("passwordHash") String passwordHash,
            @Param("passwordChangeRequired") boolean passwordChangeRequired,
            @Param("now") LocalDateTime now,
            @Param("expectedRowVersion") long expectedRowVersion);

    int updateStatus(
            @Param("id") String id,
            @Param("status") UserStatus status,
            @Param("now") LocalDateTime now,
            @Param("expectedRowVersion") long expectedRowVersion);
}
