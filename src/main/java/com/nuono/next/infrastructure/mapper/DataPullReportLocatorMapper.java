package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.report.ReportDownloadLocatorRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Persistence Adapter for encrypted report download locators. */
public interface DataPullReportLocatorMapper {

    @Insert({
            "INSERT INTO dp_pull_report_download_locator (",
            "  locator_ref, task_id, stable_request_key, remote_handle_sha256, iv,",
            "  encrypted_locator, created_at",
            ") VALUES (",
            "  #{row.locatorReference}, #{row.taskId}, #{row.stableRequestKey},",
            "  #{row.remoteHandleSha256},",
            "  #{row.initializationVector}, #{row.encryptedLocator}, #{row.createdAt}",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insert(@Param("row") ReportDownloadLocatorRecord row);

    @Select({
            "SELECT locator_ref AS locatorReference, task_id AS taskId,",
            "       stable_request_key AS stableRequestKey,",
            "       remote_handle_sha256 AS remoteHandleSha256, iv AS initializationVector,",
            "       encrypted_locator AS encryptedLocator, created_at AS createdAt",
            "FROM dp_pull_report_download_locator",
            "WHERE BINARY locator_ref = BINARY #{locatorReference}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportDownloadLocatorRecord selectByReference(
            @Param("locatorReference") String locatorReference
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_download_locator",
            "WHERE locator_ref IN (",
            "  SELECT locator_ref FROM (",
            "    SELECT locator.locator_ref",
            "    FROM dp_pull_report_download_locator locator",
            "    INNER JOIN dp_pull_task task ON task.id = locator.task_id",
            "    WHERE task.state IN ('SUCCEEDED', 'SUPERSEDED')",
            "      AND task.finished_at IS NOT NULL",
            "      AND task.finished_at &lt; #{cutoffUtc}",
            "      AND locator.created_at &lt; #{cutoffUtc}",
            "      AND task.lease_owner IS NULL",
            "      AND task.lease_until IS NULL",
            "      AND (task.state = 'SUPERSEDED' OR EXISTS (",
            "        SELECT 1 FROM dp_pull_report_apply applied",
            "        WHERE applied.task_id = task.id",
            "      ))",
            "    ORDER BY task.finished_at ASC, locator.created_at ASC,",
            "             locator.locator_ref ASC",
            "    LIMIT #{batchSize}",
            "  ) eligible_locator",
            ")",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_download_locator",
            "WHERE locator_ref IN (SELECT locator_ref FROM (",
            " SELECT locator.locator_ref FROM dp_pull_report_download_locator locator",
            " JOIN dp_pull_task task ON task.id=locator.task_id",
            " WHERE task.state='FAILED' AND task.finished_at IS NOT NULL",
            "  AND task.finished_at &lt; #{cutoffUtc}",
            "  AND locator.created_at &lt; #{cutoffUtc}",
            "  AND task.lease_owner IS NULL AND task.lease_until IS NULL",
            " ORDER BY task.finished_at,locator.created_at,locator.locator_ref",
            " LIMIT #{batchSize}",
            ") abandoned_locator)",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );
}
