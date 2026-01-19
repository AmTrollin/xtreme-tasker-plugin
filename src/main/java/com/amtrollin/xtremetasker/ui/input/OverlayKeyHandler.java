package com.amtrollin.xtremetasker.ui.input;

import lombok.RequiredArgsConstructor;
import net.runelite.client.input.KeyListener;

import java.awt.event.KeyEvent;

@RequiredArgsConstructor
public final class OverlayKeyHandler implements KeyListener
{
    private final OverlayInputAccess a;

    @Override
    public void keyPressed(KeyEvent e)
    {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen())
        {
            return;
        }

        int code = e.getKeyCode();

        if (code == KeyEvent.VK_ESCAPE)
        {
            a.setPanelOpen(false);
            a.setDraggingPanel(false);
            e.consume();
            return;
        }

        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS)
        {
            if (a.taskQuery().searchFocused)
            {
                if (a.handleSearchKey(e))
                {
                    e.consume();
                }
                return;
            }

            if (a.handleTasksKey(e))
            {
                e.consume();
            }
            return;
        }

        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT)
        {
            if (a.handleCurrentKey(e))
            {
                e.consume();
            }
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}
