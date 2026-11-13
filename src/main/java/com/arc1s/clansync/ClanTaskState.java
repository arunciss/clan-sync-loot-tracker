package com.arc1s.clansync;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

/** The member's clan task status from GET {api}/pvm/current. */
@Data
class ClanTaskState
{
	static final String FEATURE = "clan_tasks";

	private String feature;
	private boolean available;
	private boolean joined;
	private ClanTask task;
	private int rerolls;
	private int points;
	private int completed;
	/** Completed tasks needed for the next free re-roll (0 when the website does not say). */
	@SerializedName("next_reroll_in")
	private int nextRerollIn;
	private String message;
	/** The member's saved preferences; null when the website does not send them (older website). */
	@SerializedName("wilderness_enabled")
	private Boolean wildernessEnabled;
	@SerializedName("slayer_tasks_enabled")
	private Boolean slayerTasksEnabled;
	/**
	 * Skip menu from the website. Null on an older website, which falls back to local rules.
	 * An empty list is present and means the website offered no skip rows.
	 */
	@SerializedName("skip_options")
	private List<SkipOption> skipOptions;

	/** A copy with another task (used to show local progress without changing the website's answer). */
	ClanTaskState withTask(ClanTask shownTask)
	{
		ClanTaskState copy = new ClanTaskState();
		copy.feature = feature;
		copy.available = available;
		copy.joined = joined;
		copy.task = shownTask;
		copy.rerolls = rerolls;
		copy.points = points;
		copy.completed = completed;
		copy.nextRerollIn = nextRerollIn;
		copy.message = message;
		copy.wildernessEnabled = wildernessEnabled;
		copy.slayerTasksEnabled = slayerTasksEnabled;
		copy.skipOptions = skipOptions;
		return copy;
	}
}
