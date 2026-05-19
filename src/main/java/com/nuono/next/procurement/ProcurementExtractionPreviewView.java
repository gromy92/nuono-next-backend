package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.FieldEvidenceView;
import java.util.ArrayList;
import java.util.List;

public class ProcurementExtractionPreviewView {

    private boolean ready;

    private String message;

    private String title;

    private String supplierName;

    private String materialText;

    private String powerModeText;

    private String sizeText;

    private String packageText;

    private String deliveryTimelineText;

    private String structuredFieldSource;

    private List<FieldEvidenceView> extractionEvidences = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getMaterialText() {
        return materialText;
    }

    public void setMaterialText(String materialText) {
        this.materialText = materialText;
    }

    public String getPowerModeText() {
        return powerModeText;
    }

    public void setPowerModeText(String powerModeText) {
        this.powerModeText = powerModeText;
    }

    public String getSizeText() {
        return sizeText;
    }

    public void setSizeText(String sizeText) {
        this.sizeText = sizeText;
    }

    public String getPackageText() {
        return packageText;
    }

    public void setPackageText(String packageText) {
        this.packageText = packageText;
    }

    public String getDeliveryTimelineText() {
        return deliveryTimelineText;
    }

    public void setDeliveryTimelineText(String deliveryTimelineText) {
        this.deliveryTimelineText = deliveryTimelineText;
    }

    public String getStructuredFieldSource() {
        return structuredFieldSource;
    }

    public void setStructuredFieldSource(String structuredFieldSource) {
        this.structuredFieldSource = structuredFieldSource;
    }

    public List<FieldEvidenceView> getExtractionEvidences() {
        return extractionEvidences;
    }

    public void setExtractionEvidences(List<FieldEvidenceView> extractionEvidences) {
        this.extractionEvidences = extractionEvidences;
    }
}
