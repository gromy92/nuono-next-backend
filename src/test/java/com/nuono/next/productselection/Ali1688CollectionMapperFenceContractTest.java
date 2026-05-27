package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class Ali1688CollectionMapperFenceContractTest {

    @Test
    void collectionWorkerWritebacksExposeClaimFencedMapperMethods() {
        assertUpdateIsClaimFenced("markTaskFailedByClaimedTask", Long.class, String.class, String.class, Long.class, String.class);
        assertUpdateIsClaimFenced("softDeleteCandidatesByClaimedTask", Long.class, Long.class, String.class);
        assertInsertIsClaimFenced("insertCandidateForClaimedTask", Ali1688CollectionRecords.CandidateRecord.class, String.class);
        assertUpdateIsClaimFenced("clearSelectedRanksForClaimedTask", Long.class, Long.class, String.class);
        assertUpdateIsClaimFenced("updateSelectedRankForClaimedTask", Long.class, Long.class, Integer.class, Long.class, String.class);
        assertInsertIsClaimFenced("insertAiAssessmentForClaimedTask", Ali1688CollectionRecords.AiAssessmentRecord.class, String.class);
    }

    private void assertUpdateIsClaimFenced(String methodName, Class<?>... parameterTypes) {
        Method method = assertDoesNotThrow(() -> Ali1688CollectionMapper.class.getMethod(methodName, parameterTypes));
        Update update = method.getAnnotation(Update.class);
        assertNotNull(update, methodName + " must use MyBatis @Update");
        assertClaimFence(methodName, String.join(" ", update.value()));
    }

    private void assertInsertIsClaimFenced(String methodName, Class<?>... parameterTypes) {
        Method method = assertDoesNotThrow(() -> Ali1688CollectionMapper.class.getMethod(methodName, parameterTypes));
        Insert insert = method.getAnnotation(Insert.class);
        assertNotNull(insert, methodName + " must use MyBatis @Insert");
        assertClaimFence(methodName, String.join(" ", insert.value()));
    }

    private void assertClaimFence(String methodName, String sql) {
        assertTrue(sql.contains("locked_by = #{lockedBy}"), methodName + " must check the active lock owner");
        assertTrue(sql.contains("current_task_key IS NOT NULL"), methodName + " must require the task to still be current");
        assertTrue(sql.contains("is_deleted = b'0'"), methodName + " must ignore deleted tasks");
    }
}
