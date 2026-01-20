package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;

import java.util.Comparator;

public final class TaskListSorter
{
    private TaskListSorter() {}

    public static Comparator<XtremeTask> comparator(TaskListQuery q, TaskListFilter.CompletionLookup completed)
    {
        final boolean sortByCompletion = q.sortByCompletion;
        final boolean completedFirst = q.completedFirst;

        // Only meaningful when all tiers are shown (you’re already enforcing via UI)
        final boolean sortByTier = q.sortByTier && q.tierScope == TaskListQuery.TierScope.ALL_TIERS;

        return (a, b) ->
        {
            // 1) Completion sort (optional)
            if (sortByCompletion)
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
            }

            if (sortByTier)
            {
                int aTier = tierRank(a);
                int bTier = tierRank(b);

                // If easyTierFirst = true, sort ascending (EASY...MASTER...GM).
                // If false, sort descending (GM...MASTER...EASY).
                int cmp = q.easyTierFirst
                        ? Integer.compare(aTier, bTier)
                        : Integer.compare(bTier, aTier);

                if (cmp != 0)
                {
                    return cmp;
                }
            }


            // 3) Always alphabetical fallback
            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            return an.compareToIgnoreCase(bn);
        };
    }

    private static int tierRank(XtremeTask t)
    {
        // Adjust to your real model API.
        // If you have TaskTier enum with natural order EASY..GM, ordinal() works.
        // If you want the opposite direction, flip it.
        if (t.getTier() == null)
        {
            return Integer.MAX_VALUE;
        }
        return t.getTier().ordinal();
    }

}
