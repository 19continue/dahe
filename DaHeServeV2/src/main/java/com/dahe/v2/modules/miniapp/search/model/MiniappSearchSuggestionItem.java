package com.dahe.v2.modules.miniapp.search.model;

import lombok.Data;

@Data
public class MiniappSearchSuggestionItem {

    private String key;

    private String typeLabel;

    private String label;

    private String value;

    private java.util.List<MiniappSearchHighlightRange> highlightRanges;
}
