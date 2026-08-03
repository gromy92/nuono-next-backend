package com.nuono.next.infrastructure.mapper;

import com.nuono.next.system.schema.ReleaseSchemaMigrationRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReleaseSchemaMigrationMapper {
    @Select({
            "SELECT",
            "  h.migration_key,",
            "  h.checksum_sha256,",
            "  h.postcheck_sha256,",
            "  h.state,",
            "  h.attempt_no,",
            "  a.checksum_sha256 AS attempt_checksum,",
            "  a.postcheck_sha256 AS attempt_postcheck_checksum,",
            "  a.state AS attempt_state,",
            "  a.attempt_no AS joined_attempt_no",
            "FROM nuono_schema_migration h",
            "LEFT JOIN nuono_schema_migration_attempt a",
            "  ON a.migration_key = h.migration_key",
            " AND a.attempt_no = h.attempt_no",
            "ORDER BY h.migration_key"
    })
    @Results({
            @Result(column = "migration_key", property = "migrationKey"),
            @Result(column = "checksum_sha256", property = "checksum"),
            @Result(column = "postcheck_sha256", property = "postcheckChecksum"),
            @Result(column = "state", property = "state"),
            @Result(column = "attempt_no", property = "attemptNo"),
            @Result(column = "attempt_checksum", property = "attemptChecksum"),
            @Result(
                    column = "attempt_postcheck_checksum",
                    property = "attemptPostcheckChecksum"
            ),
            @Result(column = "attempt_state", property = "attemptState"),
            @Result(column = "joined_attempt_no", property = "joinedAttemptNo")
    })
    List<ReleaseSchemaMigrationRow> selectMigrationHistory();

    @Select({
            "SELECT COUNT(*)",
            "FROM nuono_schema_migration_attempt a",
            "LEFT JOIN nuono_schema_migration h",
            "  ON h.migration_key = a.migration_key",
            "WHERE h.migration_key IS NULL"
    })
    long countOrphanAttempts();
}
