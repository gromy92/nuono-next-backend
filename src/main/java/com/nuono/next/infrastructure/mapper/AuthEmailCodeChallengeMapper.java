package com.nuono.next.infrastructure.mapper;

import com.nuono.next.auth.AuthEmailCodeChallengeRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AuthEmailCodeChallengeMapper {

    @Select({
            "SELECT",
            "  id, email, purpose, code_hash, code_salt, expires_at, consumed_at, attempt_count,",
            "  request_ip, user_agent, created_at, updated_at",
            "FROM auth_email_code_challenge",
            "WHERE email = #{email}",
            "  AND purpose = #{purpose}",
            "ORDER BY created_at DESC, id DESC",
            "LIMIT 1"
    })
    AuthEmailCodeChallengeRecord selectLatestChallenge(
            @Param("email") String email,
            @Param("purpose") String purpose
    );

    @Select({
            "SELECT",
            "  id, email, purpose, code_hash, code_salt, expires_at, consumed_at, attempt_count,",
            "  request_ip, user_agent, created_at, updated_at",
            "FROM auth_email_code_challenge",
            "WHERE email = #{email}",
            "  AND purpose = #{purpose}",
            "  AND consumed_at IS NULL",
            "  AND expires_at >= #{now}",
            "ORDER BY created_at DESC, id DESC",
            "LIMIT 1"
    })
    AuthEmailCodeChallengeRecord selectLatestActiveChallenge(
            @Param("email") String email,
            @Param("purpose") String purpose,
            @Param("now") LocalDateTime now
    );

    @Insert({
            "INSERT INTO auth_email_code_challenge (",
            "  email, purpose, code_hash, code_salt, expires_at, consumed_at, attempt_count,",
            "  request_ip, user_agent",
            ") VALUES (",
            "  #{email}, #{purpose}, #{codeHash}, #{codeSalt}, #{expiresAt}, #{consumedAt}, #{attemptCount},",
            "  #{requestIp}, #{userAgent}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChallenge(AuthEmailCodeChallengeRecord challenge);

    @Update({
            "UPDATE auth_email_code_challenge",
            "SET consumed_at = #{now},",
            "    updated_at = #{now}",
            "WHERE email = #{email}",
            "  AND purpose = #{purpose}",
            "  AND consumed_at IS NULL"
    })
    int consumeActiveChallenges(
            @Param("email") String email,
            @Param("purpose") String purpose,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE auth_email_code_challenge",
            "SET consumed_at = #{now},",
            "    updated_at = #{now}",
            "WHERE id = #{id}",
            "  AND consumed_at IS NULL"
    })
    int consumeChallenge(
            @Param("id") Long id,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE auth_email_code_challenge",
            "SET attempt_count = attempt_count + 1,",
            "    updated_at = #{now}",
            "WHERE id = #{id}",
            "  AND consumed_at IS NULL"
    })
    int incrementAttempts(
            @Param("id") Long id,
            @Param("now") LocalDateTime now
    );
}
