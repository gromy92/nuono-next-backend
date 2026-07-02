package com.nuono.next.noonads;

public enum NoonAdvertisingProductDiagnosisType {
    STOP_LOSS("优先止损"),
    PROMOTE_TO_CORE("可沉淀核心"),
    CORE_OBSERVE("核心可观察"),
    STRUCTURE_REVIEW("结构待整理"),
    INSUFFICIENT_DATA("样本不足");

    private final String label;

    NoonAdvertisingProductDiagnosisType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
