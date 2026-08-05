package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class DataPullScheduleAnchorMapperSqlTest {

    @Test
    void activeAnchorReadIsSealedByThePersistedCutoverIdentity() throws Exception {
        Method method = DataPullScheduleAnchorMapper.class.getMethod(
                "selectActiveAnchor",
                com.nuono.next.datapull.runtime.OperationCode.class,
                String.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("JOIN dp_pull_schedule_cutover cutover"));
        assertTrue(sql.contains("cutover.state = 'ACTIVE'"));
        assertTrue(sql.contains("BINARY cutover.cutover_key = BINARY anchor.cutover_key"));
        assertTrue(sql.contains("BINARY anchor.scope_key = BINARY #{scopeKey}"));
        assertTrue(sql.contains("JOIN dp_pull_scope_admission admission"));
        assertTrue(sql.contains("anchor.anchor_evidence_sha256 AS anchorEvidenceSha256"));
    }

    @Test
    void persistedPostAdmissionCanOnlyInsertItsExactEligibleAnchor() throws Exception {
        Method method = Arrays.stream(DataPullScheduleAnchorMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals("insertPostCutoverAnchorIfActive"))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Insert.class).value());

        assertTrue(sql.contains("FROM dp_pull_schedule_cutover cutover"));
        assertTrue(sql.contains("JOIN dp_pull_scope_admission admission"));
        assertTrue(sql.contains("cutover.state = 'ACTIVE'"));
        assertTrue(sql.contains("BINARY cutover.cutover_key = BINARY #{cutoverKey}"));
        assertTrue(sql.contains("admission.admission_kind = 'POST_CUTOVER'"));
        assertTrue(sql.contains("admission.first_eligible_at_utc = #{firstEligibleAtUtc}"));
        assertTrue(sql.contains("admission.first_eligible_at_utc = #{reconcileAfterUtc}"));
        assertTrue(sql.contains(
                "admission.first_eligible_at_utc >= cutover.activated_at_utc"
        ));
        assertTrue(sql.contains("admission.source_binding_sha256 = #{sourceBindingSha256}"));
        assertTrue(sql.contains("#{anchorEvidenceSha256}"));
        assertTrue(sql.contains("'POST_CUTOVER_SCOPE'"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertFalse(sql.contains("reconcile_after_utc = VALUES"));
        assertFalse(sql.toUpperCase().contains("NOW("));
    }
}
