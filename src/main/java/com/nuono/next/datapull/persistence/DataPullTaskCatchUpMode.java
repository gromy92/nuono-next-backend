package com.nuono.next.datapull.persistence;

/** The two catch-up shapes that atomically replace strictly never-started tasks. */
public enum DataPullTaskCatchUpMode {
    LATEST_CURRENT,
    ROLLING_DATE_UNION
}
