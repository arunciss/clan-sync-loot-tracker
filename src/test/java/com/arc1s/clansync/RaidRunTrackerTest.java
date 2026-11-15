package com.arc1s.clansync;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RaidRunTrackerTest
{
	private final KillCountParser parser = new KillCountParser();
	private final RaidRunTracker tracker = new RaidRunTracker();
	private final FakeContext context = new FakeContext();

	private static class FakeContext implements RaidContext
	{
		final Map<Integer, Integer> varbits = new HashMap<>();
		final Map<Integer, Integer> varps = new HashMap<>();
		final Map<Integer, String> varcs = new HashMap<>();
		List<String> coxParty = Collections.emptyList();
		List<String> scene = Collections.emptyList();
		String local;

		public List<String> coxPartyNames()
		{
			return coxParty;
		}

		public List<String> raidPlayerNames()
		{
			return scene;
		}

		public String localPlayerName()
		{
			return local;
		}

		public int varbit(int id)
		{
			return varbits.getOrDefault(id, 0);
		}

		public int varp(int id)
		{
			return varps.getOrDefault(id, 0);
		}

		public String varcStr(int id)
		{
			return varcs.get(id);
		}
	}

	private RaidRun finish(String killCountMessage, int tick)
	{
		tracker.onKillCount(parser.parse(killCountMessage, tick), context);
		assertNull(tracker.poll(tick + 1));
		return tracker.poll(tick + RaidRunTracker.FINISH_DELAY_TICKS);
	}

	@Test
	public void theatreOfBloodWavesTeamAndTimes()
	{
		context.varbits.put(VarbitID.TOB_CLIENT_P0, 1);
		context.varbits.put(VarbitID.TOB_CLIENT_P1, 15);
		context.varcs.put(VarClientID.TOB_CLIENT_NAME0, "Aruna<col=ffffff>s</col>");
		context.varcs.put(VarClientID.TOB_CLIENT_NAME1, "Friend\u00A0Two");
		context.varcs.put(VarClientID.TOB_CLIENT_NAME2, "");
		tracker.onMessage("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>2:05.4</col><br>Total completion time: <col=ff0000>2:05.4</col>", 100, context);
		tracker.onMessage("Wave 'The Pestilent Bloat' (Normal Mode) complete!<br>Duration: <col=ff0000>1:10.20</col><br>Total completion time: <col=ff0000>3:40</col>", 300, context);
		tracker.onMessage("Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>2:42.0</col><br>Theatre of Blood completion time: <col=ff0000>17:00.20</col> (new personal best)", 900, context);
		tracker.onMessage("Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00", 901, context);
		// Party slots are gone once you leave; what was seen during the raid is kept.
		context.varbits.clear();
		context.varcs.clear();
		RaidRun run = finish("Your completed Theatre of Blood count is: <col=ff0000>73</col>.", 902);

		assertEquals("Theatre of Blood", run.getRaid());
		assertEquals(Integer.valueOf(73), run.getKillCount());
		assertEquals(Arrays.asList("Arunas", "Friend Two"), run.getTeam());
		assertEquals(RaidRun.TEAM_PARTY, run.getTeamSource());
		assertEquals(Integer.valueOf(2), run.getTeamSize());
		assertNull(run.getTotalPoints());
		assertNull(run.getPersonalPoints());
		assertEquals(3, run.getRooms().size());
		assertEquals("The Maiden of Sugadinti", run.getRooms().get(0).getName());
		assertEquals(125400L, run.getRooms().get(0).getTimeMs());
		assertEquals(70200L, run.getRooms().get(1).getTimeMs());
		assertNull(run.getRooms().get(2).getGamePersonalBestMs());
		assertEquals(Long.valueOf(1020200L), run.getChallengeTimeMs());
		assertEquals(Long.valueOf(1020200L), run.getChallengePersonalBestMs());
		assertEquals(Long.valueOf(1480200L), run.getTotalTimeMs());
		assertEquals(Long.valueOf(1245000L), run.getTotalPersonalBestMs());
		assertFalse(run.isTotalNewPersonalBest());
		assertNull(tracker.poll(2000));
	}

	@Test
	public void killCountBeforeTotalStillCollectsTotal()
	{
		tracker.onMessage("Wave 'The Final Challenge' (Hard Mode) complete!<br>Duration: <col=ff0000>2:42.0</col><br>Theatre of Blood completion time: <col=ff0000>17:00</col>. Personal best: 13:52.80", 50, context);
		tracker.onKillCount(parser.parse("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>5</col>.", 51), context);
		tracker.onMessage("Theatre of Blood total completion time: <col=ff0000>24:40.20</col> (new personal best)", 52, context);
		RaidRun run = tracker.poll(54);
		assertEquals("Theatre of Blood: Hard Mode", run.getRaid());
		assertEquals(Long.valueOf(1480200L), run.getTotalTimeMs());
		assertTrue(run.isTotalNewPersonalBest());
		assertEquals(Long.valueOf(832800L), run.getChallengePersonalBestMs());
		assertEquals(1, run.getRooms().size());
	}

	@Test
	public void chambersFloorsPointsAndNoBystanderNames()
	{
		context.varbits.put(VarbitID.RAIDS_CLIENT_PARTYSIZE, 3);
		context.varbits.put(VarbitID.RAIDS_CLIENT_PARTYSCORE, 90123);
		context.varps.put(VarPlayerID.RAIDS_PLAYERSCORE, 31000);
		tracker.onMessage("The raid has begun!", 10, context);
		tracker.onMessage("Upper level complete! Duration: <col=ff0000>8:10</col>", 500, context);
		tracker.onMessage("Middle level complete! Duration: <col=ff0000>20:50</col>", 1500, context);
		tracker.onMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>41:10</col> Personal best: </col><col=ff0000>40:03</col>", 3000, context);
		RaidRun run = finish("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>13</col>.", 3001);

		assertEquals("Chambers of Xeric Challenge Mode", run.getRaid());
		assertEquals(Integer.valueOf(3), run.getTeamSize());
		assertEquals(Integer.valueOf(90123), run.getTotalPoints());
		assertEquals(Integer.valueOf(31000), run.getPersonalPoints());
		// Players merely seen on screen are never sent; CoX has no party name list.
		assertTrue(run.getTeam() == null || run.getTeam().isEmpty());
		assertFalse("scene".equals(run.getTeamSource()));
		assertFalse(run.isTimePrecise());
		assertEquals(3, run.getRooms().size());
		assertEquals("Upper level", run.getRooms().get(0).getName());
		assertEquals(RaidRoom.KIND_FLOOR, run.getRooms().get(0).getKind());
		assertEquals(490000L, run.getRooms().get(0).getTimeMs());
		assertEquals(760000L, run.getRooms().get(1).getTimeMs());
		assertEquals("Olm", run.getRooms().get(2).getName());
		assertEquals(RaidRoom.KIND_REST, run.getRooms().get(2).getKind());
		assertEquals(1220000L, run.getRooms().get(2).getTimeMs());
		assertEquals(Long.valueOf(2470000L), run.getTotalTimeMs());
		assertEquals(Long.valueOf(2403000L), run.getTotalPersonalBestMs());
	}

	@Test
	public void chambersWithCoxTimersRooms()
	{
		tracker.onMessage("Combat room `Tekton` complete! Duration: 3:19 Total: 3:19", 100, context);
		tracker.onMessage("Puzzle 'Ice demon' complete! Duration: 1:01.2 Total: 4:20.2", 200, context);
		tracker.onMessage("Upper level complete! Duration: 7:53 Total: 20:50", 300, context);
		tracker.onMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>24+ players</col> Duration:</col> <col=ff0000>37:04.20</col> (new personal best)</col>>", 400, context);
		RaidRun run = finish("Your completed Chambers of Xeric count is: <col=ff0000>51</col>.", 401);
		assertEquals(Integer.valueOf(24), run.getTeamSize());
		assertTrue(run.isTimePrecise());
		assertEquals(4, run.getRooms().size());
		assertEquals("Tekton", run.getRooms().get(0).getName());
		assertEquals(199000L, run.getRooms().get(0).getTimeMs());
		assertEquals("Ice demon", run.getRooms().get(1).getName());
		assertEquals(1250000L, run.getRooms().get(2).getTimeMs());
		assertEquals(974200L, run.getRooms().get(3).getTimeMs());
		assertTrue(run.isTotalNewPersonalBest());
		assertNull(run.getTotalPoints());
	}

	@Test
	public void tombsRoomsAndWardensPbBelongsToRaid()
	{
		for (int i = 0; i < 4; i++)
		{
			context.varbits.put(RaidRunTracker.TOA_SLOTS[i], 1);
			context.varcs.put(RaidRunTracker.TOA_NAMES[i], "Player " + i);
		}
		tracker.onMessage("Challenge complete: Path of Crondis. Duration: <col=ef1020>0:34.20</col>. Total: <col=ef1020>0:34.20</col>", 10, context);
		tracker.onMessage("Challenge complete: Zebak. Duration: <col=ef1020>2:01</col>. Total: <col=ef1020>3:00</col>. Personal best: 1:50.40", 300, context);
		tracker.onMessage("Challenge complete: The Wardens. Duration: <col=ef1020>9:40</col><br>Tombs of Amascut: Expert Mode challenge completion time: <col=ef1020>9:40</col>. Personal best: 8:31", 900, context);
		tracker.onMessage("Tombs of Amascut: Expert Mode total completion time: <col=ef1020>34:07.20</col>. Personal best: 28:16.20", 901, context);
		RaidRun run = finish("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>1</col>.", 902);
		assertEquals("Tombs of Amascut: Expert Mode", run.getRaid());
		assertEquals(Integer.valueOf(4), run.getTeamSize());
		assertEquals(4, run.getTeam().size());
		assertEquals(3, run.getRooms().size());
		assertEquals(Long.valueOf(110400L), run.getRooms().get(1).getGamePersonalBestMs());
		assertNull(run.getRooms().get(2).getGamePersonalBestMs());
		assertEquals(Long.valueOf(511000L), run.getChallengePersonalBestMs());
		assertEquals(Long.valueOf(2047200L), run.getTotalTimeMs());
		assertEquals(Long.valueOf(1696200L), run.getTotalPersonalBestMs());
		assertNull(run.getTotalPoints());
	}

	@Test
	public void fracturedArchiveRoomPb()
	{
		context.varbits.put(VarbitID.TFA_CLIENT_P0, 1);
		context.varcs.put(VarClientID.TFA_CLIENT_NAME0, "Arunas");
		tracker.onMessage("Challenge complete: Ocellus. Duration: <col=ef1020>14:28.80</col>. Total: <col=ef1020>36:01.80</col>. Personal best: 13:54.60", 10, context);
		tracker.onMessage("The Fractured Archive: Total completion time: <col=ef1020>36:01.80</col>. Personal best: 30:12", 11, context);
		RaidRun run = finish("Your completed Fractured Archive count is: <col=ff0000>3</col>.", 12);
		assertEquals("Fractured Archive", run.getRaid());
		assertEquals(Integer.valueOf(1), run.getTeamSize());
		assertEquals(Collections.singletonList("Arunas"), run.getTeam());
		assertEquals("Ocellus", run.getRooms().get(0).getName());
		assertEquals(868800L, run.getRooms().get(0).getTimeMs());
		assertEquals(Long.valueOf(834600L), run.getRooms().get(0).getGamePersonalBestMs());
		assertEquals(Long.valueOf(2161800L), run.getTotalTimeMs());
		assertEquals(Long.valueOf(1812000L), run.getTotalPersonalBestMs());
	}

	@Test
	public void raidLeftWithoutKillCountDoesNotLeakIntoNextRaid()
	{
		tracker.onMessage("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>2:05.4</col>", 10, context);
		tracker.onMessage("Wave 'The Pestilent Bloat' (Normal Mode) complete!<br>Duration: <col=ff0000>1:00</col>", 20, context);
		tracker.onMessage("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>2:00</col>", 500, context);
		tracker.onMessage("Challenge complete: Path of Crondis. Duration: 0:34.20. Total: 0:34.20", 600, context);
		tracker.onMessage("Challenge complete: Zebak. Duration: 1:00. Total: 1:34.20", 600 + RaidRunTracker.STALE_TICKS + 1, context);
		RaidRun run = finish("Your completed Tombs of Amascut count is: <col=ff0000>2</col>.", 5000);
		assertEquals(1, run.getRooms().size());
		assertEquals("Zebak", run.getRooms().get(0).getName());
	}

	@Test
	public void nonRaidMessagesAreIgnored()
	{
		tracker.onMessage("Fight duration: <col=ff0000>1:02</col>. Personal best: 0:58", 10, context);
		tracker.onKillCount(parser.parse("Your Zulrah kill count is: <col=ff0000>1,234</col>.", 11), context);
		assertNull(tracker.poll(100));
		assertNull(RaidRunTracker.name("<img=2>"));
		assertNull(RaidRunTracker.name("Way too long name"));
		assertEquals("Lynx Titan", RaidRunTracker.name("Lynx_Titan"));
	}

	@Test
	public void chambersRangedTeamSizeIsKeptForTheKillTime()
	{
		tracker.onMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>11-15 players</col> Duration:</col> <col=ff0000>30:00</col>", 400, context);
		tracker.onKillCount(parser.parse("Your completed Chambers of Xeric count is: <col=ff0000>52</col>.", 401), context);
		TeamSize size = tracker.teamSizeFor("Chambers of Xeric");
		assertEquals(11, size.getMin());
		assertEquals(Integer.valueOf(15), size.getMax());
		assertEquals("11-15 players", size.getLabel());
		assertNull(tracker.teamSizeFor("Zulrah"));
		assertNull(tracker.raidLevelFor("Chambers of Xeric"));
		RaidRun run = tracker.poll(401 + RaidRunTracker.FINISH_DELAY_TICKS);
		assertEquals(Integer.valueOf(11), run.getTeamSize());
	}

	@Test
	public void tombsRaidLevelAndExactTeamSize()
	{
		context.varbits.put(VarbitID.TOA_CLIENT_RAID_LEVEL, 425);
		for (int i = 0; i < 3; i++)
		{
			context.varbits.put(RaidRunTracker.TOA_SLOTS[i], 1);
			context.varcs.put(RaidRunTracker.TOA_NAMES[i], "Player " + i);
		}
		tracker.onMessage("Challenge complete: Path of Crondis. Duration: <col=ef1020>0:34.20</col>. Total: <col=ef1020>0:34.20</col>", 10, context);
		tracker.onMessage("Tombs of Amascut: Expert Mode total completion time: <col=ef1020>34:07.20</col>. Personal best: 28:16.20", 901, context);
		tracker.onKillCount(parser.parse("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>7</col>.", 902), context);
		assertEquals(Integer.valueOf(425), tracker.raidLevelFor("Tombs of Amascut: Expert Mode"));
		TeamSize size = tracker.teamSizeFor("Tombs of Amascut: Expert Mode");
		assertEquals(3, size.getMin());
		assertEquals(Integer.valueOf(3), size.getMax());
		RaidRun run = tracker.poll(902 + RaidRunTracker.FINISH_DELAY_TICKS);
		assertEquals(Integer.valueOf(425), run.getRaidLevel());
		assertEquals(Arrays.asList("Player 0", "Player 1", "Player 2"), run.getTeam());
		assertTrue(run.isTimePrecise());
	}

	@Test
	public void queueKeepsRaidRunsAndLoadsOldFiles() throws Exception
	{
		MemoryStore files = new MemoryStore();
		files.write(LootQueue.FILE, "{\"schemaVersion\":2,\"records\":[],\"killTimes\":[],\"itemNames\":{}}");
		LootQueue queue = new LootQueue(files, new Gson());
		assertEquals(0, queue.size());
		RaidRun run = new RaidRun();
		run.setUid("11111111-1111-1111-1111-111111111111");
		run.setRaid("Theatre of Blood");
		queue.add(run);
		LootQueue reloaded = new LootQueue(files, new Gson());
		assertEquals(1, reloaded.size());
		LootBatch batch = reloaded.next(200);
		assertEquals("Theatre of Blood", batch.getRaidRuns().get(0).getRaid());
		reloaded.remove(Collections.singleton(run.getUid()));
		assertEquals(0, reloaded.size());
	}

	// Exact chat lines (FRIENDSCHATNOTIFICATION / SPAM) from a real Chambers of Xeric Challenge Mode run with the
	// CoX Timers plugin, 2026-11-03 19:46-20:20, client.log; seconds since "The raid has begun!".
	private static final Object[][] COX_CM_RAID_730 = {
		{0, "<col=ef20ff>The raid has begun!</col>"},
		{83, "Combat room `Tekton` complete! Duration: <col=ff0000>1:22.8</col> Total: <col=ff0000>1:22.8</col>"},
		{144, "Puzzle `Crabs` complete! Duration: <col=ff0000>1:01.8</col> Total: <col=ff0000>2:24.6</col>"},
		{332, "Puzzle `Ice demon` complete! Duration: <col=ff0000>3:07.2</col> Total: <col=ff0000>5:31.8</col>"},
		{429, "Combat room `Shamans` complete! Duration: <col=ff0000>1:37.2</col> Total: <col=ff0000>7:09.0</col>"},
		{454, "@mes_hl_mag@Upper level complete! Duration: </col><col=ff0000>7:34.20</col>"},
		{740, "Combat room `Vanguards` complete! Duration: <col=ff0000>4:46.2</col> Total: <col=ff0000>12:20.4</col>"},
		{843, "Puzzle `Thieving` complete! Duration: <col=ff0000>1:43.2</col> Total: <col=ff0000>14:03.6</col>"},
		{920, "Combat room `Vespula` complete! Duration: <col=ff0000>1:16.8</col> Total: <col=ff0000>15:20.4</col>"},
		{1040, "Puzzle `Tightrope` complete! Duration: <col=ff0000>2:00.0</col> Total: <col=ff0000>17:20.4</col>"},
		{1055, "@mes_hl_mag@Middle level complete! Duration: </col><col=ff0000>17:35.40</col>"},
		{1194, "Combat room `Guardians` complete! Duration: <col=ff0000>2:18.6</col> Total: <col=ff0000>19:54.0</col>"},
		{1270, "Combat room `Vasa` complete! Duration: <col=ff0000>1:16.2</col> Total: <col=ff0000>21:10.2</col>"},
		{1376, "Combat room `Mystics` complete! Duration: <col=ff0000>1:46.2</col> Total: <col=ff0000>22:56.4</col>"},
		{1532, "Combat room `Muttadiles` complete! Duration: <col=ff0000>2:36.0</col> Total: <col=ff0000>25:32.4</col>"},
		{1589, "@mes_hl_mag@Lower level complete! Duration: </col><col=ff0000>26:28.80</col>"},
		{1738, "Olm phase 1 duration: <col=ff0000>1:49.2</col> Total: <col=ff0000>28:58.2</col>"},
		{1853, "Olm phase 2 duration: <col=ff0000>1:31.8</col> Total: <col=ff0000>30:53.4</col>"},
		{2010, "Olm phase 3 duration: <col=ff0000>2:13.2</col> Total: <col=ff0000>33:29.4</col>"},
		{2057, "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>2 players</col> Duration:</col> <col=ff0000>34:16.80</col> Personal best: </col><col=ff0000>30:32.40</col>"},
		{2057, "<colNORMAL>Total points: <colHIGHLIGHT>101,214<colNORMAL>, Personal points: <colHIGHLIGHT>50,424<colNORMAL> (<colHIGHLIGHT>49.82<colNORMAL>%)"},
	};

	// The same for the next run, 2026-11-03 20:28-21:00 (KC 731).
	private static final Object[][] COX_CM_RAID_731 = {
		{0, "<col=ef20ff>The raid has begun!</col>"},
		{108, "Combat room `Tekton` complete! Duration: <col=ff0000>1:47.4</col> Total: <col=ff0000>1:47.4</col>"},
		{170, "Puzzle `Crabs` complete! Duration: <col=ff0000>1:01.8</col> Total: <col=ff0000>2:49.2</col>"},
		{347, "Puzzle `Ice demon` complete! Duration: <col=ff0000>2:57.6</col> Total: <col=ff0000>5:46.8</col>"},
		{431, "Combat room `Shamans` complete! Duration: <col=ff0000>1:24.0</col> Total: <col=ff0000>7:10.8</col>"},
		{461, "@mes_hl_mag@Upper level complete! Duration: </col><col=ff0000>7:40.20</col>"},
		{710, "Combat room `Vanguards` complete! Duration: <col=ff0000>4:09.0</col> Total: <col=ff0000>11:49.2</col>"},
		{802, "Puzzle `Thieving` complete! Duration: <col=ff0000>1:31.8</col> Total: <col=ff0000>13:21.0</col>"},
		{871, "Combat room `Vespula` complete! Duration: <col=ff0000>1:09.6</col> Total: <col=ff0000>14:30.6</col>"},
		{991, "Puzzle `Tightrope` complete! Duration: <col=ff0000>2:00.0</col> Total: <col=ff0000>16:30.6</col>"},
		{1006, "@mes_hl_mag@Middle level complete! Duration: </col><col=ff0000>16:45.60</col>"},
		{1138, "Combat room `Guardians` complete! Duration: <col=ff0000>2:11.4</col> Total: <col=ff0000>18:57.0</col>"},
		{1240, "Combat room `Vasa` complete! Duration: <col=ff0000>1:42.6</col> Total: <col=ff0000>20:39.6</col>"},
		{1349, "Combat room `Mystics` complete! Duration: <col=ff0000>1:48.6</col> Total: <col=ff0000>22:28.2</col>"},
		{1447, "Combat room `Muttadiles` complete! Duration: <col=ff0000>1:38.4</col> Total: <col=ff0000>24:06.6</col>"},
		{1487, "@mes_hl_mag@Lower level complete! Duration: </col><col=ff0000>24:46.20</col>"},
		{1628, "Olm phase 1 duration: <col=ff0000>1:48.0</col> Total: <col=ff0000>27:07.2</col>"},
		{1754, "Olm phase 2 duration: <col=ff0000>1:42.6</col> Total: <col=ff0000>29:13.2</col>"},
		{1906, "Olm phase 3 duration: <col=ff0000>2:09.0</col> Total: <col=ff0000>31:45.6</col>"},
		{1962, "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>2 players</col> Duration:</col> <col=ff0000>32:41.40</col> Personal best: </col><col=ff0000>30:32.40</col>"},
		{1962, "<colNORMAL>Total points: <colHIGHLIGHT>107,450<colNORMAL>, Personal points: <colHIGHLIGHT>55,091<colNORMAL> (<colHIGHLIGHT>51.27<colNORMAL>%)"},
	};

	private RaidRun replay(Object[][] lines, int killCount)
	{
		int start = 1000;
		int tick = start;
		for (Object[] line : lines)
		{
			tick = start + (int) Math.round((Integer) line[0] / 0.6);
			tracker.onMessage((String) line[1], tick, context);
		}
		return finish("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>" + killCount + "</col>.", tick);
	}

	private static void assertRooms(RaidRun run, Object[][] expected)
	{
		assertEquals(expected.length, run.getRooms().size());
		for (int i = 0; i < expected.length; i++)
		{
			RaidRoom room = run.getRooms().get(i);
			assertEquals("position " + i, expected[i][0], room.getName());
			assertEquals("position " + i, expected[i][1], room.getKind());
			assertEquals("position " + i, ((Number) expected[i][2]).longValue(), room.getTimeMs());
			assertNull(room.getGamePersonalBestMs());
			assertFalse(room.isNewPersonalBest());
		}
	}

	@Test
	public void cleanStripsGameFormatPrefix()
	{
		assertEquals("Upper level complete! Duration: 7:34.20",
			RaidRunTracker.clean("@mes_hl_mag@Upper level complete! Duration: </col><col=ff0000>7:34.20</col>"));
		assertEquals("The Great Olm fires a sphere of magical power your way. Your prayers have been sapped.",
			RaidRunTracker.clean("@mes_hl_blu@The Great Olm fires a sphere of magical power your way.</col> Your prayers have been sapped."));
		// Only a leading code is a format code; "@" elsewhere stays.
		assertEquals("a @b@ c", RaidRunTracker.clean("a @b@ c"));
	}

	@Test
	public void realChambersChallengeModeRunKeepsFloorsAndOlm()
	{
		context.varbits.put(VarbitID.RAIDS_CLIENT_PARTYSIZE, 2);
		context.varbits.put(VarbitID.RAIDS_CLIENT_PARTYSCORE, 101214);
		context.varps.put(VarPlayerID.RAIDS_PLAYERSCORE, 50424);
		RaidRun run = replay(COX_CM_RAID_730, 730);
		assertEquals("Chambers of Xeric Challenge Mode", run.getRaid());
		assertEquals(Integer.valueOf(730), run.getKillCount());
		assertEquals(Integer.valueOf(2), run.getTeamSize());
		assertEquals(Integer.valueOf(101214), run.getTotalPoints());
		assertEquals(Integer.valueOf(50424), run.getPersonalPoints());
		assertEquals(Long.valueOf(2056800L), run.getTotalTimeMs());
		assertEquals(Long.valueOf(1832400L), run.getTotalPersonalBestMs());
		assertRooms(run, new Object[][]{
			{"Tekton", RaidRoom.KIND_ROOM, 82800}, {"Crabs", RaidRoom.KIND_ROOM, 61800},
			{"Ice demon", RaidRoom.KIND_ROOM, 187200}, {"Shamans", RaidRoom.KIND_ROOM, 97200},
			{"Upper level", RaidRoom.KIND_FLOOR, 454200},
			{"Vanguards", RaidRoom.KIND_ROOM, 286200}, {"Thieving", RaidRoom.KIND_ROOM, 103200},
			{"Vespula", RaidRoom.KIND_ROOM, 76800}, {"Tightrope", RaidRoom.KIND_ROOM, 120000},
			{"Middle level", RaidRoom.KIND_FLOOR, 601200},
			{"Guardians", RaidRoom.KIND_ROOM, 138600}, {"Vasa", RaidRoom.KIND_ROOM, 76200},
			{"Mystics", RaidRoom.KIND_ROOM, 106200}, {"Muttadiles", RaidRoom.KIND_ROOM, 156000},
			{"Lower level", RaidRoom.KIND_FLOOR, 533400},
			{"Olm", RaidRoom.KIND_REST, 468000},
		});
	}

	@Test
	public void realChambersChallengeModeSecondRun()
	{
		RaidRun run = replay(COX_CM_RAID_731, 731);
		assertEquals(Long.valueOf(1961400L), run.getTotalTimeMs());
		assertEquals(16, run.getRooms().size());
		assertEquals("Upper level", run.getRooms().get(4).getName());
		assertEquals(460200L, run.getRooms().get(4).getTimeMs());
		assertEquals("Middle level", run.getRooms().get(9).getName());
		assertEquals(545400L, run.getRooms().get(9).getTimeMs());
		assertEquals("Lower level", run.getRooms().get(14).getName());
		assertEquals(480600L, run.getRooms().get(14).getTimeMs());
		assertEquals("Olm", run.getRooms().get(15).getName());
		assertEquals(RaidRoom.KIND_REST, run.getRooms().get(15).getKind());
		assertEquals(475200L, run.getRooms().get(15).getTimeMs());
	}

	@Test
	public void chambersPartyListNames()
	{
		context.varbits.put(VarbitID.RAIDS_CLIENT_PARTYSIZE, 2);
		context.local = "Arc1s";
		context.coxParty = Arrays.asList("<col=ffffff>Arc1s</col>", "Friend\u00A0Two", "<col=ffffff>Arc1s</col>");
		context.scene = Arrays.asList("Arc1s", "Friend Two");
		RaidRun run = replay(COX_CM_RAID_730, 730);
		assertEquals(Arrays.asList("Arc1s", "Friend Two"), run.getTeam());
		assertEquals(RaidRun.TEAM_PARTY, run.getTeamSource());
	}

	@Test
	public void chambersFallsBackToPlayersInTheRaid()
	{
		context.varbits.put(VarbitID.RAIDS_CLIENT_PARTYSIZE, 2);
		context.local = "Arc1s";
		// A list without you, or with things that are not names, is not trusted.
		context.coxParty = Arrays.asList("Someone", "126");
		context.scene = Arrays.asList("Arc1s", "Mate");
		RaidRun run = replay(COX_CM_RAID_730, 730);
		assertEquals(Arrays.asList("Arc1s", "Mate"), run.getTeam());
		assertEquals(RaidRun.TEAM_SCENE, run.getTeamSource());
		assertEquals(Integer.valueOf(2), run.getTeamSize());
	}

	@Test
	public void chambersSceneNamesDroppedWhenMoreThanTheParty()
	{
		context.varbits.put(VarbitID.RAIDS_CLIENT_PARTYSIZE, 2);
		context.scene = Arrays.asList("Arc1s", "Mate", "Stranger");
		RaidRun run = replay(COX_CM_RAID_730, 730);
		assertTrue(run.getTeam() == null || run.getTeam().isEmpty());
		assertNull(run.getTeamSource());
		assertEquals(16, run.getRooms().size());
	}
}
