package com.nuono.next.procurement;

import java.util.List;

class ChromeTab {

    int windowIndex;
    int tabIndex;
    String title;
    String url;

    static ChromeTab findCurrent(List<ChromeTab> tabs, ChromeTab selectedTab) {
        if (tabs == null || selectedTab == null) {
            return null;
        }
        return tabs.stream()
                .filter(tab -> tab != null
                        && tab.windowIndex == selectedTab.windowIndex
                        && tab.tabIndex == selectedTab.tabIndex)
                .findFirst()
                .orElse(null);
    }
}
