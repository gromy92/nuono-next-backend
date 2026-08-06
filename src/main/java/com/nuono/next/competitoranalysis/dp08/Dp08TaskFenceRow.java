package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDateTime;

/** Locked runtime task ownership projection used by DP-08 fact transactions. */
public class Dp08TaskFenceRow {
    private Long id;
    private String operationCode;
    private String state;
    private Long fenceEpoch;
    private String leaseOwner;
    private LocalDateTime leaseUntil;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
}
