package com.aacv.system.identity.infrastructure.persistence;

import com.aacv.system.identity.domain.UserStatistics;

import com.aacv.system.identity.domain.UserProfile;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {

    long countUsers();

    UserStatistics statistics();

    Long lockAdministratorRole();

    long countActiveAdministrators();

    int updateUser(@Param("userId") long userId, @Param("expectedVersion") long expectedVersion,
            @Param("profile") UserProfile profile, @Param("status") String status,
            @Param("securityChanged") boolean securityChanged, @Param("now") Instant now);

    UserAccountRow findById(@Param("userId") long userId);

    UserAccountRow findByUsername(@Param("username") String username);

    List<UserAccountRow> findPage(@Param("offset") long offset, @Param("size") int size);

    List<String> findRoleCodesByUserId(@Param("userId") long userId);

    List<UserRoleRow> findRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    int insertUser(UserAccountRow row);

    int insertUserRole(@Param("userId") long userId, @Param("roleCode") String roleCode);

    int updateStatus(
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("status") String status,
            @Param("now") Instant now);

    int updatePassword(
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("passwordHash") String passwordHash,
            @Param("status") String status,
            @Param("credentialsChangedAt") Instant credentialsChangedAt);

    int incrementVersion(
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int deleteUserRoles(@Param("userId") long userId);

    Integer acquireInitialAdminLock(@Param("timeoutSeconds") int timeoutSeconds);

    Integer releaseInitialAdminLock();
}
