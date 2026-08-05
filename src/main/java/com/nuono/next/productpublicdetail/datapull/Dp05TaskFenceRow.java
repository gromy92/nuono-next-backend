package com.nuono.next.productpublicdetail.datapull;

/** Locked task projection used by the DP-05 item fact transaction. */
public class Dp05TaskFenceRow {
    private Long taskId;
    private String operationCode;
    private String state;
    private Long fenceEpoch;
    private String leaseOwner;
    private Boolean leaseValid;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Boolean getLeaseValid() { return leaseValid; }
    public void setLeaseValid(Boolean leaseValid) { this.leaseValid = leaseValid; }
}
