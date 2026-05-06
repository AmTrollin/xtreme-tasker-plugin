package com.amtrollin.xtremetasker.verification;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class CombatAchievementService
{
    private static final int TASKS_PER_VARPLAYER = 32;
    private static final int CA_TASK_COMPLETED_BASE = 2943;
    private static final int CA_TASK_COMPLETED_COUNT = 20;

    @Inject
    private Client client;

    public boolean isTaskComplete(int taskId)
    {
        int varPlayerIndex = taskId / TASKS_PER_VARPLAYER;
        int bitPosition = taskId % TASKS_PER_VARPLAYER;

        if (varPlayerIndex < 0 || varPlayerIndex >= CA_TASK_COMPLETED_COUNT)
        {
            log.warn("Invalid combat achievement task ID: {}", taskId);
            return false;
        }

        int varPlayerId = CA_TASK_COMPLETED_BASE + varPlayerIndex;
        int varPlayerValue = client.getVarpValue(varPlayerId);
        return (varPlayerValue & (1 << bitPosition)) != 0;
    }
}
