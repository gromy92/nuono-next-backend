package com.nuono.next.procurement.aliorder.datapull;

/** Why one exact DP-10 generation is temporarily allowed to be cross-table incomplete. */
public enum Ali1688Dp10StageCleanupReason {
    CURRENT_GENERATION,
    OLDER_GENERATION,
    FAILED_RETENTION
}
