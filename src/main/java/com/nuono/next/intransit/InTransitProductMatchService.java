package com.nuono.next.intransit;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitProductMatchViews.CandidateListView;
import com.nuono.next.intransit.InTransitProductMatchViews.PreparationView;
import com.nuono.next.intransit.InTransitProductMatchViews.RematchView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InTransitProductMatchService {
    private static final String UNMATCHED_MESSAGE = "物流 barcode 尚未匹配系统商品。";

    private final InTransitGoodsMapper mapper;
    private final InTransitBatchService batchService;
    private final InTransitGoodsAccessScopeService accessScopeService;

    public InTransitProductMatchService(
            InTransitGoodsMapper mapper,
            InTransitBatchService batchService,
            InTransitGoodsAccessScopeService accessScopeService
    ) {
        this.mapper = mapper;
        this.batchService = batchService;
        this.accessScopeService = accessScopeService;
    }

    public CandidateListView list(Long ownerUserId, Long batchId) {
        CandidateListView view = new CandidateListView();
        view.setItems(mapper.listProductMatchCandidates(ownerUserId, batchId));
        return view;
    }

    public int pendingCount(Long ownerUserId, Long batchId) {
        return mapper.countProductMatchCandidates(ownerUserId, batchId);
    }

    @Transactional
    public PreparationView prepareForStoreSite(
            BusinessAccessContext accessContext,
            String storeCode,
            String siteCode
    ) {
        Long ownerUserId = accessContext.getBusinessOwnerUserId();
        List<Long> batchIds = mapper.listProductLandingBatchIds(ownerUserId, clean(storeCode), clean(siteCode));
        int matched = 0;
        int pending = 0;
        for (Long batchId : batchIds) {
            RematchView result = rematch(
                    accessContext,
                    ownerUserId,
                    accessContext.getSessionUserId(),
                    batchId
            );
            matched += result.getMatchedCount();
            pending += result.getPendingCount();
        }
        PreparationView view = new PreparationView();
        view.setBatchCount(batchIds.size());
        view.setMatchedCount(matched);
        view.setPendingCount(pending);
        return view;
    }

    @Transactional
    public void saveCandidate(SaveLineCommand source, String sourcePsku) {
        String barcode = clean(source.getSku());
        if (!StringUtils.hasText(barcode)) {
            throw new IllegalArgumentException("待匹配物流行缺少 barcode。");
        }
        InTransitProductMatchCandidate row = mapper.selectProductMatchCandidate(
                source.getOwnerUserId(),
                source.getBatchId(),
                clean(source.getBoxNo()),
                barcode
        );
        boolean create = row == null;
        boolean sourceChanged = create || sourceChanged(row, source, sourcePsku);
        String currentStatus = create ? null : row.getMatchStatus();
        String currentMessage = create ? null : row.getMatchMessage();
        if (create) {
            row = new InTransitProductMatchCandidate();
            row.setId(mapper.nextProductMatchCandidateId());
            row.setOwnerUserId(source.getOwnerUserId());
            row.setBatchId(source.getBatchId());
            row.setBoxNo(clean(source.getBoxNo()));
            row.setSourceBarcode(barcode);
            row.setCreatedBy(source.getOperatorUserId());
        }
        PackageRow itemPackage = mapper.selectPackageByBoxNo(
                source.getOwnerUserId(),
                source.getBatchId(),
                clean(source.getBoxNo())
        );
        row.setPackageId(itemPackage == null ? null : itemPackage.getId());
        row.setSourcePsku(clean(sourcePsku));
        row.setSourceMsku(clean(source.getMsku()));
        row.setProductName(clean(source.getProductName()));
        row.setStoreCode(clean(source.getStoreCode()));
        row.setSiteCode(clean(source.getSiteCode()));
        row.setShippedQuantity(nonNegative(source.getShippedQuantity()));
        row.setReceivedQuantity(nonNegative(source.getReceivedQuantity()));
        row.setCartonCount(source.getCartonCount());
        row.setUnitsPerCarton(source.getUnitsPerCarton());
        row.setCartonWeightKg(source.getCartonWeightKg());
        row.setCartonVolumeCbm(source.getCartonVolumeCbm());
        row.setMatchStatus(sourceChanged ? "UNMATCHED" : currentStatus);
        row.setMatchMessage(sourceChanged ? UNMATCHED_MESSAGE : currentMessage);
        row.setUpdatedBy(source.getOperatorUserId());
        if (create) {
            mapper.insertProductMatchCandidate(row);
        } else {
            mapper.updateProductMatchCandidate(row);
        }
    }

    @Transactional
    public void clearCandidate(
            Long ownerUserId,
            Long batchId,
            String boxNo,
            String barcode,
            Long operatorUserId
    ) {
        mapper.resolveProductMatchCandidate(ownerUserId, batchId, clean(boxNo), clean(barcode), operatorUserId);
    }

    @Transactional
    public RematchView rematch(
            BusinessAccessContext accessContext,
            Long ownerUserId,
            Long operatorUserId,
            Long batchId
    ) {
        List<InTransitProductMatchCandidate> candidates = mapper.listProductMatchCandidates(ownerUserId, batchId);
        int matched = 0;
        for (InTransitProductMatchCandidate candidate : candidates) {
            BarcodeProductIdentity identity =
                    mapper.selectProductIdentityByBarcode(ownerUserId, candidate.getSourceBarcode());
            if (identity == null || !StringUtils.hasText(identity.getPartnerSku())) {
                mapper.markProductMatchCandidateUnmatched(
                        ownerUserId,
                        candidate.getId(),
                        UNMATCHED_MESSAGE,
                        operatorUserId
                );
                continue;
            }
            LineRow existing = mapper.selectLineByBoxNoAndPsku(
                    ownerUserId,
                    batchId,
                    candidate.getBoxNo(),
                    identity.getPartnerSku()
            );
            SaveLineCommand command = toLineCommand(candidate, existing, identity.getPartnerSku(), operatorUserId);
            accessScopeService.requireWritableLineScope(accessContext, command);
            batchService.saveLine(command);
            clearCandidate(ownerUserId, batchId, candidate.getBoxNo(), candidate.getSourceBarcode(), operatorUserId);
            matched += 1;
        }
        List<InTransitProductMatchCandidate> pending = mapper.listProductMatchCandidates(ownerUserId, batchId);
        RematchView view = new RematchView();
        view.setMatchedCount(matched);
        view.setPendingCount(pending.size());
        view.setPendingItems(pending);
        return view;
    }

    private static SaveLineCommand toLineCommand(
            InTransitProductMatchCandidate candidate,
            LineRow existing,
            String partnerSku,
            Long operatorUserId
    ) {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(candidate.getOwnerUserId());
        command.setOperatorUserId(operatorUserId);
        command.setBatchId(candidate.getBatchId());
        command.setLineId(existing == null ? null : existing.getId());
        command.setBoxNo(candidate.getBoxNo());
        command.setSku(candidate.getSourceBarcode());
        command.setMsku(candidate.getSourceMsku());
        command.setPsku(partnerSku);
        command.setProductName(candidate.getProductName());
        command.setStoreCode(candidate.getStoreCode());
        command.setSiteCode(candidate.getSiteCode());
        command.setShippedQuantity(candidate.getShippedQuantity());
        command.setReceivedQuantity(candidate.getReceivedQuantity());
        command.setCartonCount(candidate.getCartonCount());
        command.setUnitsPerCarton(candidate.getUnitsPerCarton());
        command.setCartonWeightKg(candidate.getCartonWeightKg());
        command.setCartonVolumeCbm(candidate.getCartonVolumeCbm());
        command.setPackageSnapshotAuthoritative(false);
        return command;
    }

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static boolean sourceChanged(
            InTransitProductMatchCandidate existing,
            SaveLineCommand source,
            String sourcePsku
    ) {
        return !Objects.equals(clean(existing.getSourcePsku()), clean(sourcePsku))
                || !Objects.equals(clean(existing.getSourceMsku()), clean(source.getMsku()))
                || !Objects.equals(clean(existing.getProductName()), clean(source.getProductName()))
                || !Objects.equals(clean(existing.getStoreCode()), clean(source.getStoreCode()))
                || !Objects.equals(clean(existing.getSiteCode()), clean(source.getSiteCode()))
                || !Objects.equals(existing.getShippedQuantity(), nonNegative(source.getShippedQuantity()))
                || !Objects.equals(existing.getReceivedQuantity(), nonNegative(source.getReceivedQuantity()))
                || !Objects.equals(existing.getCartonCount(), source.getCartonCount())
                || !Objects.equals(existing.getUnitsPerCarton(), source.getUnitsPerCarton())
                || !sameDecimal(existing.getCartonWeightKg(), source.getCartonWeightKg())
                || !sameDecimal(existing.getCartonVolumeCbm(), source.getCartonVolumeCbm());
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }
}
