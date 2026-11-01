package com.arc1s.clansync;

import com.google.gson.annotations.SerializedName;
import java.awt.Color;
import java.util.List;
import lombok.Data;

/** One clan task as the website describes it (English, plain ASCII). */
@Data
class ClanTask
{
	static final Color EASY_COLOR = new Color(0x4CAF50);
	static final Color MID_COLOR = new Color(0xFFB300);
	static final Color HARD_COLOR = new Color(0xE53935);

	@SerializedName("assignment_id")
	private int assignmentId;
	private String title;
	private String type;
	private String target;
	private String boss;
	private int count;
	private String difficulty;
	private boolean wilderness;
	private int points;
	private String requirement;
	@SerializedName("slayer_task_required")
	private boolean slayerTaskRequired;
	private Progress progress;
	private boolean reached;
	/** How the plugin can count progress itself between website checks; null on older websites. */
	private Track track;

	@Data
	static class Progress
	{
		private int current;
		private int target;
		private String source;
	}

	/**
	 * What counts for the task (from the website): "kc" = the kill count chat message of one of kc_names
	 * (progress = kc - base), "drop" = receiving one of items from any loot source. Any other mode is ignored.
	 */
	@Data
	static class Track
	{
		private String mode;
		@SerializedName("kc_names")
		private List<String> kcNames;
		private Integer base;
		private List<String> items;
		private List<String> sources;
	}

	/** Easy is green, mid orange, hard red; anything unknown is grey. */
	static Color color(String difficulty)
	{
		if ("easy".equals(difficulty))
		{
			return EASY_COLOR;
		}
		if ("mid".equals(difficulty))
		{
			return MID_COLOR;
		}
		if ("hard".equals(difficulty))
		{
			return HARD_COLOR;
		}
		return Color.GRAY;
	}

	static String difficultyName(String difficulty)
	{
		if (difficulty == null || difficulty.isEmpty())
		{
			return "?";
		}
		return Character.toUpperCase(difficulty.charAt(0)) + difficulty.substring(1);
	}

	/** "37/100 kills" (or "0/1 received" for drop tasks) when progress is known, otherwise null. */
	String progressText()
	{
		if (progress == null || progress.target <= 0)
		{
			return null;
		}
		boolean drop = "drop".equals(type) || "item".equals(type);
		return Math.min(progress.current, progress.target) + "/" + progress.target + (drop ? " received" : " kills");
	}

	/** A copy of this task showing the given progress (the website's task object is never changed). */
	ClanTask withProgress(int current, int target)
	{
		ClanTask copy = new ClanTask();
		copy.assignmentId = assignmentId;
		copy.title = title;
		copy.type = type;
		copy.target = this.target;
		copy.boss = boss;
		copy.count = count;
		copy.difficulty = difficulty;
		copy.wilderness = wilderness;
		copy.points = points;
		copy.requirement = requirement;
		copy.slayerTaskRequired = slayerTaskRequired;
		copy.track = track;
		Progress p = new Progress();
		p.current = Math.max(0, Math.min(current, target));
		p.target = target;
		p.source = "plugin";
		copy.progress = p;
		copy.reached = reached || current >= target;
		return copy;
	}

	String displayTitle()
	{
		return SiteUploader.ascii(title == null || title.isEmpty() ? target : title);
	}

	String displayBoss()
	{
		return SiteUploader.ascii(boss == null || boss.isEmpty() ? target : boss);
	}
}
