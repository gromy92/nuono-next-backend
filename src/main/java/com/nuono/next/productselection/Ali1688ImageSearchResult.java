package com.nuono.next.productselection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Ali1688ImageSearchResult {

    public String searchMode;
    public String officialSearchUrl;
    public String searchImageId;
    public List<String> searchImageIds = new ArrayList<>();
    public String rawSnapshotJson;
    public List<Candidate> candidates = new ArrayList<>();

    public static class Candidate {
        public String offerId;
        public String candidateUrl;
        public String title;
        public String supplierName;
        public String priceText;
        public BigDecimal priceMin;
        public BigDecimal priceMax;
        public String moqText;
        public Integer moqValue;
        public String locationText;
        public String mainImageUrl;
        public List<String> imageUrls = new ArrayList<>();
        public Map<String, Object> badges;
        public Map<String, Object> skuSnapshot;
        public Map<String, Object> supplierSnapshot;
        public Map<String, Object> logisticsSnapshot;
    }
}
