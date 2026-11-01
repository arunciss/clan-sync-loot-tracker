package com.arc1s.clansync;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TaskProgressTest
{
	private static final Gson GSON = new Gson();

	private static ClanTask task(int id, String type, int count, Integer current, String track)
	{
		String progress = current == null ? "null" : "{\"current\":" + current + ",\"target\":" + count + ",\"source\":\"plugin\"}";
		return GSON.fromJson("{\"assignment_id\":" + id + ",\"title\":\"T\",\"type\":\"" + type + "\",\"count\":" + count
			+ ",\"progress\":" + progress + ",\"reached\":false,\"track\":" + track + "}", ClanTask.class);
	}

	private static Map<String, Integer> items(String name, int quantity)
	{
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put(name, quantity);
		return map;
	}

	@Test
	public void readsTheTrackFromTheWebsite()
	{
		ClanTask kc = task(189, "kc", 25, 6, "{\"mode\":\"kc\",\"kc_names\":[\"abyssal_sire\"],\"base\":598}");
		assertEquals("kc", kc.getTrack().getMode());
		assertEquals(Collections.singletonList("abyssal_sire"), kc.getTrack().getKcNames());
		assertEquals(Integer.valueOf(598), kc.getTrack().getBase());
		assertNull(task(1, "kc", 5, null, "null").getTrack());
		assertEquals("0/1 received", task(2, "drop", 1, 0, "{\"mode\":\"drop\",\"items\":[\"Berserker ring\"]}").progressText());
	}

	@Test
	public void killCountMessageMovesProgressAtOnce()
	{
		TaskProgressTracker tracker = new TaskProgressTracker();
		ClanTask server = task(189, "kc", 25, 6, "{\"mode\":\"kc\",\"kc_names\":[\"abyssal_sire\"],\"base\":598}");
		tracker.setServerTask(server, 0);
		assertSame(server, tracker.display(0));
		assertFalse(tracker.onKillCount("Vorkath", 700, 1000));
		assertTrue(tracker.onKillCount("Abyssal Sire", 605, 1000));
		ClanTask shown = tracker.display(1000);
		assertEquals("7/25 kills", shown.progressText());
		assertFalse(shown.isReached());
		assertEquals(6, server.getProgress().getCurrent());
		// The same count again (e.g. a repeated message) changes nothing.
		assertFalse(tracker.onKillCount("Abyssal Sire", 605, 2000));
		assertTrue(tracker.takeUploadPending());
		assertFalse(tracker.takeUploadPending());
		assertTrue(tracker.onKillCount("Abyssal Sire", 623, 3000));
		assertTrue(tracker.display(3000).isReached());
		assertEquals("25/25 kills", tracker.display(3000).progressText());
	}

	@Test
	public void namesMatchWithoutPunctuation()
	{
		TaskProgressTracker tracker = new TaskProgressTracker();
		tracker.setServerTask(task(5, "kc", 10, 0, "{\"mode\":\"kc\",\"kc_names\":[\"phosanis_nightmare\"],\"base\":null}"), 0);
		assertTrue(tracker.onKillCount("Phosani's Nightmare", 41, 10));
		assertEquals(1, tracker.display(10).getProgress().getCurrent());
		// Without a known base every message counts one kill.
		assertTrue(tracker.onKillCount("Phosani's Nightmare", 42, 20));
		assertEquals(2, tracker.display(20).getProgress().getCurrent());
	}

	@Test
	public void requiredDropReachesTheTarget()
	{
		TaskProgressTracker tracker = new TaskProgressTracker();
		tracker.setServerTask(task(343, "drop", 1, 0, "{\"mode\":\"drop\",\"items\":[\"Berserker ring\"],\"sources\":[\"Dagannoth Rex\"]}"), 0);
		assertFalse(tracker.onLoot("NPC", "Dagannoth Rex", items("Dragon axe", 1), 10));
		assertFalse(tracker.onLoot("PLAYER", "PvP", items("Berserker ring", 1), 10));
		// Any monster counts for an obtain-item task, even when an old website answer still names a source.
		assertTrue(tracker.onLoot("NPC", "Dagannoth Prime", items("Berserker ring", 1), 20));
		ClanTask shown = tracker.display(20);
		assertTrue(shown.isReached());
		assertEquals("1/1 received", shown.progressText());
	}

	@Test
	public void lootNeverCountsAsKills()
	{
		// An old website answer for a monster without a boss kill count (Drake): loot must not move the task.
		TaskProgressTracker tracker = new TaskProgressTracker();
		tracker.setServerTask(task(366, "kc", 2, 0, "{\"mode\":\"kills\",\"npc\":\"Drake\"}"), 0);
		assertFalse(tracker.onLoot("NPC", "Drake", items("Drake's tooth", 1), 1));
		assertFalse(tracker.onLoot("NPC", "Drake", Collections.emptyMap(), 2));
		assertFalse(tracker.onKillCount("Drake", 5, 3));
		assertEquals(0, tracker.display(3).getProgress().getCurrent());
		assertFalse(tracker.takeUploadPending());

		// A boss kill count task moves only on the boss kill count message, not on the boss's loot.
		String track = "{\"mode\":\"kc\",\"kc_names\":[\"vorkath\"],\"base\":100}";
		tracker.setServerTask(task(9, "kc", 10, 0, track), 10);
		assertFalse(tracker.onLoot("NPC", "Vorkath", items("Dragon bones", 1), 11));
		assertFalse(tracker.onKillCount("Zulrah", 500, 12));
		assertTrue(tracker.onKillCount("Vorkath", 101, 13));
		assertEquals(1, tracker.display(13).getProgress().getCurrent());
	}

	@Test
	public void websiteWinsOnceCaughtUpAndLocalExpires()
	{
		TaskProgressTracker tracker = new TaskProgressTracker();
		String track = "{\"mode\":\"kc\",\"kc_names\":[\"vorkath\"],\"base\":100}";
		tracker.setServerTask(task(9, "kc", 10, 0, track), 0);
		assertTrue(tracker.onKillCount("Vorkath", 102, 0));
		// Website has not processed the upload yet: the local count stays.
		ClanTask lagging = task(9, "kc", 10, 1, track);
		tracker.setServerTask(lagging, 1000);
		assertEquals(2, tracker.display(1000).getProgress().getCurrent());
		// Website caught up: its task is shown as it is.
		ClanTask caughtUp = task(9, "kc", 10, 2, track);
		tracker.setServerTask(caughtUp, 2000);
		assertSame(caughtUp, tracker.display(2000));
		// An unconfirmed local count is dropped after a few minutes.
		assertTrue(tracker.onKillCount("Vorkath", 103, 3000));
		assertSame(caughtUp, tracker.display(3000 + TaskProgressTracker.LOCAL_TTL_MILLIS + 1));
		// A new assignment forgets the old local count.
		assertTrue(tracker.onKillCount("Vorkath", 104, 10_000));
		ClanTask next = task(10, "kc", 10, 0, track);
		tracker.setServerTask(next, 10_001);
		assertSame(next, tracker.display(10_001));
		assertFalse(tracker.takeUploadPending());
	}

	@Test
	public void noTrackNoLocalProgress()
	{
		TaskProgressTracker tracker = new TaskProgressTracker();
		assertFalse(tracker.onKillCount("Vorkath", 5, 0));
		assertNull(tracker.display(0));
		tracker.setServerTask(task(3, "kc", 5, 0, "null"), 0);
		assertFalse(tracker.onKillCount("Vorkath", 5, 0));
		assertFalse(tracker.onLoot("NPC", "Vorkath", items("Dragon bones", 1), 0));
	}

	@Test
	public void stateCopyKeepsEverythingButTheTask()
	{
		ClanTaskState state = GSON.fromJson("{\"feature\":\"clan_tasks\",\"available\":true,\"joined\":true,\"rerolls\":3,\"points\":40,"
			+ "\"completed\":2,\"next_reroll_in\":4,\"message\":\"m\",\"wilderness_enabled\":true,\"slayer_tasks_enabled\":false}", ClanTaskState.class);
		ClanTask task = task(1, "kc", 5, 0, "null");
		ClanTaskState copy = state.withTask(task);
		assertSame(task, copy.getTask());
		copy.setTask(null);
		assertEquals(state, copy);
	}
}
