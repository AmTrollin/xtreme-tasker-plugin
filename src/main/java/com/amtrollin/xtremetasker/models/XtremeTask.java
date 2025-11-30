package com.amtrollin.xtremetasker.models;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;

public class XtremeTask {
    private final String id;
    private final String name;
    private final TaskSource source;
    private final TaskTier tier;

    public XtremeTask(String id, String name, TaskSource source, TaskTier tier) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.tier = tier;
    }

    public String getId() {return id;}

    public String getName() {return name;}

    public TaskSource getSource() {return source;}

    public TaskTier getTier() {return tier;}

    @Override
    public String toString() {return name;}
}
