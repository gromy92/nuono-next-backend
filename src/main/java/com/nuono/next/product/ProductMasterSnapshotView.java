package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductMasterSnapshotView {

    private String mode;

    private boolean ready;

    private boolean degraded;

    private String message;

    private List<String> warnings = new ArrayList<>();

    private List<String> missingCoreTables = new ArrayList<>();

    private List<String> missingOperationalKeys = new ArrayList<>();

    private Map<String, Object> storeContext = new LinkedHashMap<>();

    private Map<String, Object> identity = new LinkedHashMap<>();

    private Map<String, Object> taxonomy = new LinkedHashMap<>();

    private Map<String, Object> content = new LinkedHashMap<>();

    private Map<String, Object> platformSignals = new LinkedHashMap<>();

    private List<Map<String, Object>> keyAttributes = new ArrayList<>();

    private Map<String, Object> group = new LinkedHashMap<>();

    private List<Map<String, Object>> variants = new ArrayList<>();

    private Map<String, Object> pricing = new LinkedHashMap<>();

    private Map<String, Object> stock = new LinkedHashMap<>();

    private List<Map<String, Object>> siteOffers = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
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
        this.warnings = warnings;
    }

    public List<String> getMissingCoreTables() {
        return missingCoreTables;
    }

    public void setMissingCoreTables(List<String> missingCoreTables) {
        this.missingCoreTables = missingCoreTables;
    }

    public List<String> getMissingOperationalKeys() {
        return missingOperationalKeys;
    }

    public void setMissingOperationalKeys(List<String> missingOperationalKeys) {
        this.missingOperationalKeys = missingOperationalKeys;
    }

    public Map<String, Object> getStoreContext() {
        return storeContext;
    }

    public void setStoreContext(Map<String, Object> storeContext) {
        this.storeContext = storeContext;
    }

    public Map<String, Object> getIdentity() {
        return identity;
    }

    public void setIdentity(Map<String, Object> identity) {
        this.identity = identity;
    }

    public Map<String, Object> getTaxonomy() {
        return taxonomy;
    }

    public void setTaxonomy(Map<String, Object> taxonomy) {
        this.taxonomy = taxonomy;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public Map<String, Object> getPlatformSignals() {
        return platformSignals;
    }

    public void setPlatformSignals(Map<String, Object> platformSignals) {
        this.platformSignals = platformSignals;
    }

    public List<Map<String, Object>> getKeyAttributes() {
        return keyAttributes;
    }

    public void setKeyAttributes(List<Map<String, Object>> keyAttributes) {
        this.keyAttributes = keyAttributes;
    }

    public Map<String, Object> getGroup() {
        return group;
    }

    public void setGroup(Map<String, Object> group) {
        this.group = group;
    }

    public List<Map<String, Object>> getVariants() {
        return variants;
    }

    public void setVariants(List<Map<String, Object>> variants) {
        this.variants = variants;
    }

    public Map<String, Object> getPricing() {
        return pricing;
    }

    public void setPricing(Map<String, Object> pricing) {
        this.pricing = pricing;
    }

    public Map<String, Object> getStock() {
        return stock;
    }

    public void setStock(Map<String, Object> stock) {
        this.stock = stock;
    }

    public List<Map<String, Object>> getSiteOffers() {
        return siteOffers;
    }

    public void setSiteOffers(List<Map<String, Object>> siteOffers) {
        this.siteOffers = siteOffers;
    }
}
