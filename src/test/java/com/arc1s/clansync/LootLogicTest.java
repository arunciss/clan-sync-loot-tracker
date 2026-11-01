package com.arc1s.clansync;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LootLogicTest
{
	private final KillCountParser parser = new KillCountParser();

	@Test
	public void parsesKillCountMessages()
	{
		KillCount zulrah = parser.parse("Your Zulrah kill count is: <col=ff0000>1,234</col>.", 10);
		assertEquals("Zulrah", zulrah.getName());
		assertEquals(1234, zulrah.getCount());
		assertEquals("Chambers of Xeric", parser.parse("Your completed Chambers of Xeric count is: 52.", 1).getName());
		assertEquals("Wintertodt", parser.parse("Your subdued Wintertodt count is: 400.", 1).getName());
		assertEquals("Tombs of Amascut", parser.parse("Your completion count for Tombs of Amascut is: 10.", 1).getName());
		assertEquals(77, parser.parse("Your Tempoross kill count is: 77.", 1).getCount());
		assertNull(parser.parse("You have a funny feeling like you're being followed.", 1));
	}

	@Test
	public void killCountMatchesItsSourceOnlyShortlyAfter()
	{
		KillCount tempoross = new KillCount("Tempoross", 77, 100);
		assertTrue(tempoross.belongsTo("Reward pool (Tempoross)", 103, 6));
		assertFalse(tempoross.belongsTo("Reward pool (Tempoross)", 120, 6));
		assertFalse(tempoross.belongsTo("Zulrah", 101, 6));
		KillCount cm = new KillCount("Chambers of Xeric Challenge Mode", 721, 100);
		assertTrue(cm.belongsTo("Chambers of Xeric", 400, KillCount.windowFor("Chambers of Xeric", 6)));
		assertFalse(cm.belongsTo("Chambers of Xeric", 2000, KillCount.windowFor("Chambers of Xeric", 6)));
		assertTrue(KillCount.isRaid("Theatre of Blood: Hard Mode") && KillCount.isRaid("Tombs of Amascut: Expert Mode"));
		assertFalse(KillCount.isRaid("Vorkath"));
		assertTrue(KillCount.windowFor("Vorkath", 6) == 6);
	}

	@Test
	public void openedContainerRecordsWhatWasGained()
	{
		Map<Integer, Integer> before = new HashMap<>();
		before.put(30000, 2);
		before.put(995, 100);
		ContainerOpening opening = new ContainerOpening(30000, "Lockbox", before, 5);
		Map<Integer, Integer> after = new HashMap<>();
		after.put(30000, 1);
		after.put(995, 5100);
		after.put(1234, 1);
		assertTrue(opening.inventoryChanged(after, 6));
		LootRecord record = opening.getResult();
		assertEquals(LootSourceType.CONTAINER, record.getType());
		assertEquals(Integer.valueOf(30000), record.getContainerItemId());
		assertEquals(1, record.getAmount());
		assertEquals(2, record.getItems().size());
		assertFalse(opening.isExpired(8, 3));
		assertTrue(opening.isExpired(10, 3));
	}

	@Test
	public void containerIgnoresChangesThatDoNotUseIt()
	{
		ContainerOpening opening = new ContainerOpening(30000, "Lockbox", Collections.singletonMap(30000, 1), 5);
		Map<Integer, Integer> after = new HashMap<>();
		after.put(30000, 1);
		after.put(1234, 1);
		assertFalse(opening.inventoryChanged(after, 6));
		opening.claim();
		assertTrue(opening.isExpired(6, 3));
	}

	@Test
	public void queueKeepsRecordsUntilRemoved() throws Exception
	{
		MemoryStore directory = new MemoryStore();
		Gson gson = new Gson();
		LootQueue queue = new LootQueue(directory, gson);
		LootRecord record = new LootRecord();
		record.setUid("a");
		record.getItems().add(new LootItem(4151, 1));
		queue.add(record, Collections.singletonMap(4151, "Abyssal whip"), Collections.singletonMap(4151, true));
		LootQueue reloaded = new LootQueue(directory, gson);
		assertEquals(1, reloaded.size());
		LootBatch batch = reloaded.next(10);
		assertEquals("Abyssal whip", batch.getItemNames().get(4151));
		reloaded.remove(Collections.singleton("a"));
		assertEquals(0, new LootQueue(directory, gson).size());
	}

	@Test
	public void containerOpeningsGoToTheOldestUnopenedDrop() throws Exception
	{
		MemoryStore directory = new MemoryStore();
		ContainerLedger ledger = new ContainerLedger(directory, new Gson());
		ledger.received("yama-1", 30000, 1);
		ledger.received("yama-2", 30000, 1);
		ledger.received("yama-3", 30000, 3);
		ledger.received("zulrah-1", 12345, 1);
		assertEquals("yama-1", ledger.opened(30000, 1));
		assertEquals("yama-2", ledger.opened(30000, 1));
		ContainerLedger reloaded = new ContainerLedger(directory, new Gson());
		assertEquals(3, reloaded.waiting(30000));
		assertEquals("yama-3", reloaded.opened(30000, 1));
		assertEquals("yama-3", reloaded.opened(30000, 2));
		assertNull(reloaded.opened(30000, 1));
		assertEquals("zulrah-1", reloaded.opened(12345, 1));
	}
}
