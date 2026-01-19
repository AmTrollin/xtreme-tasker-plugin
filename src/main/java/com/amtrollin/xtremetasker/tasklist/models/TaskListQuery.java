package com.amtrollin.xtremetasker.tasklist.models;

public class TaskListQuery {
    public String searchText = "";
    public boolean searchFocused = false;

    public boolean filterCA = true;
    public boolean filterCL = true;
    public boolean filterIncomplete = true;
    public boolean filterComplete = true;

    public boolean completedFirst = false;
}
