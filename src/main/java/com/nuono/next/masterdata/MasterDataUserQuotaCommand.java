package com.nuono.next.masterdata;

public class MasterDataUserQuotaCommand {

    private Integer listLimit;

    private Integer collectLimit;

    private Integer whApLimit;

    private Integer chatgptTranslateLimit;

    private Long operatorUserId;

    public Integer getListLimit() {
        return listLimit;
    }

    public void setListLimit(Integer listLimit) {
        this.listLimit = listLimit;
    }

    public Integer getCollectLimit() {
        return collectLimit;
    }

    public void setCollectLimit(Integer collectLimit) {
        this.collectLimit = collectLimit;
    }

    public Integer getWhApLimit() {
        return whApLimit;
    }

    public void setWhApLimit(Integer whApLimit) {
        this.whApLimit = whApLimit;
    }

    public Integer getChatgptTranslateLimit() {
        return chatgptTranslateLimit;
    }

    public void setChatgptTranslateLimit(Integer chatgptTranslateLimit) {
        this.chatgptTranslateLimit = chatgptTranslateLimit;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
