package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

public class ProductListingKeywordSuggestionCommand {
    private List<String> english = new ArrayList<>();
    private List<String> arabic = new ArrayList<>();

    public List<String> getEnglish() {
        return english;
    }

    public void setEnglish(List<String> english) {
        this.english = english == null ? new ArrayList<>() : new ArrayList<>(english);
    }

    public List<String> getArabic() {
        return arabic;
    }

    public void setArabic(List<String> arabic) {
        this.arabic = arabic == null ? new ArrayList<>() : new ArrayList<>(arabic);
    }
}
