package com.arc1s.clansync;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Small, movable on-screen box with the current clan task: boss (in the difficulty colour), a progress bar with
 * "12/50 (24%)", and "Target reached - press Done" once the website says the target is reached.
 * Hidden when there is no task or the "Show task overlay" option is off.
 */
class ClanTaskOverlay extends OverlayPanel
{
	static final int WIDTH = 150;
	static final Color REACHED_COLOR = new Color(0x66BB6A);
	static final Color WILDY_COLOR = new Color(0xFF5252);
	private static final Color BAR_BACKGROUND = new Color(0x3C, 0x3C, 0x3C, 200);

	private final BooleanSupplier visible;
	private volatile ClanTask task;

	ClanTaskOverlay(Plugin plugin, BooleanSupplier visible)
	{
		super(plugin);
		this.visible = visible;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
	}

	void setTask(ClanTask task)
	{
		this.task = task;
	}

	ClanTask getTask()
	{
		return task;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		ClanTask current = task;
		if (current == null || !visible.getAsBoolean())
		{
			return null;
		}
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
		fill(panelComponent.getChildren(), current);
		return super.render(g);
	}

	/** The lines for one task (also used by the tests). */
	static void fill(List<LayoutableRenderableEntity> children, ClanTask task)
	{
		Color color = ClanTask.color(task.getDifficulty());
		children.add(TitleComponent.builder().text(shortTitle(task)).color(color).build());
		String what = whatText(task);
		if (what != null)
		{
			children.add(LineComponent.builder().left(what).leftColor(Color.WHITE)
				.right(task.isWilderness() ? "Wildy" : "").rightColor(WILDY_COLOR).build());
		}
		else if (task.isWilderness())
		{
			children.add(LineComponent.builder().left("Wilderness").leftColor(WILDY_COLOR).build());
		}
		ClanTask.Progress progress = task.getProgress();
		if (progress != null && progress.getTarget() > 0)
		{
			int target = progress.getTarget();
			int current = Math.max(0, Math.min(progress.getCurrent(), target));
			ProgressBarComponent bar = new ProgressBarComponent();
			bar.setMinimum(0);
			bar.setMaximum(target);
			bar.setValue(current);
			bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
			bar.setCenterLabel(progressText(current, target));
			bar.setForegroundColor(task.isReached() || current >= target ? REACHED_COLOR : color.darker());
			bar.setBackgroundColor(BAR_BACKGROUND);
			bar.setFontColor(Color.WHITE);
			children.add(bar);
		}
		if (task.isReached())
		{
			children.add(LineComponent.builder().left("Target reached - press Done").leftColor(REACHED_COLOR).build());
		}
	}

	/** Boss or item the task is about, e.g. "Abyssal Sire". */
	static String shortTitle(ClanTask task)
	{
		String boss = task.displayBoss();
		return boss == null || boss.isEmpty() ? task.displayTitle() : boss;
	}

	/** What to do, e.g. "Kill 50" or "Get: Draconic visage". */
	static String whatText(ClanTask task)
	{
		String type = task.getType();
		if ("kc".equals(type))
		{
			return task.getCount() > 0 ? "Kill " + task.getCount() : null;
		}
		String target = SiteUploader.ascii(task.getTarget() == null ? "" : task.getTarget());
		if (("drop".equals(type) || "item".equals(type)) && !target.isEmpty())
		{
			return target.equals(shortTitle(task)) ? ("drop".equals(type) ? "Get the drop" : "Obtain it") : "Get: " + target;
		}
		return task.displayTitle();
	}

	/** "12/50 (24%)". */
	static String progressText(int current, int target)
	{
		int percent = target <= 0 ? 0 : (int) Math.floor(current * 100.0 / target);
		return current + "/" + target + " (" + percent + "%)";
	}
}
