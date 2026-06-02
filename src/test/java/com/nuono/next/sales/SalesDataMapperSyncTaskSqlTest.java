package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class SalesDataMapperSyncTaskSqlTest {

    @Test
    void exportStatusUpdateShouldPersistExportFieldsAndKeepTaskRunning() {
        Method method = Arrays.stream(SalesDataMapper.class.getDeclaredMethods())
                .filter(candidate -> "updateSalesSyncTaskExportStatus".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("status = 'running'"));
        assertTrue(sql.contains("export_code = COALESCE(#{status.exportCode}, export_code)"));
        assertTrue(sql.contains("export_status = #{status.status}"));
        assertTrue(sql.contains("export_download_url = COALESCE(#{status.downloadUrl}, export_download_url)"));
        assertTrue(sql.contains("finished_at = NULL"));
    }

    @Test
    void reusableTaskLookupShouldOnlyReuseRunningTasksWithExportCodeInSameWindow() {
        Method method = Arrays.stream(SalesDataMapper.class.getDeclaredMethods())
                .filter(candidate -> "selectReusableSalesSyncTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("owner_user_id = #{command.ownerUserId}"));
        assertTrue(sql.contains("store_code = #{command.storeCode}"));
        assertTrue(sql.contains("site_code = #{command.siteCode}"));
        assertTrue(sql.contains("date_from = #{command.dateFrom}"));
        assertTrue(sql.contains("date_to = #{command.dateTo}"));
        assertTrue(sql.contains("status = 'running'"));
        assertTrue(sql.contains("export_code IS NOT NULL"));
    }
}
