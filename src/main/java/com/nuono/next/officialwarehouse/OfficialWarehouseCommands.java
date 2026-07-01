package com.nuono.next.officialwarehouse;

import java.util.ArrayList;
import java.util.List;

public final class OfficialWarehouseCommands {

    private OfficialWarehouseCommands() {
    }

    public static class CreateAsnCommand {
        public String storeCode;
        public String siteCode;
        public String sourceType;
        public List<String> shippingBatchIds = new ArrayList<>();
        public List<CreateAsnLineCommand> lines = new ArrayList<>();
    }

    public static class CreateAsnLineCommand {
        public Long productVariantId;
        public Long productSiteOfferId;
        public String partnerSku;
        public Integer quantity;
    }

    public static class UpsertAppointmentCommand {
        public String warehouseFrom;
        public String warehouseToPartnerCode;
        public String warehouseToCode;
        public String apStartDate;
        public String apEndDate;
        public String apTimeRange;
        public Boolean availableToday;
        public String appointmentDate;
        public Integer appointmentSlotId;
        public String appointmentTime;
    }

    public static class CorrectAppointmentCommand {
        public String status;
        public String appointmentDate;
        public Integer appointmentSlotId;
        public String appointmentTime;
        public String failureType;
        public String errorStage;
        public String errorMessage;
    }
}
