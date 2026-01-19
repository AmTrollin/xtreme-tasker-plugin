package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;

import java.util.Comparator;

public final class TaskListSorter
{
    private TaskListSorter() {}

    public static Comparator<XtremeTask> comparator(TaskListQuery q, TaskListFilter.CompletionLookup completed)
    {
        final boolean completedFirst = q.completedFirst;

        return (a, b) ->
        {
            boolean aDone = completed.isCompleted(a);
            boolean bDone = completed.isCompleted(b);

            int aKey = aDone ? 1 : 0;
            int bKey = bDone ? 1 : 0;

            if (completedFirst)
            {
                aKey = 1 - aKey;
                bKey = 1 - bKey;
            }

            int cmp = Integer.compare(aKey, bKey);
            if (cmp != 0)
            {
                return cmp;
            }

            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            return an.compareToIgnoreCase(bn);
        };
    }
}
