package com.dahe.v2.modules.miniapp.search.service;

import com.dahe.v2.modules.miniapp.search.model.MiniappSearchSuggestionItem;

import java.util.List;

public interface MiniappSearchSuggestionService {

    List<MiniappSearchSuggestionItem> listSuggestions(String scene, String keyword, Integer limit);
}
