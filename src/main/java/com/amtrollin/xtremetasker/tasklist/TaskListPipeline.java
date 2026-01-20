package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.*;
import java.util.regex.Pattern;


public final class TaskListPipeline {
    private TaskListPipeline() {
    }

    public static List<XtremeTask> apply(
            List<XtremeTask> input,
            TaskListQuery query,
            TaskListFilter.CompletionLookup completed
    ) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }

        // Defensive defaults
        if (query == null) {
            query = new TaskListQuery();
        }

        String q = (query.searchText == null) ? "" : query.searchText.trim();

        // Build filters (CA/CL/incomplete/complete/etc)
        Predicate<XtremeTask> filterPred = TaskListFilter.build(query, completed);

        // Pre-tokenize the user's query once
        final List<String> queryTerms = keywordTokens(q);

        List<XtremeTask> out = new ArrayList<>(input.size());
        for (XtremeTask t : input) {
            // Search (name/description/prereqs keyword tokens; ignores stopwords)
            if (!matchesKeywordSearch(queryTerms, t)) {
                continue;
            }

            // Other filters
            if (!filterPred.test(t)) {
                continue;
            }

            out.add(t);
        }

        out.sort(TaskListSorter.comparator(query, completed));
        return out;
    }


    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at",
            "be", "by",
            "for", "from",
            "in", "into",
            "is", "it", "its",
            "of", "on", "or",
            "the",
            "to",
            "with"
    ));

    private static List<String> keywordTokens(String s) {
        if (s == null || s.isEmpty()) {
            return Collections.emptyList();
        }

        String lower = s.toLowerCase(Locale.ROOT);
        String[] raw = TOKEN_SPLIT.split(lower);

        ArrayList<String> out = new ArrayList<>(raw.length);
        for (String tok : raw) {
            if (tok == null) continue;
            if (tok.length() < 2) continue;           // drops "a", "i", etc.
            if (STOP_WORDS.contains(tok)) continue;   // drops filler
            out.add(tok);
        }
        return out;
    }

    public static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Keyword-search match against a task.
     * AND semantics: every query term must appear in name/description/prereqs tokens.
     */
    private static boolean matchesKeywordSearch(List<String> queryTerms, XtremeTask t) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return true;
        }
        if (t == null) {
            return false;
        }

        ArrayList<String> realTerms = new ArrayList<>();
        for (String term : queryTerms) {
            if (term.length() >= 3) {
                realTerms.add(term);
            }
        }
        if (realTerms.isEmpty()) {
            return true;
        }

        ArrayList<String> hay = new ArrayList<>();
        hay.addAll(keywordTokens(safe(t.getName())));
        hay.addAll(keywordTokens(safe(t.getDescription())));
        hay.addAll(keywordTokens(safe(t.getPrereqs())));

        for (String term : realTerms) {
            boolean matched = false;
            for (String tok : hay) {
                if (tok.startsWith(term)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }


}
