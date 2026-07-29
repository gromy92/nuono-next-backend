package com.nuono.next.productlisting;

public class ProductListingOfficialTaxonomyRecord {

    private Long idProductFulltype;
    private String productFulltypeCode;
    private String familyNameEn;
    private String productTypeNameEn;
    private String productSubtypeNameEn;

    public Long getIdProductFulltype() {
        return idProductFulltype;
    }

    public void setIdProductFulltype(Long idProductFulltype) {
        this.idProductFulltype = idProductFulltype;
    }

    public String getProductFulltypeCode() {
        return productFulltypeCode;
    }

    public void setProductFulltypeCode(String productFulltypeCode) {
        this.productFulltypeCode = productFulltypeCode;
    }

    public String getFamilyNameEn() {
        return familyNameEn;
    }

    public void setFamilyNameEn(String familyNameEn) {
        this.familyNameEn = familyNameEn;
    }

    public String getProductTypeNameEn() {
        return productTypeNameEn;
    }

    public void setProductTypeNameEn(String productTypeNameEn) {
        this.productTypeNameEn = productTypeNameEn;
    }

    public String getProductSubtypeNameEn() {
        return productSubtypeNameEn;
    }

    public void setProductSubtypeNameEn(String productSubtypeNameEn) {
        this.productSubtypeNameEn = productSubtypeNameEn;
    }
}
