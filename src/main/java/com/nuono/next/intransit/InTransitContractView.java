package com.nuono.next.intransit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InTransitContractView {

    private List<InTransitEnumOptionView> transportModes;
    private List<InTransitEnumOptionView> batchStatuses;
    private List<InTransitEnumOptionView> nodeStatuses;
    private List<InTransitEnumOptionView> qualityStatuses;
    private List<String> purchaseOrderFields;
    private List<String> feeFields;

    public static InTransitContractView build() {
        InTransitContractView view = new InTransitContractView();
        view.setTransportModes(Arrays.stream(InTransitTransportMode.values())
                .map(mode -> new InTransitEnumOptionView(mode.code(), mode.label()))
                .collect(Collectors.toList()));
        view.setBatchStatuses(Arrays.stream(InTransitBatchStatus.values())
                .map(status -> new InTransitEnumOptionView(status.code(), status.label()))
                .collect(Collectors.toList()));
        view.setNodeStatuses(Arrays.stream(InTransitNodeStatus.values())
                .map(status -> new InTransitEnumOptionView(status.code(), status.label()))
                .collect(Collectors.toList()));
        view.setQualityStatuses(Arrays.stream(InTransitQualityStatus.values())
                .map(status -> new InTransitEnumOptionView(status.code(), status.label()))
                .collect(Collectors.toList()));
        view.setPurchaseOrderFields(Collections.emptyList());
        view.setFeeFields(Collections.emptyList());
        return view;
    }

    public List<String> transportModeCodes() {
        return transportModes == null
                ? Collections.emptyList()
                : transportModes.stream().map(InTransitEnumOptionView::getCode).collect(Collectors.toList());
    }

    public List<InTransitEnumOptionView> getTransportModes() {
        return transportModes;
    }

    public void setTransportModes(List<InTransitEnumOptionView> transportModes) {
        this.transportModes = transportModes;
    }

    public List<InTransitEnumOptionView> getBatchStatuses() {
        return batchStatuses;
    }

    public void setBatchStatuses(List<InTransitEnumOptionView> batchStatuses) {
        this.batchStatuses = batchStatuses;
    }

    public List<InTransitEnumOptionView> getNodeStatuses() {
        return nodeStatuses;
    }

    public void setNodeStatuses(List<InTransitEnumOptionView> nodeStatuses) {
        this.nodeStatuses = nodeStatuses;
    }

    public List<InTransitEnumOptionView> getQualityStatuses() {
        return qualityStatuses;
    }

    public void setQualityStatuses(List<InTransitEnumOptionView> qualityStatuses) {
        this.qualityStatuses = qualityStatuses;
    }

    public List<String> getPurchaseOrderFields() {
        return purchaseOrderFields;
    }

    public void setPurchaseOrderFields(List<String> purchaseOrderFields) {
        this.purchaseOrderFields = purchaseOrderFields;
    }

    public List<String> getFeeFields() {
        return feeFields;
    }

    public void setFeeFields(List<String> feeFields) {
        this.feeFields = feeFields;
    }
}
