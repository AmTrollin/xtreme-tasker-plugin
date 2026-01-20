package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;

import java.util.function.Predicate;

public final class TaskListFilter
{
    private TaskListFilter() {}

    public interface CompletionLookup
    {
        boolean isCompleted(XtremeTask task);
    }

    public static Predicate<XtremeTask> build(TaskListQuery query, CompletionLookup completed)
    {
        if (query == null)
        {
            query = new TaskListQuery();
        }

        final TaskListQuery.SourceFilter sourceFilter = query.sourceFilter;
        final TaskListQuery.StatusFilter statusFilter = query.statusFilter;

        return t ->
        {
            if (t == null)
            {
                return false;
            }

            // -------------------------
            // 1) Source filter
            // -------------------------
            if (sourceFilter != null && sourceFilter != TaskListQuery.SourceFilter.ALL)
            {
                boolean ok;
                switch (sourceFilter)
                {
                    case CA:
                        ok = isCombatAchievementTask(t);
                        break;
                    case CLOGS:
                        ok = isCollectionLogTask(t);
                        break;
                    default:
                        ok = true;
                        break;
                }

                if (!ok)
                {
                    return false;
                }
            }

            // -------------------------
            // 2) Status filter
            // -------------------------
            if (statusFilter != null && statusFilter != TaskListQuery.StatusFilter.ALL)
            {
                boolean isDone = completed != null && completed.isCompleted(t);

                boolean ok;
                switch (statusFilter)
                {
                    case INCOMPLETE:
                        ok = !isDone;
                        break;
                    case COMPLETE:
                        ok = isDone;
                        break;
                    default:
                        ok = true;
                        break;
                }

                return ok;
            }

            return true;
        };
    }

    // =========================================================
    // SOURCE DETECTION
    // Update these two methods to match your actual XtremeTask model.
    // =========================================================

    private static boolean isCombatAchievementTask(XtremeTask t)
    {
        // Example implementations — pick ONE that matches your codebase:

        // If you have an enum/source field:
        // return t.getSource() == TaskSource.COMBAT_ACHIEVEMENT;

        // If you store a string source:
        // return "COMBAT_ACHIEVEMENT".equalsIgnoreCase(t.getSource());

        // If you infer from tier/fields:
        // return t.getCombatAchievementTier() != null;

        // TEMP fallback: try best-effort inference from existing fields
        String src = safeLower(String.valueOf(t.getSource()));
        if (!src.isEmpty())
        {
            return src.contains("combat");
        }

        return false;
    }

    private static boolean isCollectionLogTask(XtremeTask t)
    {
        // Example implementations — pick ONE that matches your codebase:

        // If you have an enum/source field:
        // return t.getSource() == TaskSource.COLLECTION_LOG;

        // If you store a string source:
        // return "COLLECTION_LOG".equalsIgnoreCase(t.getSource());

        // TEMP fallback: try best-effort inference from existing fields
        String src = safeLower(String.valueOf(t.getSource()));
        if (!src.isEmpty())
        {
            return src.contains("collection");
        }

        return false;
    }

    private static String safeLower(String s)
    {
        return s == null ? "" : s.toLowerCase();
    }
}
