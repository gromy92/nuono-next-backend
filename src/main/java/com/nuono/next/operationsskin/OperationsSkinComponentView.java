package com.nuono.next.operationsskin;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OperationsSkinComponentView {
    private Long id;
    private String templateRole;
    private String componentKey;
    private String imageUrl;
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
    private Integer zIndex;
    private Boolean required;
    private Boolean locked;
    private String styleJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTemplateRole() {
        return templateRole;
    }

    public void setTemplateRole(String templateRole) {
        this.templateRole = templateRole;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public void setComponentKey(String componentKey) {
        this.componentKey = componentKey;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }

    public Integer getY() {
        return y;
    }

    public void setY(Integer y) {
        this.y = y;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    @JsonProperty("zIndex")
    public Integer getZIndex() {
        return zIndex;
    }

    @JsonProperty("zIndex")
    public void setZIndex(Integer zIndex) {
        this.zIndex = zIndex;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getStyleJson() {
        return styleJson;
    }

    public void setStyleJson(String styleJson) {
        this.styleJson = styleJson;
    }
}
