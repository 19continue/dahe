package com.dahe.v2.modules.miniapp.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.extra.pinyin.PinyinUtil;
import com.dahe.v2.modules.miniapp.search.model.MiniappSearchSuggestionItem;
import com.dahe.v2.modules.miniapp.search.model.MiniappSearchHighlightRange;
import com.dahe.v2.modules.miniapp.search.model.MiniappSearchTerm;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchSuggestionService;
import com.dahe.v2.modules.miniapp.search.service.MiniappSearchTermService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MiniappSearchSuggestionServiceImpl implements MiniappSearchSuggestionService {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 10;
    private static final int CANDIDATE_LIMIT_FACTOR = 8;
    private static final int MIN_CONTAINS_LENGTH = 2;
    private static final int MAX_FUZZY_LENGTH = 8;
    private static final int MAX_PINYIN_FUZZY_LENGTH = 12;
    private static final int MAX_PREFIX_TOKENS = 6;
    private static final int MAX_CONTAINS_TOKENS = 5;

    private final MiniappSearchTermService miniappSearchTermService;

    public MiniappSearchSuggestionServiceImpl(MiniappSearchTermService miniappSearchTermService) {
        this.miniappSearchTermService = miniappSearchTermService;
    }

    @Override
    public List<MiniappSearchSuggestionItem> listSuggestions(String scene, String keyword, Integer limit) {
        String normalizedScene = normalizeScene(scene);
        String normalizedKeyword = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalizedScene) || !StringUtils.hasText(normalizedKeyword)) {
            return Collections.emptyList();
        }
        int safeLimit = normalizeLimit(limit);
        List<MiniappSearchTerm> candidates = queryCandidates(normalizedScene, normalizedKeyword, safeLimit);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        return rankSuggestions(candidates, normalizedKeyword, safeLimit);
    }

    private List<MiniappSearchTerm> queryCandidates(String scene, String keyword, int limit) {
        int candidateLimit = candidateLimit(limit);
        int prefixBudget = Math.max(limit * 3, 12);
        Set<String> prefixTokens = buildPrefixTokens(keyword);
        Set<String> containsTokens = buildContainsTokens(keyword);
        boolean pinyinMode = isPinyinLike(keyword);
        Map<String, MiniappSearchTerm> merged = new LinkedHashMap<>();
        appendCandidates(merged, queryByPrefix(scene, prefixTokens, prefixBudget, pinyinMode), prefixBudget);
        if (shouldRunContains(keyword)) {
            appendCandidates(merged, queryByContains(scene, containsTokens, Math.max(limit * 2, 12), pinyinMode), candidateLimit);
        }
        if (shouldRunLooseFallback(keyword, merged.size(), limit)) {
            appendCandidates(merged, queryByLooseFallback(scene, buildLooseFallbackTokens(keyword), Math.max(limit * 2, 12)), candidateLimit);
        }
        return new ArrayList<>(merged.values());
    }

    private List<MiniappSearchTerm> queryByPrefix(String scene, Set<String> tokens, int limit, boolean includePinyin) {
        List<String> normalizedTokens = cleanTokens(tokens, false);
        if (normalizedTokens.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<MiniappSearchTerm> wrapper = new LambdaQueryWrapper<MiniappSearchTerm>()
                .select(
                        MiniappSearchTerm::getId,
                        MiniappSearchTerm::getTypeLabel,
                        MiniappSearchTerm::getLabel,
                        MiniappSearchTerm::getValueText,
                        MiniappSearchTerm::getSearchText,
                        MiniappSearchTerm::getSearchCompact,
                        MiniappSearchTerm::getPinyinFull,
                        MiniappSearchTerm::getPinyinInitials,
                        MiniappSearchTerm::getSortWeight,
                        MiniappSearchTerm::getTermKeyHash
                )
                .eq(MiniappSearchTerm::getSceneKey, scene);
        wrapper.and(w -> {
            boolean first = true;
            for (String token : normalizedTokens) {
                if (first) {
                    w.likeRight(MiniappSearchTerm::getSearchCompact, token)
                            .or().likeRight(MiniappSearchTerm::getSearchText, token);
                    if (includePinyin) {
                        w.or().likeRight(MiniappSearchTerm::getPinyinFull, token)
                                .or().likeRight(MiniappSearchTerm::getPinyinInitials, token);
                    }
                    first = false;
                    continue;
                }
                w.or().likeRight(MiniappSearchTerm::getSearchCompact, token)
                        .or().likeRight(MiniappSearchTerm::getSearchText, token);
                if (includePinyin) {
                    w.or().likeRight(MiniappSearchTerm::getPinyinFull, token)
                            .or().likeRight(MiniappSearchTerm::getPinyinInitials, token);
                }
            }
        });
        wrapper.orderByDesc(MiniappSearchTerm::getSortWeight)
                .orderByDesc(MiniappSearchTerm::getUpdatedAt)
                .last("limit " + limit);
        return miniappSearchTermService.list(wrapper);
    }

    private List<MiniappSearchTerm> queryByContains(String scene, Set<String> tokens, int limit, boolean includePinyin) {
        List<String> normalizedTokens = cleanTokens(tokens, true);
        if (normalizedTokens.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<MiniappSearchTerm> wrapper = new LambdaQueryWrapper<MiniappSearchTerm>()
                .select(
                        MiniappSearchTerm::getId,
                        MiniappSearchTerm::getTypeLabel,
                        MiniappSearchTerm::getLabel,
                        MiniappSearchTerm::getValueText,
                        MiniappSearchTerm::getSearchText,
                        MiniappSearchTerm::getSearchCompact,
                        MiniappSearchTerm::getPinyinFull,
                        MiniappSearchTerm::getPinyinInitials,
                        MiniappSearchTerm::getSortWeight,
                        MiniappSearchTerm::getTermKeyHash
                )
                .eq(MiniappSearchTerm::getSceneKey, scene);
        wrapper.and(w -> {
            boolean first = true;
            for (String token : normalizedTokens) {
                if (first) {
                    w.like(MiniappSearchTerm::getSearchCompact, token)
                            .or().like(MiniappSearchTerm::getSearchText, token);
                    if (includePinyin) {
                        w.or().like(MiniappSearchTerm::getPinyinFull, token)
                                .or().like(MiniappSearchTerm::getPinyinInitials, token);
                    }
                    first = false;
                    continue;
                }
                w.or().like(MiniappSearchTerm::getSearchCompact, token)
                        .or().like(MiniappSearchTerm::getSearchText, token);
                if (includePinyin) {
                    w.or().like(MiniappSearchTerm::getPinyinFull, token)
                            .or().like(MiniappSearchTerm::getPinyinInitials, token);
                }
            }
        });
        wrapper.orderByDesc(MiniappSearchTerm::getSortWeight)
                .orderByDesc(MiniappSearchTerm::getUpdatedAt)
                .last("limit " + limit);
        return miniappSearchTermService.list(wrapper);
    }

    private List<MiniappSearchTerm> queryByLooseFallback(String scene, Set<String> tokens, int limit) {
        List<String> normalizedTokens = cleanTokens(tokens, false);
        if (normalizedTokens.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<MiniappSearchTerm> wrapper = new LambdaQueryWrapper<MiniappSearchTerm>()
                .select(
                        MiniappSearchTerm::getId,
                        MiniappSearchTerm::getTypeLabel,
                        MiniappSearchTerm::getLabel,
                        MiniappSearchTerm::getValueText,
                        MiniappSearchTerm::getSearchText,
                        MiniappSearchTerm::getSearchCompact,
                        MiniappSearchTerm::getPinyinFull,
                        MiniappSearchTerm::getPinyinInitials,
                        MiniappSearchTerm::getSortWeight,
                        MiniappSearchTerm::getTermKeyHash
                )
                .eq(MiniappSearchTerm::getSceneKey, scene);
        wrapper.and(w -> {
            boolean first = true;
            for (String token : normalizedTokens) {
                if (first) {
                    w.like(MiniappSearchTerm::getSearchCompact, token)
                            .or().like(MiniappSearchTerm::getSearchText, token);
                    first = false;
                    continue;
                }
                w.or().like(MiniappSearchTerm::getSearchCompact, token)
                        .or().like(MiniappSearchTerm::getSearchText, token);
            }
        });
        wrapper.orderByDesc(MiniappSearchTerm::getSortWeight)
                .orderByDesc(MiniappSearchTerm::getUpdatedAt)
                .last("limit " + limit);
        return miniappSearchTermService.list(wrapper);
    }

    private void appendCandidates(Map<String, MiniappSearchTerm> merged, List<MiniappSearchTerm> rows, int maxSize) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (MiniappSearchTerm row : rows) {
            if (row == null || !StringUtils.hasText(row.getTermKeyHash())) {
                continue;
            }
            merged.putIfAbsent(row.getTermKeyHash(), row);
            if (merged.size() >= maxSize) {
                return;
            }
        }
    }

    private List<MiniappSearchSuggestionItem> rankSuggestions(List<MiniappSearchTerm> rows, String keyword, int limit) {
        Map<String, RankedSuggestion> dedup = new LinkedHashMap<>();
        for (MiniappSearchTerm row : rows) {
            if (row == null || !StringUtils.hasText(row.getLabel()) || !StringUtils.hasText(row.getTypeLabel())) {
                continue;
            }
            MatchScore score = scoreSuggestion(keyword, row);
            if (!score.isMatched()) {
                continue;
            }
            String key = buildSuggestionDedupKey(row);
            RankedSuggestion current = dedup.get(key);
            RankedSuggestion candidate = new RankedSuggestion(row, score.getScore(), key, score.getHighlightRanges());
            if (current == null || candidate.getScore() > current.getScore()) {
                dedup.put(key, candidate);
            }
        }
        if (dedup.isEmpty()) {
            return Collections.emptyList();
        }
        List<RankedSuggestion> ranked = new ArrayList<>(dedup.values());
        ranked.sort(Comparator
                .comparingInt(RankedSuggestion::getScore).reversed()
                .thenComparingInt(item -> item.getRow().getLabel().length())
                .thenComparing(item -> item.getRow().getLabel(), Comparator.nullsLast(String::compareTo)));
        List<MiniappSearchSuggestionItem> out = new ArrayList<>();
        for (RankedSuggestion item : ranked.subList(0, Math.min(limit, ranked.size()))) {
            MiniappSearchSuggestionItem row = new MiniappSearchSuggestionItem();
            row.setKey(item.getKey());
            row.setTypeLabel(item.getRow().getTypeLabel());
            row.setLabel(item.getRow().getLabel());
            row.setValue(item.getRow().getValueText());
            row.setHighlightRanges(item.getHighlightRanges());
            out.add(row);
        }
        return out;
    }

    private MatchScore scoreSuggestion(String keyword, MiniappSearchTerm row) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String visibleLabel = firstNonBlank(row.getLabel(), row.getSearchText());
        String normalizedLabel = firstNonBlank(row.getSearchText(), row.getLabel());
        String compactKeyword = compact(normalizedKeyword);
        LabelMatchContext context = new LabelMatchContext(visibleLabel);
        String compactLabel = firstNonBlank(row.getSearchCompact(), context.getCompact().getText());
        String pinyinFull = firstNonBlank(compact(row.getPinyinFull()), context.getPinyinFull().getText());
        String pinyinInitials = firstNonBlank(compact(row.getPinyinInitials()), context.getPinyinInitials().getText());
        boolean pinyinMode = isPinyinLike(normalizedKeyword);
        if (!StringUtils.hasText(normalizedKeyword) || !StringUtils.hasText(normalizedLabel)) {
            return MatchScore.unmatched();
        }
        int score = safeWeight(row.getSortWeight());
        if (normalizedLabel.equals(normalizedKeyword) || compactLabel.equals(compactKeyword)) {
            return MatchScore.matched(score + 5000, context.wholeRanges());
        }
        if (normalizedLabel.startsWith(normalizedKeyword) || compactLabel.startsWith(compactKeyword)) {
            List<MiniappSearchHighlightRange> ranges = normalizedLabel.startsWith(normalizedKeyword)
                    ? context.rangesForRawSpan(0, normalizedKeyword.length())
                    : context.rangesForProjectionSpan(context.getCompact(), 0, compactKeyword.length());
            return MatchScore.matched(score + 3600 - normalizedLabel.length(), ranges);
        }
        if (normalizedLabel.endsWith(normalizedKeyword) || compactLabel.endsWith(compactKeyword)) {
            List<MiniappSearchHighlightRange> ranges = normalizedLabel.endsWith(normalizedKeyword)
                    ? context.rangesForRawSpan(Math.max(0, visibleLabel.length() - normalizedKeyword.length()), normalizedKeyword.length())
                    : context.rangesForProjectionSpan(context.getCompact(), Math.max(0, compactLabel.length() - compactKeyword.length()), compactKeyword.length());
            return MatchScore.matched(score + 3200 - normalizedLabel.length(), ranges);
        }
        if (pinyinMode) {
            if (StringUtils.hasText(pinyinFull) && pinyinFull.equals(compactKeyword)) {
                return MatchScore.matched(score + 3300 - normalizedLabel.length(), context.wholeRanges());
            }
            if (StringUtils.hasText(pinyinInitials) && pinyinInitials.equals(compactKeyword)) {
                return MatchScore.matched(score + 3150 - normalizedLabel.length(), context.wholeRanges());
            }
            if (StringUtils.hasText(pinyinFull) && pinyinFull.startsWith(compactKeyword)) {
                return MatchScore.matched(score + 2900 - pinyinFull.length(), context.rangesForProjectionSpan(context.getPinyinFull(), 0, compactKeyword.length()));
            }
            if (StringUtils.hasText(pinyinInitials) && pinyinInitials.startsWith(compactKeyword)) {
                return MatchScore.matched(score + 2700 - pinyinInitials.length(), context.rangesForProjectionSpan(context.getPinyinInitials(), 0, compactKeyword.length()));
            }
            if ((StringUtils.hasText(pinyinFull) && pinyinFull.endsWith(compactKeyword))
                    || (StringUtils.hasText(pinyinInitials) && pinyinInitials.endsWith(compactKeyword))) {
                TextProjection projection = StringUtils.hasText(pinyinFull) && pinyinFull.endsWith(compactKeyword)
                        ? context.getPinyinFull()
                        : context.getPinyinInitials();
                String projectionText = projection.getText();
                return MatchScore.matched(score + 2400 - normalizedLabel.length(),
                        context.rangesForProjectionSpan(projection, Math.max(0, projectionText.length() - compactKeyword.length()), compactKeyword.length()));
            }
        }
        int rawIndex = normalizedLabel.indexOf(normalizedKeyword);
        int compactIndex = compactLabel.indexOf(compactKeyword);
        if (rawIndex >= 0 || compactIndex >= 0) {
            int hitIndex = rawIndex >= 0 ? rawIndex : compactIndex;
            List<MiniappSearchHighlightRange> ranges = rawIndex >= 0
                    ? context.rangesForRawSpan(rawIndex, normalizedKeyword.length())
                    : context.rangesForProjectionSpan(context.getCompact(), compactIndex, compactKeyword.length());
            return MatchScore.matched(score + 2600 - hitIndex * 8, ranges);
        }
        if (pinyinMode) {
            int pinyinFullIndex = StringUtils.hasText(pinyinFull) ? pinyinFull.indexOf(compactKeyword) : -1;
            int pinyinInitialIndex = StringUtils.hasText(pinyinInitials) ? pinyinInitials.indexOf(compactKeyword) : -1;
            if (pinyinFullIndex >= 0 || pinyinInitialIndex >= 0) {
                int hitIndex = pinyinFullIndex >= 0 ? pinyinFullIndex : pinyinInitialIndex;
                int bonus = pinyinFullIndex >= 0 ? 2200 : 2000;
                TextProjection projection = pinyinFullIndex >= 0 ? context.getPinyinFull() : context.getPinyinInitials();
                int start = pinyinFullIndex >= 0 ? pinyinFullIndex : pinyinInitialIndex;
                return MatchScore.matched(score + bonus - hitIndex * 6, context.rangesForProjectionSpan(projection, start, compactKeyword.length()));
            }
        }
        if (compactKeyword.length() >= MIN_CONTAINS_LENGTH && orderedContains(compactLabel, compactKeyword)) {
            return MatchScore.matched(score + 1800 - compactLabel.length(), context.rangesForOrderedProjection(context.getCompact(), compactKeyword));
        }
        if (pinyinMode && compactKeyword.length() >= MIN_CONTAINS_LENGTH) {
            if (StringUtils.hasText(pinyinFull) && orderedContains(pinyinFull, compactKeyword)) {
                return MatchScore.matched(score + 1600 - pinyinFull.length(), context.rangesForOrderedProjection(context.getPinyinFull(), compactKeyword));
            }
            if (StringUtils.hasText(pinyinInitials) && orderedContains(pinyinInitials, compactKeyword)) {
                return MatchScore.matched(score + 1450 - pinyinInitials.length(), context.rangesForOrderedProjection(context.getPinyinInitials(), compactKeyword));
            }
        }
        if (compactKeyword.length() >= MIN_CONTAINS_LENGTH
                && compactKeyword.length() <= MAX_FUZZY_LENGTH
                && (distanceWithinOne(compactKeyword, compactLabel) || transpositionWithinOne(compactKeyword, compactLabel))) {
            return MatchScore.matched(score + 1200 - compactLabel.length(), context.rangesForSharedProjection(context.getCompact(), compactKeyword));
        }
        if (pinyinMode
                && compactKeyword.length() >= MIN_CONTAINS_LENGTH
                && compactKeyword.length() <= MAX_PINYIN_FUZZY_LENGTH
                && ((StringUtils.hasText(pinyinFull)
                        && (distanceWithinOne(compactKeyword, pinyinFull) || transpositionWithinOne(compactKeyword, pinyinFull)))
                    || (StringUtils.hasText(pinyinInitials)
                        && (distanceWithinOne(compactKeyword, pinyinInitials) || transpositionWithinOne(compactKeyword, pinyinInitials))))) {
            TextProjection projection = StringUtils.hasText(pinyinFull)
                    && (distanceWithinOne(compactKeyword, pinyinFull) || transpositionWithinOne(compactKeyword, pinyinFull))
                    ? context.getPinyinFull()
                    : context.getPinyinInitials();
            return MatchScore.matched(score + 1050 - normalizedLabel.length(), context.rangesForSharedProjection(projection, compactKeyword));
        }
        int compactShared = sharedCharacterCount(compactLabel, compactKeyword);
        if (compactKeyword.length() <= 4 && compactShared >= Math.max(1, compactKeyword.length() - 1)) {
            return MatchScore.matched(score + 900 - compactLabel.length(), context.rangesForSharedProjection(context.getCompact(), compactKeyword));
        }
        if (pinyinMode) {
            int pinyinShared = Math.max(sharedCharacterCount(pinyinFull, compactKeyword), sharedCharacterCount(pinyinInitials, compactKeyword));
            if (compactKeyword.length() <= 5 && pinyinShared >= Math.max(1, compactKeyword.length() - 1)) {
                TextProjection projection = sharedCharacterCount(pinyinFull, compactKeyword) >= sharedCharacterCount(pinyinInitials, compactKeyword)
                        ? context.getPinyinFull()
                        : context.getPinyinInitials();
                return MatchScore.matched(score + 820 - normalizedLabel.length(), context.rangesForSharedProjection(projection, compactKeyword));
            }
        }
        return MatchScore.unmatched();
    }

    private Set<String> buildPrefixTokens(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String compact = compact(normalized);
        addToken(out, compact, MAX_PREFIX_TOKENS);
        addToken(out, normalized, MAX_PREFIX_TOKENS);
        if (compact.length() >= 3 && compact.length() <= 8) {
            for (int i = 0; i < compact.length(); i++) {
                String variant = compact.substring(0, i) + compact.substring(i + 1);
                if (StringUtils.hasText(variant)) {
                    addToken(out, variant, MAX_PREFIX_TOKENS);
                    if (out.size() >= MAX_PREFIX_TOKENS) {
                        break;
                    }
                }
            }
        }
        return out;
    }

    private Set<String> buildContainsTokens(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String compact = compact(normalized);
        addToken(out, compact, MAX_CONTAINS_TOKENS);
        addToken(out, normalized, MAX_CONTAINS_TOKENS);
        addFragmentTokens(out, compact, 3, MAX_CONTAINS_TOKENS);
        addFragmentTokens(out, compact, 2, MAX_CONTAINS_TOKENS);
        String[] parts = normalized.split("[\\s·•、,，;；/\\\\()（）\\-_.]+");
        for (String part : parts) {
            String safePart = compact(part);
            if (StringUtils.hasText(safePart) && safePart.length() >= MIN_CONTAINS_LENGTH) {
                addToken(out, safePart, MAX_CONTAINS_TOKENS);
                if (out.size() >= MAX_CONTAINS_TOKENS) {
                    break;
                }
            }
        }
        if (compact.length() >= 3) {
            int start = Math.max(0, compact.length() - 3);
            addToken(out, compact.substring(start), MAX_CONTAINS_TOKENS);
        }
        if (compact.length() >= 4) {
            int start = Math.max(0, compact.length() - 4);
            addToken(out, compact.substring(start), MAX_CONTAINS_TOKENS);
        }
        if (compact.length() >= 3 && compact.length() <= MAX_FUZZY_LENGTH) {
            for (int i = 0; i < compact.length(); i++) {
                String variant = compact.substring(0, i) + compact.substring(i + 1);
                addToken(out, variant, MAX_CONTAINS_TOKENS);
                if (out.size() >= MAX_CONTAINS_TOKENS) {
                    break;
                }
            }
        }
        return out;
    }

    private Set<String> buildLooseFallbackTokens(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String compact = compact(normalized);
        addFragmentTokens(out, compact, 2, Math.max(MAX_CONTAINS_TOKENS, 6));
        if (compact.length() <= 4) {
            addFragmentTokens(out, compact, 1, Math.max(MAX_CONTAINS_TOKENS, 6));
        }
        return out;
    }

    private boolean shouldRunLooseFallback(String keyword, int currentSize, int limit) {
        String compactKeyword = compact(keyword);
        if (!StringUtils.hasText(compactKeyword) || currentSize >= limit) {
            return false;
        }
        return compactKeyword.length() <= 4;
    }

    private void addToken(Set<String> out, String token, int maxSize) {
        if (out == null || out.size() >= maxSize || !StringUtils.hasText(token)) {
            return;
        }
        out.add(token);
    }

    private void addFragmentTokens(Set<String> out, String compact, int fragmentSize, int maxSize) {
        if (out == null || !StringUtils.hasText(compact) || compact.length() < fragmentSize) {
            return;
        }
        for (int i = 0; i <= compact.length() - fragmentSize; i++) {
            addToken(out, compact.substring(i, i + fragmentSize), maxSize);
            if (out.size() >= maxSize) {
                return;
            }
        }
    }

    private List<String> cleanTokens(Set<String> tokens, boolean forContains) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String safe = token.trim();
            if (forContains && safe.length() < MIN_CONTAINS_LENGTH) {
                continue;
            }
            out.add(safe);
        }
        return out;
    }

    private boolean orderedContains(String label, String keyword) {
        if (!StringUtils.hasText(label) || !StringUtils.hasText(keyword)) {
            return false;
        }
        int cursor = 0;
        for (int i = 0; i < keyword.length(); i++) {
            cursor = label.indexOf(keyword.charAt(i), cursor);
            if (cursor < 0) {
                return false;
            }
            cursor += 1;
        }
        return true;
    }

    private boolean transpositionWithinOne(String keyword, String label) {
        if (!StringUtils.hasText(keyword) || !StringUtils.hasText(label) || keyword.length() != label.length()) {
            return false;
        }
        int first = -1;
        int second = -1;
        for (int i = 0; i < keyword.length(); i++) {
            if (keyword.charAt(i) == label.charAt(i)) {
                continue;
            }
            if (first < 0) {
                first = i;
                continue;
            }
            if (second < 0) {
                second = i;
                continue;
            }
            return false;
        }
        if (first < 0 || second < 0 || second != first + 1) {
            return false;
        }
        return keyword.charAt(first) == label.charAt(second)
                && keyword.charAt(second) == label.charAt(first);
    }

    private boolean distanceWithinOne(String keyword, String label) {
        if (!StringUtils.hasText(keyword) || !StringUtils.hasText(label)) {
            return false;
        }
        if (Math.abs(label.length() - keyword.length()) > 1) {
            return false;
        }
        if (label.length() == keyword.length()) {
            int mismatch = 0;
            for (int i = 0; i < label.length(); i++) {
                if (label.charAt(i) != keyword.charAt(i) && ++mismatch > 1) {
                    return false;
                }
            }
            return true;
        }
        String shorter = label.length() < keyword.length() ? label : keyword;
        String longer = label.length() < keyword.length() ? keyword : label;
        int i = 0;
        int j = 0;
        int mismatch = 0;
        while (i < shorter.length() && j < longer.length()) {
            if (shorter.charAt(i) == longer.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++mismatch > 1) {
                return false;
            }
            j++;
        }
        return true;
    }

    private int sharedCharacterCount(String label, String keyword) {
        if (!StringUtils.hasText(label) || !StringUtils.hasText(keyword)) {
            return 0;
        }
        int cursor = 0;
        int matched = 0;
        for (int i = 0; i < keyword.length(); i++) {
            cursor = label.indexOf(keyword.charAt(i), cursor);
            if (cursor < 0) {
                continue;
            }
            matched += 1;
            cursor += 1;
        }
        return matched;
    }

    private boolean isPinyinLike(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            char current = keyword.charAt(i);
            if ((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9') || current == ' ') {
                continue;
            }
            return false;
        }
        return true;
    }

    private String buildSuggestionDedupKey(MiniappSearchTerm row) {
        return String.join("|",
                String.valueOf(firstNonBlank(row.getTypeLabel(), "")),
                String.valueOf(firstNonBlank(row.getLabel(), "")),
                String.valueOf(firstNonBlank(row.getValueText(), "")));
    }

    private String normalizeScene(String scene) {
        String value = String.valueOf(scene == null ? "" : scene).trim().toLowerCase(Locale.ROOT);
        if ("field".equals(value) || "field-picker".equals(value) || "seed-batch".equals(value)) {
            return value;
        }
        return null;
    }

    private String normalizeKeyword(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private String compact(String raw) {
        String normalized = normalizeKeyword(raw);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String compacted = normalized.replaceAll("[\\s·•、,，;；/\\\\()（）\\-_.]+", "");
        return compacted.isEmpty() ? normalized : compacted;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean shouldRunContains(String keyword) {
        String compactKeyword = compact(keyword);
        return compactKeyword.length() >= MIN_CONTAINS_LENGTH;
    }

    private int candidateLimit(int limit) {
        return Math.max(limit * CANDIDATE_LIMIT_FACTOR, 16);
    }

    private int safeWeight(Integer weight) {
        return weight == null ? 0 : weight;
    }

    private static final class RankedSuggestion {
        private final MiniappSearchTerm row;
        private final int score;
        private final String key;
        private final List<MiniappSearchHighlightRange> highlightRanges;

        private RankedSuggestion(MiniappSearchTerm row, int score, String key, List<MiniappSearchHighlightRange> highlightRanges) {
            this.row = row;
            this.score = score;
            this.key = key;
            this.highlightRanges = highlightRanges;
        }

        public MiniappSearchTerm getRow() {
            return row;
        }

        public int getScore() {
            return score;
        }

        public String getKey() {
            return key;
        }

        public List<MiniappSearchHighlightRange> getHighlightRanges() {
            return highlightRanges;
        }
    }

    private static final class MatchScore {
        private final boolean matched;
        private final int score;
        private final List<MiniappSearchHighlightRange> highlightRanges;

        private MatchScore(boolean matched, int score, List<MiniappSearchHighlightRange> highlightRanges) {
            this.matched = matched;
            this.score = score;
            this.highlightRanges = highlightRanges;
        }

        public static MatchScore matched(int score, List<MiniappSearchHighlightRange> highlightRanges) {
            return new MatchScore(true, score, highlightRanges);
        }

        public static MatchScore unmatched() {
            return new MatchScore(false, Integer.MIN_VALUE, Collections.emptyList());
        }

        public boolean isMatched() {
            return matched;
        }

        public int getScore() {
            return score;
        }

        public List<MiniappSearchHighlightRange> getHighlightRanges() {
            return highlightRanges;
        }
    }

    private final class LabelMatchContext {
        private final String label;
        private final TextProjection compact;
        private final TextProjection pinyinFull;
        private final TextProjection pinyinInitials;

        private LabelMatchContext(String label) {
            this.label = StringUtils.hasText(label) ? label : "";
            this.compact = buildCompactProjection(this.label);
            this.pinyinFull = buildPinyinProjection(this.label, false);
            this.pinyinInitials = buildPinyinProjection(this.label, true);
        }

        public TextProjection getCompact() {
            return compact;
        }

        public TextProjection getPinyinFull() {
            return pinyinFull;
        }

        public TextProjection getPinyinInitials() {
            return pinyinInitials;
        }

        public List<MiniappSearchHighlightRange> wholeRanges() {
            return label.isEmpty() ? Collections.emptyList() : rangesFromIndices(Collections.singletonList(0), label.length() - 1);
        }

        public List<MiniappSearchHighlightRange> rangesForRawSpan(int start, int length) {
            if (length <= 0 || start < 0 || start >= label.length()) {
                return Collections.emptyList();
            }
            int end = Math.min(label.length(), start + length);
            return rangesFromIndices(Collections.singletonList(start), end - 1);
        }

        public List<MiniappSearchHighlightRange> rangesForProjectionSpan(TextProjection projection, int start, int length) {
            if (projection == null || length <= 0 || start < 0 || start >= projection.getText().length()) {
                return Collections.emptyList();
            }
            int end = Math.min(projection.getText().length(), start + length);
            LinkedHashSet<Integer> indices = new LinkedHashSet<>();
            for (int i = start; i < end; i++) {
                indices.add(projection.getOriginIndex(i));
            }
            return rangesFromIndices(indices, null);
        }

        public List<MiniappSearchHighlightRange> rangesForOrderedProjection(TextProjection projection, String keyword) {
            if (projection == null || !StringUtils.hasText(keyword)) {
                return Collections.emptyList();
            }
            LinkedHashSet<Integer> indices = new LinkedHashSet<>();
            int cursor = 0;
            for (int i = 0; i < keyword.length(); i++) {
                int hit = projection.getText().indexOf(keyword.charAt(i), cursor);
                if (hit < 0) {
                    continue;
                }
                indices.add(projection.getOriginIndex(hit));
                cursor = hit + 1;
            }
            return rangesFromIndices(indices, null);
        }

        public List<MiniappSearchHighlightRange> rangesForSharedProjection(TextProjection projection, String keyword) {
            if (projection == null || !StringUtils.hasText(keyword)) {
                return Collections.emptyList();
            }
            LinkedHashSet<Integer> indices = new LinkedHashSet<>();
            int cursor = 0;
            for (int i = 0; i < keyword.length(); i++) {
                int hit = projection.getText().indexOf(keyword.charAt(i), cursor);
                if (hit < 0) {
                    continue;
                }
                indices.add(projection.getOriginIndex(hit));
                cursor = hit + 1;
            }
            return rangesFromIndices(indices, null);
        }

        private List<MiniappSearchHighlightRange> rangesFromIndices(Iterable<Integer> rawIndices, Integer terminalEnd) {
            List<Integer> indices = new ArrayList<>();
            for (Integer index : rawIndices) {
                if (index == null || index < 0 || index >= label.length()) {
                    continue;
                }
                indices.add(index);
            }
            if (terminalEnd != null && terminalEnd >= 0) {
                for (int i = indices.isEmpty() ? 0 : indices.get(0); i <= terminalEnd; i++) {
                    if (i >= 0 && i < label.length()) {
                        indices.add(i);
                    }
                }
            }
            if (indices.isEmpty()) {
                return Collections.emptyList();
            }
            Collections.sort(indices);
            List<MiniappSearchHighlightRange> ranges = new ArrayList<>();
            int start = indices.get(0);
            int prev = start;
            for (int i = 1; i < indices.size(); i++) {
                int current = indices.get(i);
                if (current <= prev + 1) {
                    prev = Math.max(prev, current);
                    continue;
                }
                ranges.add(createRange(start, prev + 1));
                start = current;
                prev = current;
            }
            ranges.add(createRange(start, prev + 1));
            return ranges;
        }

        private MiniappSearchHighlightRange createRange(int start, int end) {
            MiniappSearchHighlightRange range = new MiniappSearchHighlightRange();
            range.setStart(start);
            range.setEnd(end);
            return range;
        }

        private TextProjection buildCompactProjection(String value) {
            StringBuilder text = new StringBuilder();
            List<Integer> origin = new ArrayList<>();
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                if (isSeparator(current)) {
                    continue;
                }
                text.append(Character.toLowerCase(current));
                origin.add(i);
            }
            return new TextProjection(text.toString(), origin);
        }

        private TextProjection buildPinyinProjection(String value, boolean initials) {
            StringBuilder text = new StringBuilder();
            List<Integer> origin = new ArrayList<>();
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                if (isSeparator(current)) {
                    continue;
                }
                String token = initials ? toInitial(String.valueOf(current)) : toPinyin(String.valueOf(current));
                if (!StringUtils.hasText(token)) {
                    token = String.valueOf(Character.toLowerCase(current));
                }
                for (int j = 0; j < token.length(); j++) {
                    text.append(token.charAt(j));
                    origin.add(i);
                }
            }
            return new TextProjection(text.toString(), origin);
        }

        private String toPinyin(String value) {
            try {
                return compact(PinyinUtil.getPinyin(value, ""));
            } catch (Exception ignored) {
                return null;
            }
        }

        private String toInitial(String value) {
            try {
                return compact(PinyinUtil.getFirstLetter(value, ""));
            } catch (Exception ignored) {
                return null;
            }
        }

        private boolean isSeparator(char current) {
            return Character.isWhitespace(current)
                    || "·•、,，;；/\\()（）-_. ".indexOf(current) >= 0;
        }
    }

    private final class TextProjection {
        private final String text;
        private final List<Integer> originIndices;

        private TextProjection(String text, List<Integer> originIndices) {
            this.text = text == null ? "" : text;
            this.originIndices = originIndices == null ? Collections.emptyList() : originIndices;
        }

        public String getText() {
            return text;
        }

        public int getOriginIndex(int projectionIndex) {
            if (projectionIndex < 0 || projectionIndex >= originIndices.size()) {
                return -1;
            }
            return originIndices.get(projectionIndex);
        }
    }
}
