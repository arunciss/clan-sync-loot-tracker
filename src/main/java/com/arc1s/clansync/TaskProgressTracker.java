package com.arc1s.clansync;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Optimistic task progress between website checks: the task boss's kill count chat message (kill count tasks) or
 * a required drop (drop tasks) moves the shown progress at once. Loot never counts as a kill. The website stays the judge: its next answer replaces the local
 * count as soon as it has caught up. A count the plugin itself reported expires after a few minutes. A count the
 * site still shows as Wise Old Man data does not: it stays until the site catches up, the task changes, or the
 * track base changes (then it is recomputed from the last kill count seen this session).
 * Called from the client thread (game events) and the executor (website answers), so every method is synchronized.
 */
class TaskProgressTracker
{
	static final long LOCAL_TTL_MILLIS = 180_000;

	private ClanTask task;
	private int local = -1;
	private long localAt;
	private boolean uploadPending;
	/** Last game kill count seen for this task's boss. Forgotten when the assignment changes. */
	private int lastKc = -1;

	/** The website's latest task (null = none). Keeps the local count only while it is ahead and still fresh. */
	synchronized void setServerTask(ClanTask newTask, long now)
	{
		Integer previousBase = baseOf(task);
		boolean sameAssignment = newTask != null && task != null && newTask.getAssignmentId() == task.getAssignmentId();
		if (!sameAssignment)
		{
			local = -1;
			lastKc = -1;
			uploadPending = false;
		}
		task = newTask;
		if (sameAssignment)
		{
			Integer base = baseOf(task);
			if (lastKc >= 0 && base != null && (previousBase == null || !base.equals(previousBase)))
			{
				int recomputed = lastKc - base;
				if (recomputed >= 0)
				{
					local = recomputed;
					localAt = now;
				}
			}
		}
		if (local >= 0 && ((pluginSource() && now - localAt > LOCAL_TTL_MILLIS) || serverCurrent() >= local))
		{
			local = -1;
		}
	}

	/** A kill count chat message ("Your Abyssal Sire kill count is: 604"). True when the shown progress moved. */
	synchronized boolean onKillCount(String name, int killCount, long now)
	{
		ClanTask.Track track = track();
		if (track == null || !"kc".equals(track.getMode()) || !matchesAny(alnum(name), track.getKcNames(), true))
		{
			return false;
		}
		lastKc = killCount;
		int value = track.getBase() != null ? killCount - track.getBase() : current(now) + 1;
		return raise(value, now);
	}

	/**
	 * A kill count was queued for this task's boss. Ask for the early upload even when the shown count did not move,
	 * so the site can replace a Wise Old Man number.
	 */
	synchronized void markTaskBossUpload(String name)
	{
		ClanTask.Track track = track();
		if (track != null && "kc".equals(track.getMode()) && matchesAny(alnum(name), track.getKcNames(), true))
		{
			uploadPending = true;
		}
	}

	/**
	 * One loot record: type is the RuneLite loot type ("NPC", "EVENT", ...), items maps item names to quantities.
	 * True when the shown progress moved.
	 */
	synchronized boolean onLoot(String type, String source, Map<String, Integer> items, long now)
	{
		ClanTask.Track track = track();
		if (track == null || "PLAYER".equals(type) || source == null || "pvp".equals(lower(source)))
		{
			return false;
		}
		if ("drop".equals(track.getMode()))
		{
			// Obtain-item tasks: the item counts from any monster or event loot (boss or not).
			int received = 0;
			for (Map.Entry<String, Integer> item : items.entrySet())
			{
				if (matchesAny(lower(item.getKey()), track.getItems(), false))
				{
					received += Math.max(1, item.getValue() == null ? 1 : item.getValue());
				}
			}
			return received > 0 && raise(current(now) + received, now);
		}
		// Kill count tasks move only on the boss kill count chat message (onKillCount), never on loot.
		return false;
	}

	/** The task to show: the website's task, with the local count when that is ahead. Null when there is no task. */
	synchronized ClanTask display(long now)
	{
		if (task == null)
		{
			return null;
		}
		int target = target();
		if (target <= 0 || localCount(now) < 0 || localCount(now) <= serverCurrent())
		{
			return task;
		}
		return task.withProgress(local, target);
	}

	/** True once, after a counted event, so one early website check can follow its upload. */
	synchronized boolean takeUploadPending()
	{
		boolean pending = uploadPending;
		uploadPending = false;
		return pending;
	}

	private boolean raise(int value, long now)
	{
		int target = target();
		if (target <= 0 || value <= current(now))
		{
			return false;
		}
		local = value;
		localAt = now;
		uploadPending = true;
		return true;
	}

	private int current(long now)
	{
		return Math.max(serverCurrent(), localCount(now));
	}

	private int localCount(long now)
	{
		if (local < 0)
		{
			return -1;
		}
		// Wise Old Man (or a missing source) has not caught up: keep the count instead of falling back to 0.
		if (!pluginSource() || now - localAt <= LOCAL_TTL_MILLIS)
		{
			return local;
		}
		return -1;
	}

	/** True only when the website says this progress came from the plugin, so the short expiry applies. */
	private boolean pluginSource()
	{
		return task != null && task.getProgress() != null && "plugin".equals(task.getProgress().getSource());
	}

	private static Integer baseOf(ClanTask task)
	{
		return task == null || task.getTrack() == null ? null : task.getTrack().getBase();
	}

	private int serverCurrent()
	{
		return task == null || task.getProgress() == null ? 0 : task.getProgress().getCurrent();
	}

	private int target()
	{
		if (task == null)
		{
			return 0;
		}
		ClanTask.Progress progress = task.getProgress();
		return progress != null && progress.getTarget() > 0 ? progress.getTarget() : task.getCount();
	}

	private ClanTask.Track track()
	{
		return task == null ? null : task.getTrack();
	}

	private static boolean matchesAny(String value, List<String> names, boolean alnum)
	{
		if (value.isEmpty() || names == null)
		{
			return false;
		}
		for (String name : names)
		{
			if (value.equals(alnum ? alnum(name) : lower(name)))
			{
				return true;
			}
		}
		return false;
	}

	static String lower(String text)
	{
		return text == null ? "" : SiteUploader.ascii(text.replace('\u00A0', ' ')).trim().toLowerCase(Locale.ROOT);
	}

	/** "Phosani's Nightmare" and "phosanis_nightmare" both become "phosanisnightmare". */
	static String alnum(String text)
	{
		return lower(text).replaceAll("[^a-z0-9]", "");
	}
}
