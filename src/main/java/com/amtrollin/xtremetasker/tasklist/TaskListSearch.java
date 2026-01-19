package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;

public final class TaskListSearch
{
    private TaskListSearch() {}

    public static boolean matches(XtremeTask t, String queryLower)
    {
        if (queryLower == null || queryLower.isEmpty())
        {
            return true;
        }

        return containsLower(t.getName(), queryLower)
                || containsLower(t.getDescription(), queryLower)
                || containsLower(t.getPrereqs(), queryLower);
    }

    private static boolean containsLower(String haystack, String needleLower)
    {
        if (haystack == null || haystack.isEmpty())
        {
            return false;
        }
        return haystack.toLowerCase().contains(needleLower);
    }
}
