package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Skip menu rows. A website that sends {@code skip_options} is the source of truth.
 * An older website gets the local rules: re-roll when any are left, "I have this item" on item or drop tasks,
 * "Green log" on boss and kill-count tasks, and "No access" only when the task needs a quest.
 */
final class SkipOptions
{
	private SkipOptions()
	{
	}

	static List<SkipOption> resolve(ClanTaskState state)
	{
		if (state != null && state.getSkipOptions() != null)
		{
			return state.getSkipOptions();
		}
		return local(state);
	}

	static List<SkipOption> local(ClanTaskState state)
	{
		ClanTask task = state == null ? null : state.getTask();
		if (task == null)
		{
			return Collections.emptyList();
		}
		List<SkipOption> options = new ArrayList<>();
		int rerolls = state.getRerolls();
		SkipOption reroll = option(SkipOption.REROLL, "Re-roll", SkipOption.COST_REROLL, rerolls > 0);
		if (rerolls > 0)
		{
			reroll.setHint("New random task. " + rerolls + " left, " + (rerolls - 1) + " after.");
		}
		else
		{
			reroll.setReason(ClanSyncPlugin.noRerollsText(state.getNextRerollIn()).replace("\n", " "));
		}
		options.add(reroll);
		if (isItemTask(task))
		{
			SkipOption have = option(SkipOption.HAVEITEM, "I have this item", SkipOption.COST_FREE, true);
			have.setHint("You will not get " + task.displayBoss() + " tasks again.");
			options.add(have);
		}
		else
		{
			SkipOption green = option(SkipOption.GREENLOG, "Green log", SkipOption.COST_FREE, true);
			green.setHint("You have the whole collection log. This boss will not be given again.");
			options.add(green);
		}
		String quest = questRequirement(task);
		SkipOption access = option(SkipOption.NOACCESS, "No access", SkipOption.COST_FREE, quest != null);
		if (quest != null)
		{
			access.setHint("No " + quest + ". Tasks from this quest stop.");
		}
		else
		{
			access.setReason("Only for tasks that need a quest.");
		}
		options.add(access);
		return options;
	}

	static boolean isItemTask(ClanTask task)
	{
		String type = task == null ? null : task.getType();
		return "item".equals(type) || "drop".equals(type);
	}

	/** Boss and kill-count tasks. Item and drop tasks are not. */
	static boolean isBossTask(ClanTask task)
	{
		return task != null && !isItemTask(task);
	}

	static String questRequirement(ClanTask task)
	{
		if (task == null || task.getRequirement() == null)
		{
			return null;
		}
		String text = SiteUploader.ascii(task.getRequirement()).trim();
		return text.isEmpty() ? null : text;
	}

	private static SkipOption option(String id, String label, String cost, boolean available)
	{
		SkipOption option = new SkipOption();
		option.setId(id);
		option.setLabel(label);
		option.setCost(cost);
		option.setAvailable(available);
		option.setAction(SkipOption.defaultAction(id));
		return option;
	}
}
