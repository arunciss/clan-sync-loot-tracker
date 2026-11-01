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

public class KillTimeTest
{
	private final DurationParser durations = new DurationParser();
	private final DurationMessageParser messages = new DurationMessageParser();
	private final KillCountParser killCounts = new KillCountParser();

	@Test
	public void parsesTimes()
	{
		assertEquals(Long.valueOf(83000), durations.toMillis("1:23"));
		assertEquals(Long.valueOf(83400), durations.toMillis("1:23.40"));
		assertEquals(Long.valueOf(83400), durations.toMillis("1:23.4"));
		assertEquals(Long.valueOf(3723200), durations.toMillis("1:02:03.20"));
		assertNull(durations.toMillis("1:75"));
	}

	@Test
	public void parsesDurationMessages()
	{
		DurationMessage fight = messages.parse("Fight duration: <col=ff0000>1:23.40</col>. Personal best: 1:10.20", 5);
		assertEquals(83400, fight.getTimeMs());
		assertEquals(Long.valueOf(70200), fight.getPersonalBestMs());
		assertFalse(fight.isNewPersonalBest());
		DurationMessage best = messages.parse("Fight duration: <col=ff0000>0:58.80</col> (new personal best)", 5);
		assertTrue(best.isNewPersonalBest());
		assertEquals(Long.valueOf(58800), best.getPersonalBestMs());
		DurationMessage raid = messages.parse("Congratulations - your raid is complete!<br>Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>23:12.60</col> Personal best: </col><col=ff0000>21:00.00</col>", 5);
		assertEquals(DurationMessageParser.PRIORITY_TOTAL, raid.getPriority());
		assertEquals(1392600, raid.getTimeMs());
		assertEquals(TeamSize.exact(1), raid.getTeamSize());
		assertNull(fight.getTeamSize());
		assertEquals(DurationMessageParser.PRIORITY_TOTAL, messages.parse("Theatre of Blood total completion time: 20:15. Personal best: 19:00", 1).getPriority());
		assertNull(messages.parse("Your Zulrah kill count is: 5.", 1));
	}

