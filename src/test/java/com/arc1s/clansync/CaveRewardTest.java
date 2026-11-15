package com.arc1s.clansync;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CaveRewardTest
{
	private static final Instant WHEN = Instant.parse("2026-11-13T12:00:30Z");

	@Test
	public void jadKillCountBecomesOneFireCape()
	{
		CaveRewardDetector caves = new CaveRewardDetector();
		LootRecord record = caves.fromKillCount("Some Name", "TzTok-Jad", 12, WHEN);
		assertEquals(CaveRewardDetector.capeUid("Some Name", CaveRewardDetector.FIGHT_CAVE, 12), record.getUid());
		assertEquals(CaveRewardDetector.FIGHT_CAVE, record.getSource());
		assertEquals(LootSourceType.EVENT, record.getType());
		assertEquals(Integer.valueOf(12), record.getKillCount());
		assertEquals(CaveRewardDetector.FIRE_CAPE, record.getItems().get(0).getId());
		assertEquals(1, record.getItems().get(0).getQuantity());
		assertNull(caves.fromKillCount("some name", "tztok-jad", 12, WHEN));
		assertEquals(record.getUid(), CaveRewardDetector.capeUid("some name", CaveRewardDetector.FIGHT_CAVE, 12));
	}

	@Test
	public void zukKillCountBecomesInfernalCape()
	{
		LootRecord record = new CaveRewardDetector().fromKillCount("Me", "TzKal-Zuk", 3, WHEN);
		assertEquals(CaveRewardDetector.INFERNO, record.getSource());
		assertEquals(CaveRewardDetector.INFERNAL_CAPE, record.getItems().get(0).getId());
		assertEquals(CaveRewardDetector.capeUid("Me", CaveRewardDetector.INFERNO, 3), record.getUid());
	}

	@Test
	public void collectionLogAloneRecordsTheCape()
	{
		CaveRewardDetector caves = new CaveRewardDetector();
		LootRecord record = caves.fromCollectionLog("Me",
			"<col=ef1020>New item added to your collection log: Fire cape</col>", WHEN);
		assertEquals(CaveRewardDetector.FIGHT_CAVE, record.getSource());
		assertEquals(CaveRewardDetector.FIRE_CAPE, record.getItems().get(0).getId());
		long minute = WHEN.getEpochSecond() / 60;
		assertEquals(CaveRewardDetector.fallbackUid("Me", CaveRewardDetector.FIGHT_CAVE, minute), record.getUid());
		assertNull(caves.fromCollectionLog("Me", "New item added to your collection log: Fire cape", WHEN));
	}

	@Test
	public void killCountAndCollectionLogAreOneRecord()
	{
		CaveRewardDetector caves = new CaveRewardDetector();
		assertTrue(caves.fromKillCount("Me", "TzTok-Jad", 4, WHEN) != null);
		assertNull(caves.fromCollectionLog("Me", "New item added to your collection log: Fire cape", WHEN));
		assertNull(caves.fromInventory("Me", CaveRewardDetector.FIGHT_CAVES_REGION, capes(1), WHEN));
	}

	@Test
	public void inventoryIncreaseIsUsedOnlyWithoutAKillCount()
	{
		CaveRewardDetector caves = new CaveRewardDetector();
		assertNull(caves.fromInventory("Me", CaveRewardDetector.FIGHT_CAVES_REGION, capes(0), WHEN));
		LootRecord record = caves.fromInventory("Me", CaveRewardDetector.FIGHT_CAVES_REGION, capes(1), WHEN);
		assertEquals(CaveRewardDetector.FIGHT_CAVE, record.getSource());
		assertEquals(CaveRewardDetector.FIRE_CAPE, record.getItems().get(0).getId());
		assertNull(caves.fromKillCount("Me", "TzTok-Jad", 1, WHEN));
	}

	@Test
	public void practiceAndChallengesAreNotCapes()
	{
		KillCountParser parser = new KillCountParser();
		CaveRewardDetector caves = new CaveRewardDetector();
		KillCount practice = parser.parse("Your Jad practice completion count is: 2.", 1);
		KillCount challenge = parser.parse("Your completion count for TzHaar-Ket-Rak's Challenges is: 3.", 1);
		assertEquals("Jad practice", practice.getName());
		assertEquals("TzHaar-Ket-Rak's Challenges", challenge.getName());
		assertNull(caves.fromKillCount("Me", practice.getName(), practice.getCount(), WHEN));
		assertNull(caves.fromKillCount("Me", challenge.getName(), challenge.getCount(), WHEN));
		assertEquals(9551, CaveRewardDetector.FIGHT_CAVES_REGION);
		assertEquals(9043, CaveRewardDetector.INFERNO_REGION);
	}

	private static Map<Integer, Integer> capes(int fire)
	{
		return new HashMap<>(Collections.singletonMap(CaveRewardDetector.FIRE_CAPE, fire));
	}
}
