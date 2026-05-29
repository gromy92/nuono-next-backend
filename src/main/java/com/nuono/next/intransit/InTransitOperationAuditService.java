package com.nuono.next.intransit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchRecords.OperationAuditRow;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InTransitOperationAuditService {

    private final InTransitGoodsMapper mapper;
    private final ObjectMapper objectMapper;

    public InTransitOperationAuditService(InTransitGoodsMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void record(AuditCommand command) {
        if (command == null || command.getOwnerUserId() == null || command.getOwnerUserId() <= 0) {
            throw new IllegalArgumentException("缺少老板账号范围。");
        }
        if (!StringUtils.hasText(command.getOperationType()) || !StringUtils.hasText(command.getTargetType())) {
            throw new IllegalArgumentException("缺少在途商品审计操作类型。");
        }
        OperationAuditRow row = new OperationAuditRow();
        row.setId(mapper.nextOperationAuditId());
        row.setOwnerUserId(command.getOwnerUserId());
        row.setOperatorUserId(command.getOperatorUserId());
        row.setOperationType(command.getOperationType());
        row.setTargetType(command.getTargetType());
        row.setTargetId(command.getTargetId());
        row.setBatchId(command.getBatchId());
        row.setStoreCode(clean(command.getStoreCode()));
        row.setSiteCode(clean(command.getSiteCode()));
        row.setSummary(clean(command.getSummary()));
        row.setDetailJson(command.getDetail() == null || command.getDetail().isEmpty() ? null : writeJson(command.getDetail()));
        row.setCreatedBy(command.getOperatorUserId());
        mapper.insertOperationAudit(row);
    }

    private String writeJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("在途商品操作审计序列化失败。", exception);
        }
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static class AuditCommand {
        private Long ownerUserId;
        private Long operatorUserId;
        private String operationType;
        private String targetType;
        private Long targetId;
        private Long batchId;
        private String storeCode;
        private String siteCode;
        private String summary;
        private Map<String, Object> detail;

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public Long getTargetId() { return targetId; }
        public void setTargetId(Long targetId) { this.targetId = targetId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public Map<String, Object> getDetail() { return detail; }
        public void setDetail(Map<String, Object> detail) { this.detail = detail; }
    }
}
