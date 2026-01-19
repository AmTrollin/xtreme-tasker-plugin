package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class TaskListPipeline
{
    private TaskListPipeline() {}

    public static List<XtremeTask> apply(
            List<XtremeTask> input,
            TaskListQuery query,
            TaskListFilter.CompletionLookup completed
    )
    {
        if (input == null || input.isEmpty())
        {
            return new ArrayList<>();
        }

        String q = (query.searchText == null) ? "" : query.searchText.trim();
        String qLower = q.isEmpty() ? "" : q.toLowerCase();

        Predicate<XtremeTask> filterPred = TaskListFilter.build(query, completed);

        List<XtremeTask> out = new ArrayList<>(input.size());
        for (XtremeTask t : input)
        {
            if (!TaskListSearch.matches(t, qLower))
            {
                continue;
            }
            if (!filterPred.test(t))
            {
                continue;
            }
            out.add(t);
        }

        out.sort(TaskListSorter.comparator(query, completed));
        return out;
    }
}
