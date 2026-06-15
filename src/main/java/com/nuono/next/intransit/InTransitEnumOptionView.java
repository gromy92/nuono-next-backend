package com.nuono.next.intransit;

public class InTransitEnumOptionView {

    private String code;
    private String label;

    public InTransitEnumOptionView() {
    }

    public InTransitEnumOptionView(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
