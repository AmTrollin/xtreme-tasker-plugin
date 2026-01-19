package com.amtrollin.xtremetasker.ui.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

public final class TaskRowsLayout
{
    public final Rectangle viewportBounds = new Rectangle();
    public final Map<XtremeTask, Rectangle> rowBounds = new HashMap<>();
}
