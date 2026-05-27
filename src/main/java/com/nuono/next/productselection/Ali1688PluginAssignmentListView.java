package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class Ali1688PluginAssignmentListView {

    public List<Ali1688PluginAssignmentView> items = new ArrayList<>();
    public Summary summary = new Summary();
    public Diagnostics diagnostics = new Diagnostics();
    public String emptyReason;
    public String message;
    public String refreshedAt;

    public static class Summary {
        public int total;
        public int pending;
        public int running;
        public int synced;
        public int blockedOrFailed;
    }

    public static class Diagnostics {
        public boolean apiAvailable = true;
        public boolean bearerSessionValid = true;
        public int visibleTaskCount;
        public int assignableTaskCount;
        public int issuedAssignmentCount;
        public int missingSourceImageCount;
        public int expiredAssignmentCount;
        public String version = "ALI1688_PLUGIN_ASSIGNMENT_LIST_V1";
    }
}
