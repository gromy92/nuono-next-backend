package com.nuono.next.productselection;

public class ProductSelectionCompetitorCategoryLink {

    private String name;
    private String path;
    private String url;

    public ProductSelectionCompetitorCategoryLink() {
    }

    public ProductSelectionCompetitorCategoryLink(String name, String path, String url) {
        this.name = name;
        this.path = path;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
