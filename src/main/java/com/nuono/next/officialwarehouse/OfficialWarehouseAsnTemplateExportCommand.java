package com.nuono.next.officialwarehouse;

import java.util.ArrayList;
import java.util.List;

public class OfficialWarehouseAsnTemplateExportCommand {
    public String storeCode;
    public String siteCode;
    public List<Line> lines = new ArrayList<>();

    public static class Line {
        public String partnerSku;
        public Integer quantity;
    }
}
