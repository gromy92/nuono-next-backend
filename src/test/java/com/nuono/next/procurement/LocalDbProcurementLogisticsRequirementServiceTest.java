package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.infrastructure.mapper.ProcurementLogisticsRequirementMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalDbProcurementLogisticsRequirementServiceTest {

    @Test
    void shouldSaveAndReadSingleSelectedTransportModeForProcurementDemand() {
        CapturingProcurementLogisticsRequirementMapper mapper = new CapturingProcurementLogisticsRequirementMapper();
        mapper.ownedDemandItems.put(41101L, 10002L);
        LocalDbProcurementLogisticsRequirementService service =
                new LocalDbProcurementLogisticsRequirementService(mapper);
        ProcurementLogisticsRequirementCommand command = new ProcurementLogisticsRequirementCommand();
        command.setOwnerUserId(10002L);
        command.setDemandItemId(41101L);
        command.setTransportMode("sea");
        command.setDestinationCountry("KSA");
        command.setDestinationNode("KSA/Riyadh");
        command.setOriginNode("佛山仓");
        command.setPackageLengthCm(new BigDecimal("23"));
        command.setPackageWidthCm(new BigDecimal("13"));
        command.setPackageHeightCm(new BigDecimal("5"));
        command.setUnitWeightGrams(new BigDecimal("100"));
        command.setQuantity(300);
        command.setCargoAttributes("ordinary");

        ProcurementLogisticsRequirementView saved = service.saveRequirement(command);
        ProcurementLogisticsRequirementView loaded = service.getRequirement(10002L, 41101L);

        assertEquals("物流需求已保存。", saved.getMessage());
        assertEquals("sea", loaded.getRequirement().getTransportMode());
        assertEquals("KSA", loaded.getRequirement().getDestinationCountry());
        assertEquals("KSA/Riyadh", loaded.getRequirement().getDestinationNode());
        assertEquals("佛山仓", loaded.getRequirement().getOriginNode());
        assertEquals(new BigDecimal("23"), loaded.getRequirement().getPackageLengthCm());
        assertEquals(new BigDecimal("13"), loaded.getRequirement().getPackageWidthCm());
        assertEquals(new BigDecimal("5"), loaded.getRequirement().getPackageHeightCm());
        assertEquals(new BigDecimal("100"), loaded.getRequirement().getUnitWeightGrams());
        assertEquals(Integer.valueOf(300), loaded.getRequirement().getQuantity());
        assertEquals("ordinary", loaded.getRequirement().getCargoAttributes());
        assertFalse(loaded.getRequirement().getTransportMode().contains(","));
        assertEquals(1, mapper.requirements.size());
    }

    @Test
    void shouldBlockRecommendationWhenRequiredShipmentFactsAreMissing() {
        CapturingProcurementLogisticsRequirementMapper mapper = new CapturingProcurementLogisticsRequirementMapper();
        mapper.ownedDemandItems.put(41101L, 10002L);
        LocalDbProcurementLogisticsRequirementService service =
                new LocalDbProcurementLogisticsRequirementService(mapper);
        ProcurementLogisticsRequirementCommand command = new ProcurementLogisticsRequirementCommand();
        command.setOwnerUserId(10002L);
        command.setDemandItemId(41101L);
        command.setTransportMode("air");

        ProcurementLogisticsRequirementView saved = service.saveRequirement(command);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.requireReadyForRecommendation(10002L, 41101L)
        );

        assertFalse(saved.isRecommendationReady());
        assertEquals(
                "请先补全物流需求：请填写目的国家；请填写目的仓或目的节点；请填写发货地；请填写包装长度；请填写包装宽度；请填写包装高度；请填写单件重量；请填写采购数量。",
                exception.getMessage()
        );
    }

    @Test
    void shouldUpdateExistingRequirementInsteadOfCreatingAlternateAirAndSeaPlans() {
        CapturingProcurementLogisticsRequirementMapper mapper = new CapturingProcurementLogisticsRequirementMapper();
        mapper.ownedDemandItems.put(41101L, 10002L);
        LocalDbProcurementLogisticsRequirementService service =
                new LocalDbProcurementLogisticsRequirementService(mapper);
        ProcurementLogisticsRequirementCommand air = fullCommand();
        air.setTransportMode("air");
        ProcurementLogisticsRequirementView first = service.saveRequirement(air);
        ProcurementLogisticsRequirementCommand sea = fullCommand();
        sea.setTransportMode("sea");

        ProcurementLogisticsRequirementView updated = service.saveRequirement(sea);
        ProcurementLogisticsRequirementView loaded = service.getRequirement(10002L, 41101L);

        assertEquals(first.getRequirement().getId(), updated.getRequirement().getId());
        assertEquals("sea", loaded.getRequirement().getTransportMode());
        assertEquals(1, mapper.requirements.size());
    }

    private static ProcurementLogisticsRequirementCommand fullCommand() {
        ProcurementLogisticsRequirementCommand command = new ProcurementLogisticsRequirementCommand();
        command.setOwnerUserId(10002L);
        command.setDemandItemId(41101L);
        command.setTransportMode("sea");
        command.setDestinationCountry("KSA");
        command.setDestinationNode("KSA/Riyadh");
        command.setOriginNode("佛山仓");
        command.setPackageLengthCm(new BigDecimal("23"));
        command.setPackageWidthCm(new BigDecimal("13"));
        command.setPackageHeightCm(new BigDecimal("5"));
        command.setUnitWeightGrams(new BigDecimal("100"));
        command.setQuantity(300);
        command.setCargoAttributes("ordinary");
        return command;
    }

    private static class CapturingProcurementLogisticsRequirementMapper implements ProcurementLogisticsRequirementMapper {

        private long nextId = 96000L;
        private final Map<Long, Long> ownedDemandItems = new LinkedHashMap<>();
        private final Map<Long, ProcurementLogisticsRequirementRow> requirements = new LinkedHashMap<>();

        @Override
        public Long nextRequirementId() {
            nextId += 1;
            return nextId;
        }

        @Override
        public int countOwnedDemandItem(Long ownerUserId, Long demandItemId) {
            return ownerUserId != null && ownerUserId.equals(ownedDemandItems.get(demandItemId)) ? 1 : 0;
        }

        @Override
        public ProcurementLogisticsRequirementRow selectRequirement(Long ownerUserId, Long demandItemId) {
            ProcurementLogisticsRequirementRow row = requirements.get(demandItemId);
            if (row == null || !ownerUserId.equals(row.getOwnerUserId())) {
                return null;
            }
            return row;
        }

        @Override
        public int upsertRequirement(ProcurementLogisticsRequirementRow row, Long operatorUserId) {
            requirements.put(row.getDemandItemId(), row);
            return 1;
        }
    }
}
