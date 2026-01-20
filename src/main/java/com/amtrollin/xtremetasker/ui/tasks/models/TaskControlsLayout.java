package com.amtrollin.xtremetasker.ui.tasks.models;

import java.awt.Rectangle;

public class TaskControlsLayout
{
    public final Rectangle searchBox = new Rectangle();

    // Source (single select): ALL / CA / CLOGs
    public final Rectangle filterSourceAll = new Rectangle();
    public final Rectangle filterCA = new Rectangle();
    public final Rectangle filterCL = new Rectangle();

    // Status (single select): ALL / Incomplete / Complete
    public final Rectangle filterStatusAll = new Rectangle();
    public final Rectangle filterIncomplete = new Rectangle();
    public final Rectangle filterComplete = new Rectangle();

    public final Rectangle filterTierThis = new Rectangle();
    public final Rectangle filterTierAll = new Rectangle();

    public final Rectangle sortCompletion = new Rectangle();
    public final Rectangle sortTier = new Rectangle();
    public final Rectangle sortReset = new Rectangle();
    public String hoverTooltipText = null;
    public final Rectangle hoverTooltipAnchor = new Rectangle(); // optional



}
