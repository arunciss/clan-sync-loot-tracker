package com.arc1s.clansync;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SnapshotDiff
{
	private SnapshotDiff()
	{
	}

	/**
	 * A renamed player shows up as one "left" and one "joined", because the game
	 * only exposes names.
	 */
	public static ClanSnapshot.Changes compute(ClanSnapshot previous, ClanSnapshot current)
	{
		if (previous == null)
		{
			return null;
		}

		Map<String, ClanSnapshot.Member> before = index(previous);
		Map<String, ClanSnapshot.Member> after = index(current);

		ClanSnapshot.Changes changes = new ClanSnapshot.Changes();
		changes.setPreviousCollectedAt(previous.getCollectedAt());

		for (Map.Entry<String, ClanSnapshot.Member> entry : after.entrySet())
		{
			ClanSnapshot.Member now = entry.getValue();
			ClanSnapshot.Member old = before.get(entry.getKey());
			if (old == null)
			{
				changes.getJoined().add(now.getName());
			}
			else if (old.getRank() != now.getRank())
			{
				ClanSnapshot.RankChange change = new ClanSnapshot.RankChange();
				change.setName(now.getName());
				change.setFromRank(old.getRank());
				change.setFromTitle(old.getTitle());
				change.setToRank(now.getRank());
				change.setToTitle(now.getTitle());
				changes.getRankChanges().add(change);
			}
		}

		for (Map.Entry<String, ClanSnapshot.Member> entry : before.entrySet())
		{
			if (!after.containsKey(entry.getKey()))
			{
				changes.getLeft().add(entry.getValue().getName());
			}
		}

		return changes;
	}

	static String key(String name)
	{
		return name == null ? "" : name.replace('\u00A0', ' ').replace('_', ' ').replace('-', ' ')
			.trim().toLowerCase(Locale.ROOT);
	}

	private static Map<String, ClanSnapshot.Member> index(ClanSnapshot snapshot)
	{
		Map<String, ClanSnapshot.Member> map = new LinkedHashMap<>();
		for (ClanSnapshot.Member member : snapshot.getMembers())
		{
			map.put(key(member.getName()), member);
		}
		return map;
	}
}
