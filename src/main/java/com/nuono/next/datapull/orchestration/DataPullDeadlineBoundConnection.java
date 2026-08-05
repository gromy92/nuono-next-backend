package com.nuono.next.datapull.orchestration;

import org.springframework.jdbc.datasource.ConnectionProxy;

/** Marker exposed through JDBC unwrap even when MyBatis adds its logging proxy. */
interface DataPullDeadlineBoundConnection extends ConnectionProxy {
    DataPullAdvanceDeadline deadlineOwner();
}
