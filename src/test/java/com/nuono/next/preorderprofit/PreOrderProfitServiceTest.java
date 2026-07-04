package com.nuono.next.preorderprofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.PreOrderProfitMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PreOrderProfitServiceTest {

    private final FakePreOrderProfitMapper mapper = new FakePreOrderProfitMapper();
    private final PreOrderProfitService service = new PreOrderProfitService(
            mapper,
            new PreOrderProfitCalculator(),
            new ObjectMapper()
    );

    @Test
    void createCandidateCalculatesAndStoresReadySnapshot() {
        PreOrderProfitCandidateView view = service.createCandidate(bossContext(), candidateCommand());

        assertEquals("READY", view.getLatestCalculationStatus());
        assertEquals(new BigDecimal("17.08"), view.getLatestCalculation().getEstimatedProfit());
        assertEquals(1, mapper.candidates.size());
        assertTrue(mapper.candidates.get(view.getId()).getLatestCalculationJson().contains("\"status\":\"READY\""));
        assertEquals(501L, mapper.candidates.get(view.getId()).getOwnerUserId());
        assertEquals("CANMAN", mapper.candidates.get(view.getId()).getStoreCode());
    }

    @Test
    void createCandidateKeepsIncompleteSnapshotWhenInputsAreMissing() {
        PreOrderProfitCandidateCommand command = candidateCommand();
        command.setPurchasePriceRmb(null);

        PreOrderProfitCandidateView view = service.createCandidate(bossContext(), command);

        assertEquals("INCOMPLETE_INPUT", view.getLatestCalculationStatus());
        assertTrue(view.getLatestCalculation().getMissingFields().contains("PURCHASE_PRICE_RMB"));
        assertTrue(mapper.candidates.get(view.getId()).getLatestCalculationJson().contains("INCOMPLETE_INPUT"));
    }

    @Test
    void competitorStorageAllowsMultipleCompetitorsForOneCandidate() {
        PreOrderProfitCandidateView candidate = service.createCandidate(bossContext(), candidateCommand());

        PreOrderProfitCompetitorView first = service.addCompetitor(bossContext(), candidate.getId(), competitorCommand("A"));
        PreOrderProfitCompetitorView second = service.addCompetitor(bossContext(), candidate.getId(), competitorCommand("B"));
        PreOrderProfitCandidateView detail = service.getCandidate(bossContext(), candidate.getId());

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertEquals(2, detail.getCompetitors().size());
    }

    @Test
    void purchaseOrderLinkIsNotDuplicated() {
        PreOrderProfitCandidateView candidate = service.createCandidate(bossContext(), candidateCommand());
        PreOrderProfitPurchaseOrderView order = service.createPurchaseOrder(bossContext(), purchaseOrderCommand());

        PreOrderProfitPurchaseOrderLinkView first = service.addCandidateToPurchaseOrder(
                bossContext(),
                candidate.getId(),
                order.getId()
        );
        PreOrderProfitPurchaseOrderLinkView second = service.addCandidateToPurchaseOrder(
                bossContext(),
                candidate.getId(),
                order.getId()
        );

        assertFalse(first.isAlreadyLinked());
        assertTrue(second.isAlreadyLinked());
        assertEquals(first.getItemId(), second.getItemId());
        assertEquals(1, mapper.purchaseOrderItems.size());
    }

    @Test
    void deleteCandidateIsOwnerScoped() {
        PreOrderProfitCandidateView candidate = service.createCandidate(bossContext(), candidateCommand());

        service.deleteCandidate(bossContext(), candidate.getId());

        assertTrue(mapper.candidates.get(candidate.getId()).isDeleted());
    }

    private BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("CANMAN"))
                .storeOwnerUserIds(Map.of("CANMAN", 501L))
                .build();
    }

    private PreOrderProfitCandidateCommand candidateCommand() {
        PreOrderProfitCandidateCommand command = new PreOrderProfitCandidateCommand();
        command.setStoreCode("canman");
        command.setSiteCode("sa");
        command.setTitle("Portable Electric Bakhoor Burner");
        command.setSkuHint("BURNER-01");
        command.setPurchaseUrl("https://detail.1688.com/offer/123.html");
        command.setPurchasePriceRmb(new BigDecimal("12.50"));
        command.setLengthCm(new BigDecimal("18"));
        command.setWidthCm(new BigDecimal("8"));
        command.setHeightCm(new BigDecimal("8"));
        command.setActualWeightKg(new BigDecimal("0.30"));
        command.setCategoryId("home-kitchen-sa");
        command.setLogisticsCarrierId("et-sa-air-standard");
        command.setSalePrice(new BigDecimal("49"));
        command.setTargetMarginRate(new BigDecimal("0.30"));
        return command;
    }

    private PreOrderProfitCompetitorCommand competitorCommand(String suffix) {
        PreOrderProfitCompetitorCommand command = new PreOrderProfitCompetitorCommand();
        command.setStoreCode("CANMAN");
        command.setTitle("Competitor " + suffix);
        command.setUrl("https://www.noon.com/saudi-en/example-" + suffix);
        command.setPlatform("NOON");
        command.setSiteCode("SA");
        command.setPrice(new BigDecimal("52"));
        command.setCurrency("SAR");
        command.setSellerName("seller " + suffix);
        command.setNotes("manual");
        return command;
    }

    private PreOrderProfitPurchaseOrderCommand purchaseOrderCommand() {
        PreOrderProfitPurchaseOrderCommand command = new PreOrderProfitPurchaseOrderCommand();
        command.setStoreCode("CANMAN");
        command.setSiteCode("SA");
        command.setName("SA June Selection");
        command.setNotes("selection pool");
        return command;
    }

    private static final class FakePreOrderProfitMapper implements PreOrderProfitMapper {
        private long nextId = 260000L;
        private final Map<Long, PreOrderProfitCandidateRow> candidates = new LinkedHashMap<>();
        private final Map<Long, PreOrderProfitCompetitorRow> competitors = new LinkedHashMap<>();
        private final Map<Long, PreOrderProfitPurchaseOrderRow> purchaseOrders = new LinkedHashMap<>();
        private final Map<Long, PreOrderProfitPurchaseOrderItemRow> purchaseOrderItems = new LinkedHashMap<>();

        @Override
        public int allocateProductManagementId(IdSequenceCommand command) {
            nextId++;
            command.setAllocatedId(nextId);
            return 1;
        }

        @Override
        public int insertCandidate(PreOrderProfitCandidateRow row) {
            row.setGmtCreate(LocalDateTime.now());
            row.setGmtUpdated(row.getGmtCreate());
            candidates.put(row.getId(), row);
            return 1;
        }

        @Override
        public int updateCandidate(PreOrderProfitCandidateRow row) {
            candidates.put(row.getId(), row);
            return 1;
        }

        @Override
        public int softDeleteCandidate(Long ownerUserId, Long candidateId, Long actorUserId) {
            PreOrderProfitCandidateRow row = candidates.get(candidateId);
            if (row == null || !ownerUserId.equals(row.getOwnerUserId())) {
                return 0;
            }
            row.setDeleted(true);
            row.setUpdatedBy(actorUserId);
            return 1;
        }

        @Override
        public PreOrderProfitCandidateRow selectCandidateById(Long ownerUserId, Long candidateId) {
            PreOrderProfitCandidateRow row = candidates.get(candidateId);
            return row == null || row.isDeleted() || !ownerUserId.equals(row.getOwnerUserId()) ? null : row;
        }

        @Override
        public List<PreOrderProfitCandidateRow> selectCandidates(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String keyword,
                String calculationStatus,
                String categoryId,
                String logisticsCarrierId
        ) {
            List<PreOrderProfitCandidateRow> rows = new ArrayList<>();
            for (PreOrderProfitCandidateRow row : candidates.values()) {
                if (!row.isDeleted() && ownerUserId.equals(row.getOwnerUserId()) && storeCode.equals(row.getStoreCode())) {
                    rows.add(row);
                }
            }
            return rows;
        }

        @Override
        public int insertCompetitor(PreOrderProfitCompetitorRow row) {
            competitors.put(row.getId(), row);
            return 1;
        }

        @Override
        public int updateCompetitor(PreOrderProfitCompetitorRow row) {
            competitors.put(row.getId(), row);
            return 1;
        }

        @Override
        public int softDeleteCompetitor(Long ownerUserId, Long candidateId, Long competitorId, Long actorUserId) {
            PreOrderProfitCompetitorRow row = competitors.get(competitorId);
            if (row == null || !ownerUserId.equals(row.getOwnerUserId()) || !candidateId.equals(row.getCandidateId())) {
                return 0;
            }
            row.setDeleted(true);
            row.setUpdatedBy(actorUserId);
            return 1;
        }

        @Override
        public PreOrderProfitCompetitorRow selectCompetitorById(Long ownerUserId, Long candidateId, Long competitorId) {
            PreOrderProfitCompetitorRow row = competitors.get(competitorId);
            return row == null || row.isDeleted() || !ownerUserId.equals(row.getOwnerUserId()) || !candidateId.equals(row.getCandidateId())
                    ? null
                    : row;
        }

        @Override
        public List<PreOrderProfitCompetitorRow> selectCompetitorsByCandidate(Long ownerUserId, Long candidateId) {
            List<PreOrderProfitCompetitorRow> rows = new ArrayList<>();
            for (PreOrderProfitCompetitorRow row : competitors.values()) {
                if (!row.isDeleted() && ownerUserId.equals(row.getOwnerUserId()) && candidateId.equals(row.getCandidateId())) {
                    rows.add(row);
                }
            }
            return rows;
        }

        @Override
        public int insertPurchaseOrder(PreOrderProfitPurchaseOrderRow row) {
            purchaseOrders.put(row.getId(), row);
            return 1;
        }

        @Override
        public PreOrderProfitPurchaseOrderRow selectPurchaseOrderById(Long ownerUserId, Long purchaseOrderId) {
            PreOrderProfitPurchaseOrderRow row = purchaseOrders.get(purchaseOrderId);
            return row == null || row.isDeleted() || !ownerUserId.equals(row.getOwnerUserId()) ? null : row;
        }

        @Override
        public List<PreOrderProfitPurchaseOrderRow> selectPurchaseOrders(Long ownerUserId, String storeCode, String siteCode) {
            List<PreOrderProfitPurchaseOrderRow> rows = new ArrayList<>();
            for (PreOrderProfitPurchaseOrderRow row : purchaseOrders.values()) {
                if (!row.isDeleted() && ownerUserId.equals(row.getOwnerUserId()) && storeCode.equals(row.getStoreCode())) {
                    rows.add(row);
                }
            }
            return rows;
        }

        @Override
        public PreOrderProfitPurchaseOrderItemRow selectPurchaseOrderItem(Long ownerUserId, Long purchaseOrderId, Long candidateId) {
            for (PreOrderProfitPurchaseOrderItemRow row : purchaseOrderItems.values()) {
                if (!row.isDeleted()
                        && ownerUserId.equals(row.getOwnerUserId())
                        && purchaseOrderId.equals(row.getPurchaseOrderId())
                        && candidateId.equals(row.getCandidateId())) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public int insertPurchaseOrderItem(PreOrderProfitPurchaseOrderItemRow row) {
            purchaseOrderItems.put(row.getId(), row);
            return 1;
        }

        @Override
        public List<PreOrderProfitPurchaseOrderRow> selectPurchaseOrdersByCandidate(Long ownerUserId, Long candidateId) {
            List<PreOrderProfitPurchaseOrderRow> rows = new ArrayList<>();
            for (PreOrderProfitPurchaseOrderItemRow item : purchaseOrderItems.values()) {
                if (!item.isDeleted() && ownerUserId.equals(item.getOwnerUserId()) && candidateId.equals(item.getCandidateId())) {
                    rows.add(purchaseOrders.get(item.getPurchaseOrderId()));
                }
            }
            return rows;
        }
    }
}
