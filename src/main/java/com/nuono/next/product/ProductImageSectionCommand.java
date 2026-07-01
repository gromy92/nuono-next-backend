package com.nuono.next.product;

public class ProductImageSectionCommand {
    private Long id;
    private ProductImageSectionType sectionType;
    private String titleAr;
    private String titleEn;
    private String descriptionAr;
    private String descriptionEn;
    private String attributesText;
    private String focusPart;
    private Integer sortOrder;
    private Boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ProductImageSectionType getSectionType() {
        return sectionType;
    }

    public void setSectionType(ProductImageSectionType sectionType) {
        this.sectionType = sectionType;
    }

    public String getTitleAr() {
        return titleAr;
    }

    public void setTitleAr(String titleAr) {
        this.titleAr = titleAr;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getDescriptionAr() {
        return descriptionAr;
    }

    public void setDescriptionAr(String descriptionAr) {
        this.descriptionAr = descriptionAr;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getAttributesText() {
        return attributesText;
    }

    public void setAttributesText(String attributesText) {
        this.attributesText = attributesText;
    }

    public String getFocusPart() {
        return focusPart;
    }

    public void setFocusPart(String focusPart) {
        this.focusPart = focusPart;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
