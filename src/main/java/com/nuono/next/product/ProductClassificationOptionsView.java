package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductClassificationOptionsView {

    private boolean ready;
    private String source;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private List<ProductClassificationOptionRecord> brands = new ArrayList<>();
    private List<ProductClassificationOptionRecord> fulltypes = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public List<ProductClassificationOptionRecord> getBrands() {
        return brands;
    }

    public void setBrands(List<ProductClassificationOptionRecord> brands) {
        this.brands = brands == null ? new ArrayList<>() : brands;
    }

    public List<ProductClassificationOptionRecord> getFulltypes() {
        return fulltypes;
    }

    public void setFulltypes(List<ProductClassificationOptionRecord> fulltypes) {
        this.fulltypes = fulltypes == null ? new ArrayList<>() : fulltypes;
    }
}
