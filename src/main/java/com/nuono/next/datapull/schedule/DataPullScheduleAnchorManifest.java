package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical manifest used by the managed cutover and every runtime cohort check. */
public final class DataPullScheduleAnchorManifest {

    private DataPullScheduleAnchorManifest() {
    }

    public static String sha256(
            OperationCode operationCode,
            String cutoverKey,
            List<DataPullScheduleAnchor> anchors
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        String key = DataPullScheduleAnchor.requireIdentity(cutoverKey, "cutoverKey", 96);
        List<DataPullScheduleAnchor> ordered = new ArrayList<>(
                Objects.requireNonNull(anchors, "anchors")
        );
        ordered.sort(Comparator.comparing(DataPullScheduleAnchor::getScopeKey));
        DataPullScheduleAnchorManifestAccumulator digest =
                DataPullScheduleAnchorManifestAccumulator.initial(
                        operation, key, ordered.size()
                );
        for (DataPullScheduleAnchor anchor : ordered) {
            digest.append(Objects.requireNonNull(anchor, "anchor"));
        }
        return digest.finishHex();
    }
}
