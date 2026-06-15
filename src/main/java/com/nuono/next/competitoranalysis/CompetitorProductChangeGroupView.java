package com.nuono.next.competitoranalysis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class CompetitorProductChangeGroupView {
    private String id;
    private String factDate;
    private String noonProductCode;
    private String productName;
    private String subjectType;
    private List<CompetitorProductChangeFieldView> changes = new ArrayList<>();

    static CompetitorProductChangeGroupView fromRow(CompetitorProductChangeEventRow row) {
        CompetitorProductChangeGroupView view = new CompetitorProductChangeGroupView();
        String factDate = formatDate(row.getFactDate());
        view.setId(factDate + "-" + row.getNoonProductCode());
        view.setFactDate(factDate);
        view.setNoonProductCode(row.getNoonProductCode());
        view.setProductName(StringUtils.hasText(row.getProductName()) ? row.getProductName() : row.getNoonProductCode());
        view.setSubjectType(normalizeSubjectType(row.getSubjectType()));
        return view;
    }

    static String groupKey(CompetitorProductChangeEventRow row) {
        return formatDate(row.getFactDate()) + "|" + row.getNoonProductCode() + "|" + normalizeSubjectType(row.getSubjectType());
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String normalizeSubjectType(String value) {
        return "SELF".equalsIgnoreCase(value) ? "self" : "competitor";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFactDate() { return factDate; }
    public void setFactDate(String factDate) { this.factDate = factDate; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public List<CompetitorProductChangeFieldView> getChanges() { return changes; }
    public void setChanges(List<CompetitorProductChangeFieldView> changes) {
        this.changes = changes == null ? new ArrayList<>() : changes;
    }
}
