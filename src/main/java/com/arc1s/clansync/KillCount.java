package com.arc1s.clansync;

import lombok.Value;

@Value
public class KillCount
{
	String name;
	int count;
	int tick;

	// Normal bosses drop loot with the kill count message. A raid's count arrives when the raid ends,
	// but its loot only when the reward chest is opened, so raids wait up to 15 minutes (1500 ticks).
	static final int RAID_WINDOW_TICKS = 1500;

	public static boolean isRaid(String source)
	{
		String key = source == null ? "" : SnapshotDiff.key(source);
		return key.contains(SnapshotDiff.key("Chambers of Xeric")) || key.contains(SnapshotDiff.key("Theatre of Blood"))
			|| key.contains(SnapshotDiff.key("Tombs of Amascut")) || isFracturedArchive(source);
	}

	// The Fractured Archive (raid 4, October 2026). The game writes it as "Fractured Archive" in the
	// kill count message and RuneLite's loot tracker event, and as "The Fractured Archive" elsewhere.
	public static boolean isFracturedArchive(String source)
	{
		return source != null && SnapshotDiff.key(source).contains(SnapshotDiff.key("Fractured Archive"));
	}

	public static int windowFor(String source, int defaultTicks)
	{
		return isRaid(source) ? RAID_WINDOW_TICKS : defaultTicks;
	}

	public boolean belongsTo(String source, int currentTick, int windowTicks)
	{
		if (source == null || currentTick - tick > windowTicks)
		{
			return false;
		}
		String a = SnapshotDiff.key(source);
		String b = SnapshotDiff.key(name);
		return a.contains(b) || b.contains(a);
	}
}