	// Message formats from RuneLite's own TFA support (commit 99b3c90afe "chat commands: add tfa").
	@Test
	public void fracturedArchiveCompletionMakesARaidTime()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		assertNull(tracker.onDuration(messages.parse("Challenge complete: Ocellus. Duration: <col=ef1020>14:28.80</col>. Total: <col=ef1020>36:01.80</col>. Personal best: 13:54.60", 100)));
		assertNull(tracker.onDuration(messages.parse("The Fractured Archive: Total completion time: <col=ef1020>36:01.80</col>. Personal best: 30:12.00", 100)));
		KillTime time = tracker.onKillCount(killCounts.parse("Your completed Fractured Archive count is: <col=ff0000>25</col>.", 101));
		assertEquals("Fractured Archive", time.getSource());
		assertEquals(Integer.valueOf(25), time.getKillCount());
		assertEquals(2161800, time.getTimeMs());
		assertEquals(Long.valueOf(1812000), time.getPersonalBestMs());
		assertFalse(time.isNewPersonalBest());
		assertNull(time.getTeamSize());
		assertTrue(KillCount.isRaid(time.getSource()) && KillCount.isRaid("The Fractured Archive"));
		assertEquals(KillCount.RAID_WINDOW_TICKS, KillCount.windowFor("Fractured Archive", 6));
	}

	@Test
	public void fracturedArchiveNewPersonalBest()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		assertNull(tracker.onDuration(messages.parse("Challenge complete: Ocellus. Duration: <col=ef1020>2:25.80</col>. Total: <col=ef1020>2:25.80</col> (new personal best)", 50)));
		assertNull(tracker.onDuration(messages.parse("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> (new personal best)", 50)));
		KillTime time = tracker.onKillCount(killCounts.parse("Your completed Fractured Archive count is: <col=ff0000>1</col>.", 50));
		assertEquals(145800, time.getTimeMs());
		assertTrue(time.isNewPersonalBest());
		assertEquals(Long.valueOf(145800), time.getPersonalBestMs());
	}

	@Test
	public void fracturedArchiveTeamSizeFromPartySlots()
	{
		Map<Integer, Integer> varbits = new HashMap<>();
		assertNull(TfaTeamSize.count(id -> varbits.getOrDefault(id, 0)));
		varbits.put(TfaTeamSize.SLOTS[0], 126);
		assertEquals(Integer.valueOf(1), TfaTeamSize.count(id -> varbits.getOrDefault(id, 0)));
		varbits.put(TfaTeamSize.SLOTS[1], 27);
		varbits.put(TfaTeamSize.SLOTS[2], 30);
		varbits.put(TfaTeamSize.SLOTS[4], 13);
		assertEquals(Integer.valueOf(4), TfaTeamSize.count(id -> varbits.getOrDefault(id, 0)));
		assertFalse(KillCount.isFracturedArchive("Xarth"));
	}

	@Test
	public void chambersChallengeModeCompletionMakesATime()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		assertNull(tracker.onDuration(messages.parse("Olm phase 3 duration: <col=ff0000>2:06.6</col> Total: <col=ff0000>36:02.4</col>", 90)));
		assertNull(tracker.onDuration(messages.parse("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>4 players</col> Duration:</col> <col=ff0000>37:10.80</col> Personal best: </col><col=ff0000>28:27.00</col>", 100)));
		KillTime time = tracker.onKillCount(killCounts.parse("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>689</col>.", 100));
		assertEquals("Chambers of Xeric Challenge Mode", time.getSource());
		assertEquals(Integer.valueOf(689), time.getKillCount());
		assertEquals(2230800, time.getTimeMs());
		assertEquals(Long.valueOf(1707000), time.getPersonalBestMs());
		assertEquals(Integer.valueOf(4), time.getTeamSize());
	}

	@Test
	public void chambersDuoFromTheGameChatKeepsTheTeamSize()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		assertNull(messages.parse("<col=ef20ff>Congratulations - your raid is complete!</col>", 300));
		assertNull(tracker.onDuration(messages.parse("Team size: <col=ff0000>2 players</col> Duration:</col> <col=ff0000>1:03:47.40</col> Personal best: </col><col=ff0000>30:32.40</col> Olm duration:</col> <col=ff0000>17:10.8</col>", 300)));
		KillTime time = tracker.onKillCount(killCounts.parse("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>691</col>.", 300));
		assertEquals("Chambers of Xeric Challenge Mode", time.getSource());
		assertEquals(Integer.valueOf(2), time.getTeamSize());
		assertEquals(3827400, time.getTimeMs());
		assertEquals(Long.valueOf(1832400), time.getPersonalBestMs());
	}

	@Test
	public void killCountBeforeDurationMakesATime()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		assertNull(tracker.onKillCount(killCounts.parse("Your Vorkath kill count is: 50.", 10)));
		KillTime time = tracker.onDuration(messages.parse("Fight duration: 1:30. Personal best: 1:10", 10));
		assertEquals("Vorkath", time.getSource());
		assertEquals(Integer.valueOf(50), time.getKillCount());
		assertEquals(90000, time.getTimeMs());
		assertNull(tracker.onDuration(messages.parse("Fight duration: 1:31", 11)));
	}

	@Test
	public void raidPrefersTheTotalOverRoomTimes()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		assertNull(tracker.onDuration(messages.parse("Wave 'The Final Challenge' complete! Duration: 3:10", 100)));
		assertNull(tracker.onDuration(messages.parse("Theatre of Blood total completion time: 20:15. Personal best: 19:00", 100)));
		KillTime time = tracker.onKillCount(killCounts.parse("Your completed Theatre of Blood count is: 12.", 101));
		assertEquals("Theatre of Blood", time.getSource());
		assertEquals(1215000, time.getTimeMs());
		assertEquals(Long.valueOf(1140000), time.getPersonalBestMs());
	}

	@Test
	public void oldDurationsAreNotGluedToALaterKill()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		tracker.onDuration(messages.parse("Duration: 2:00", 10));
		assertNull(tracker.onKillCount(killCounts.parse("Your Zulrah kill count is: 5.", 40)));
	}

	@Test
	public void teamSizesKeepTheirRange()
	{
		TeamSize range = TeamSize.parse("Team size: 11-15 players Duration: 30:00");
		assertEquals(11, range.getMin());
		assertEquals(Integer.valueOf(15), range.getMax());
		assertEquals("11-15 players", range.getLabel());
		TeamSize open = TeamSize.parse("Team size: 24+ players");
		assertEquals(24, open.getMin());
		assertNull(open.getMax());
		TeamSize solo = TeamSize.parse("Team size: Solo Fight duration: 1:00");
		assertEquals(1, solo.getMin());
		assertEquals(Integer.valueOf(1), solo.getMax());
		TeamSize three = TeamSize.parse("Team size: 3 players");
		assertEquals(Integer.valueOf(3), three.getMax());
		assertNull(TeamSize.parse("Fight duration: 1:00"));
		assertEquals("Solo", TeamSize.exact(1).getLabel());
		assertEquals("4 players", TeamSize.exact(4).getLabel());
	}

	@Test
	public void timePrecisionFollowsTheGameSetting()
	{
		assertTrue(DurationParser.isPrecise("1:23.40"));
		assertFalse(DurationParser.isPrecise("1:23"));
		assertTrue(messages.parse("Fight duration: <col=ff0000>1:23.40</col>", 5).isPrecise());
		assertFalse(messages.parse("Fight duration: <col=ff0000>1:23</col>", 5).isPrecise());
	}

	@Test
	public void impossiblyShortTimesAreIgnored()
	{
		// A King Black Dragon kill was once stored as 0:03 (live database, KC 202).
		assertNull(messages.parse("Fight duration: <col=ff0000>0:03</col>", 5));
		assertTrue(messages.parse("Fight duration: <col=ff0000>0:05</col>", 5) != null);
	}

	@Test
	public void killTimeGetsTheTeamSizeRangeAndPrecision()
	{
		KillTimeTracker tracker = new KillTimeTracker();
		tracker.onDuration(messages.parse("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>11-15 players</col> Duration:</col> <col=ff0000>30:00.60</col>", 10));
		KillTime time = tracker.onKillCount(killCounts.parse("Your completed Chambers of Xeric count is: <col=ff0000>52</col>.", 11));
		assertEquals(Integer.valueOf(11), time.getTeamSize());
		assertEquals(Integer.valueOf(15), time.getTeamSizeMax());
		assertEquals("11-15 players", time.getTeamSizeLabel());
		assertTrue(time.isTimePrecise());
	}

	@Test
	public void queueCarriesKillTimes() throws Exception
	{
		LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
		KillTime time = new KillTime();
		time.setUid("t1");
		queue.add(time);
		assertEquals(1, queue.size());
		assertEquals(1, queue.next(10).getKillTimes().size());
		assertEquals(2, queue.next(10).getSchemaVersion());
		queue.remove(Collections.singleton("t1"));
		assertEquals(0, queue.size());
	}
}
