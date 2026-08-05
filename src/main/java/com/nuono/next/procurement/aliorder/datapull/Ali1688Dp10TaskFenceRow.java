package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;

/** Locked projection used to prove the live DP-10 task epoch before fact writes. */
public class Ali1688Dp10TaskFenceRow {

    private Long id;
    private OperationCode operationCode;
    private Long ownerUserId;
    private String accountKey;
    private String scopeKey;
    private TaskState state;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Long fenceEpoch;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode operationCode) { this.operationCode = operationCode; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getAccountKey() { return accountKey; }
    public void setAccountKey(String accountKey) { this.accountKey = accountKey; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public TaskState getState() { return state; }
    public void setState(TaskState state) { this.state = state; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
