package com.arc1s.clansync;

import lombok.Data;

/**
 * One row in the Skip menu. When the website sends {@code skip_options}, these fields are used as-is.
 * Otherwise {@link SkipOptions} fills them from the task.
 */
@Data
class SkipOption
{
	static final String REROLL = "reroll";
	static final String HAVEITEM = "haveitem";
	static final String GREENLOG = "greenlog";
	static final String NOACCESS = "noaccess";
	static final String COST_FREE = "free";
	static final String COST_REROLL = "reroll";

	private String id;
	private String label;
	private boolean available;
	/** {@code free} or {@code reroll}. */
	private String cost;
	private String hint;
	/** Why the row is greyed out, when {@link #available} is false. */
	private String reason;
	/** Exact path segment under {@code /pvm/}, from the website's {@code skip_options[].action}. */
	private String action;

	/** Path segment under {@code /api/plugin/pvm/}. The website's {@code action} wins over {@code id}. */
	String endpoint()
	{
		if (action != null && !action.trim().isEmpty())
		{
			return action.trim();
		}
		return defaultAction(id);
	}

	static String defaultAction(String id)
	{
		if (HAVEITEM.equals(id))
		{
			return ClanTasksClient.ACTION_HAVEITEM;
		}
		if (GREENLOG.equals(id))
		{
			return ClanTasksClient.ACTION_GREENLOG;
		}
		if (NOACCESS.equals(id))
		{
			return ClanTasksClient.ACTION_NOACCESS;
		}
		return id == null ? "" : id;
	}

	/** "Working... (green log)" from the option's label. */
	String workingText()
	{
		String name = label == null ? id : label;
		return "Working... (" + (name == null ? "skip" : name.toLowerCase(java.util.Locale.ROOT)) + ")";
	}

	String costTag()
	{
		if (COST_REROLL.equals(cost))
		{
			return "-1 re-roll";
		}
		if (COST_FREE.equals(cost))
		{
			return "Free";
		}
		return "";
	}
}
