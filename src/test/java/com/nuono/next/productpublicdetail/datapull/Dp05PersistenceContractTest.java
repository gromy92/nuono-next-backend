package com.nuono.next.productpublicdetail.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.InMemoryBackoffHoldStore;
import com.nuono.next.infrastructure.mapper.Dp05RuntimeMapper;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailRuntimeFactWriter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.TaskState;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class Dp05PersistenceContractTest {

    @Test
    void runtimeCursorHasNoBusinessLimitOrFreshnessFilterAndFirstIdentityWins() throws Exception {
        Method method = Dp05RuntimeMapper.class.getMethod(
                "selectCandidateAfter",
                Long.class,
                Long.class,
                String.class,
                String.class,
                long.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("pso.id > #{afterOfferId}"));
        assertTrue(sql.contains("ls.id = #{logicalStoreId}"));
        assertTrue(sql.contains("ORDER BY pso.id ASC LIMIT 1"));
        assertTrue(sql.contains("earlier_pso.id < pso.id"));
        assertTrue(sql.contains("UPPER(TRIM(earlier_pm.sku_parent)) = UPPER(TRIM(pm.sku_parent))"));
        assertFalse(sql.contains("#{limit}"));
        assertFalse(sql.contains("product_public_detail_snapshot"));
        assertFalse(sql.contains("fetched_at"));
        assertFalse(sql.contains("maintenance_enabled"));
    }

    @Test
    void checkpointRoundTripRetainsTheCurrentItemAndPartnerPhase() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Dp05CheckpointCodec codec = new Dp05CheckpointCodec(mapper);
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(81L, "ZABCDEF81");

        Dp05Checkpoint restored = codec.decode(codec.encode(Dp05Checkpoint.partner(80L, candidate)));

        assertEquals(Dp05Checkpoint.Phase.PARTNER, restored.getPhase());
        assertEquals(80L, restored.getAfterOfferId());
        assertEquals(81L, restored.getCandidate().getProductSiteOfferId());
        assertEquals("ZABCDEF81", restored.getCandidate().getNoonProductCode());
    }

    @Test
    void malformedPersistedCheckpointFailsAsPersistentStateCorruption() {
        Dp05ProductDetailJob job = Dp05TestSupport.job(
                List.of(),
                request -> {
                    throw new AssertionError("invalid checkpoint must precede frontend access");
                },
                request -> {
                    throw new AssertionError("invalid checkpoint must precede Partner access");
                },
                new Dp05TestSupport.RecordingWriter(),
                new InMemoryBackoffHoldStore()
        );

        AdvanceResult result = job.advance(Dp05TestSupport.context("{invalid-json", 0));

        assertEquals(TaskState.FAILED, result.getNextState());
        assertEquals("DP05_CHECKPOINT_INVALID", result.getSanitizedCode());
        assertEquals("{invalid-json", result.getCheckpoint());
    }

    @Test
    void factApplyIsOneTransactionalWriterBoundary() throws Exception {
        Method apply = ProductPublicDetailRuntimeFactWriter.class.getMethod(
                "apply",
                DataPullTask.class,
                ProductPublicDetailCandidate.class,
                NoonPublicProductDetailResult.class,
                LocalDate.class,
                long.class
        );

        assertTrue(apply.isAnnotationPresent(Transactional.class));
        assertEquals(
                1,
                Arrays.stream(ProductPublicDetailRuntimeFactWriter.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals("apply"))
                        .count()
        );
    }
}
