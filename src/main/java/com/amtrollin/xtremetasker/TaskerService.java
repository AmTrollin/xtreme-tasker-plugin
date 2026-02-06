package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;

import java.util.List;

public interface TaskerService
{
    boolean isOverlayEnabled();

    boolean hasTaskPackLoaded();

    XtremeTask getCurrentTask();

    TaskTier getCurrentTier();

    int getTierPercent(TaskTier tier);

    String getTierProgressLabel(TaskTier tier);

    List<XtremeTask> getDummyTasks();

    boolean isTaskCompleted(XtremeTask task);

    void toggleTaskCompletedAndPersist(XtremeTask task);

    void completeCurrentTaskAndPersist();

    void rollRandomTaskAndPersist();

    void reloadTaskPack();

    void pushGameMessage(String msg);
}
