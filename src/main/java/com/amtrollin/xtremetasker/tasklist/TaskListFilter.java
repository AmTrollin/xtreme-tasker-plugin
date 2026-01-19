package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;

import java.util.function.Predicate;

public final class TaskListFilter
{
    private TaskListFilter() {}

    @FunctionalInterface
    public interface CompletionLookup
    {
        boolean isCompleted(XtremeTask task);
    }

    public static Predicate<XtremeTask> build(TaskListQuery q, CompletionLookup completed)
    {
        final boolean allowCA = q.filterCA;
        final boolean allowCL = q.filterCL;

        final boolean allowIncomplete = q.filterIncomplete;
        final boolean allowComplete = q.filterComplete;

        return t ->
        {
            // --- Source filter ---
            TaskSource s = t.getSource();
            if (s == TaskSource.COMBAT_ACHIEVEMENT && !allowCA)
            {
                return false;
            }
            if (s == TaskSource.COLLECTION_LOG && !allowCL)
            {
                return false;
            }

            // --- Completion filter ---
            boolean isDone = completed.isCompleted(t);
            if (isDone && !allowComplete)
            {
                return false;
            }
            return isDone || allowIncomplete;
        };
    }
}
