package com.arc1s.clansync;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fight Caves and Inferno rewards. RuneLite's loot tracker does not emit those completions, so the cape is
 * recorded from the Jad or Zuk kill count, with the collection log or an inventory increase only when that
 * message was not seen for this run.
 *
 * <p>Region ids are {@code WorldPoint.getRegionID()} ({@code (x &gt;&gt; 6) &lt;&lt; 8 | y &gt;&gt; 6}).
 * The Fight Caves sit near (2411, 5116), which is region 9551. The Inferno sits near (2271, 5344), which is
 * region 9043.
 */
final class CaveRewardDetector
{
	static final int FIRE_CAPE = 6570;
	static final int INFERNAL_CAPE = 21295;
	static final int FIGHT_CAVES_REGION = 9551;
	static final int INFERNO_REGION = 9043;
	static final String FIGHT_CAVE = "TzHaar Fight Cave";
	static final String INFERNO = "Inferno";

	private boolean savedThisRun;
	private final Map<Integer, Integer> seenCapes = new HashMap<>();

	/** TzTok-Jad or TzKal-Zuk kill count. Practice and challenge completion counts return null. */
	LootRecord fromKillCount(String player, String bossName, int count, Instant when)
	{
		String source = sourceForBoss(bossName);
		if (source == null || savedThisRun || count < 0)
		{
			return null;
		}
		savedThisRun = true;
		return record(player, source, count, itemFor(source), when, capeUid(player, source, count));
	}

	/** "New item added to your collection log: Fire cape" / "Infernal cape", only if this run has no cape yet. */
	LootRecord fromCollectionLog(String player, String message, Instant when)
	{
		if (savedThisRun)
		{
			return null;
		}
		String source = sourceForCollectionLog(message);
		if (source == null)
		{
			return null;
		}
		savedThisRun = true;
		long minute = when.getEpochSecond() / 60;
		return record(player, source, null, itemFor(source), when, fallbackUid(player, source, minute));
	}

	/**
	 * Cape count increased by one while inside the cave, and no kill count or collection log was seen for this run.
	 * Counts seen outside the cave are only a baseline.
	 */
	LootRecord fromInventory(String player, int regionId, Map<Integer, Integer> counts, Instant when)
	{
		int fire = count(counts, FIRE_CAPE);
		int infernal = count(counts, INFERNAL_CAPE);
		int cape = capeForRegion(regionId);
		if (cape < 0)
		{
			savedThisRun = false;
			seenCapes.put(FIRE_CAPE, fire);
			seenCapes.put(INFERNAL_CAPE, infernal);
			return null;
		}
		int now = cape == FIRE_CAPE ? fire : infernal;
		Integer before = seenCapes.put(cape, now);
		if (savedThisRun || before == null || now != before + 1)
		{
			return null;
		}
		savedThisRun = true;
		String source = cape == FIRE_CAPE ? FIGHT_CAVE : INFERNO;
		long minute = when.getEpochSecond() / 60;
		return record(player, source, null, cape, when, fallbackUid(player, source, minute));
	}

	/** A loot-tracker event for a cape source is dropped once this run already saved one. */
	boolean ignoreLoot(String source)
	{
		return savedThisRun && (FIGHT_CAVE.equals(source) || INFERNO.equals(source));
	}

	void resetRun()
	{
		savedThisRun = false;
	}

	static String sourceForBoss(String name)
	{
		String key = KillCountReport.lower(name);
		if ("tztok-jad".equals(key))
		{
			return FIGHT_CAVE;
		}
		if ("tzkal-zuk".equals(key))
		{
			return INFERNO;
		}
		return null;
	}

	static String sourceForCollectionLog(String message)
	{
		if (message == null)
		{
			return null;
		}
		String text = message.replaceAll("<[^>]*>", "").trim();
		String prefix = "New item added to your collection log:";
		if (text.length() < prefix.length() || !text.regionMatches(true, 0, prefix, 0, prefix.length()))
		{
			return null;
		}
		String item = text.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
		if (item.startsWith("fire cape"))
		{
			return FIGHT_CAVE;
		}
		if (item.startsWith("infernal cape"))
		{
			return INFERNO;
		}
		return null;
	}

	static int capeForRegion(int regionId)
	{
		if (regionId == FIGHT_CAVES_REGION)
		{
			return FIRE_CAPE;
		}
		if (regionId == INFERNO_REGION)
		{
			return INFERNAL_CAPE;
		}
		return -1;
	}

	static String capeUid(String player, String source, int killCount)
	{
		String key = "cape|" + KillCountReport.lower(player) + "|" + source + "|" + killCount;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}

	static String fallbackUid(String player, String source, long epochMinute)
	{
		String key = "cape|" + KillCountReport.lower(player) + "|" + source + "|" + epochMinute;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}

	private static LootRecord record(String player, String source, Integer killCount, int itemId, Instant when, String uid)
	{
		LootRecord record = new LootRecord();
		record.setUid(uid);
		record.setPlayer(player);
		record.setType(LootSourceType.EVENT);
		record.setSource(source);
		record.setAmount(1);
		record.setKillCount(killCount);
		record.setRecordedAt(when.toString());
		record.getItems().add(new LootItem(itemId, 1));
		return record;
	}

	private static int itemFor(String source)
	{
		return INFERNO.equals(source) ? INFERNAL_CAPE : FIRE_CAPE;
	}

	private static int count(Map<Integer, Integer> counts, int itemId)
	{
		if (counts == null)
		{
			return 0;
		}
		Integer value = counts.get(itemId);
		return value == null ? 0 : value;
	}
}
