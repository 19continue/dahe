package com.dahe.v2.modules.miniapp.search.controller;

import com.dahe.v2.common.Result;
import com.dahe.v2.modules.miniapp.search.model.MiniappSearchSuggestionItem;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchSuggestionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

@RestController
@RequestMapping("/api/v2/miniapp/search")
@Validated
public class MiniappSearchSuggestionController {

    private final MiniappSearchSuggestionService miniappSearchSuggestionService;

    public MiniappSearchSuggestionController(MiniappSearchSuggestionService miniappSearchSuggestionService) {
        this.miniappSearchSuggestionService = miniappSearchSuggestionService;
    }

    @GetMapping("/suggestions")
    public Result<List<MiniappSearchSuggestionItem>> suggestions(
            @RequestParam String scene,
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "6") @Min(1) @Max(10) Integer limit
    ) {
        return Result.success(miniappSearchSuggestionService.listSuggestions(scene, keyword, limit));
    }
}
