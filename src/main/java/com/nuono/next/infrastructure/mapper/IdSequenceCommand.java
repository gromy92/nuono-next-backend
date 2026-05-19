package com.nuono.next.infrastructure.mapper;

public class IdSequenceCommand {
    private String sequenceName;
    private Long initialValue;
    private Long allocatedId;

    public IdSequenceCommand(String sequenceName, Long initialValue) {
        this.sequenceName = sequenceName;
        this.initialValue = initialValue;
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public void setSequenceName(String sequenceName) {
        this.sequenceName = sequenceName;
    }

    public Long getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(Long initialValue) {
        this.initialValue = initialValue;
    }

    public Long getAllocatedId() {
        return allocatedId;
    }

    public void setAllocatedId(Long allocatedId) {
        this.allocatedId = allocatedId;
    }
}
