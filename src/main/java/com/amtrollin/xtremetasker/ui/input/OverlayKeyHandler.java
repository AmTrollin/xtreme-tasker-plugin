package com.amtrollin.xtremetasker.ui.input;

import lombok.RequiredArgsConstructor;
import net.runelite.client.input.KeyListener;

import java.awt.event.KeyEvent;

@RequiredArgsConstructor
public final class OverlayKeyHandler implements KeyListener {
    private final OverlayInputAccess a;

    @Override
    public void keyPressed(KeyEvent e) {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen()) {
            return;
        }

        int code = e.getKeyCode();

        // ESC always closes panel (even if search focused)
        if (code == KeyEvent.VK_ESCAPE) {
            a.setPanelOpen(false);
            a.setDraggingPanel(false);
            e.consume();
            return;
        }

        // If search is focused, handle ONLY non-text keys here
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && a.taskQuery().searchFocused) {
            if (code == KeyEvent.VK_BACK_SPACE) {
                String s = a.taskQuery().searchText;
                if (s != null && !s.isEmpty()) {
                    a.taskQuery().searchText = s.substring(0, s.length() - 1);
                    a.resetTaskListViewAfterQueryChange();
                }
                e.consume();
                return;
            }

            if (code == KeyEvent.VK_ENTER) {
                a.taskQuery().searchFocused = false;
                e.consume();
                return;
            }

            // Let keyTyped handle actual characters.
            return;
        }

        // Normal key handling when search is NOT focused
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS)
        {
            if (a.handleTasksKey(e))
            {
                // If the key changed selection/scroll, keep selection visible (keyboard UX)
                // IMPORTANT: do NOT do this during wheel scrolling; this is keyboard-only.
                int total = a.getSortedTasksForTier(a.activeTier()).size();
                int viewportH = a.taskListViewportBounds().height;
                int rowBlock = a.taskRowBlock();
                if (total > 0 && viewportH > 0 && rowBlock > 0)
                {
                    a.taskListView().ensureSelectionVisible(total, viewportH, rowBlock);
                }
                a.taskListView().ensureSelectionVisible(total, viewportH, rowBlock);

                e.consume();
            }
            return;
        }


        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT) {
            if (a.handleCurrentKey(e)) {
                e.consume();
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen()) {
            return;
        }

        if (a.activeTab() != OverlayInputAccess.MainTab.TASKS || !a.taskQuery().searchFocused) {
            return;
        }

        char c = e.getKeyChar();

        // Ignore control characters
        if (c < 32 || c == 127) {
            return;
        }

        // Optional: normalize to lower-case so search feels consistent
        c = Character.toLowerCase(c);

        String s = a.taskQuery().searchText;
        if (s == null) {
            s = "";
        }

        if (s.length() < 40) {
            a.taskQuery().searchText = s + c;
            a.resetTaskListViewAfterQueryChange();
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }
}
