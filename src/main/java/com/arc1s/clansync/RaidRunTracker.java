package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

// Collects one raid's room times, party and points from chat messages and party varbits, and turns them into
// a RaidRun when the raid's kill count message arrives. Only messages whose format is known are parsed:
//  - Chambers of Xeric: "<floor> level complete! Duration: T" (game, T is the raid time so far) and
//    "Congratulations - your raid is complete! Team size: N Duration: T Personal best: P" (game, RuneLite tests);
//    "Combat room `X` complete! Duration: T Total: T" / "Puzzle `X` complete! ..." come from the optional
//    CoX Timers plugin, which also rewrites the floor message to "Duration: split Total: raid time".
//  - Theatre of Blood: "Wave 'X' (Normal|Hard|Entry Mode) complete! Duration: T ...",
//    "Theatre of Blood completion time: T" and "Theatre of Blood total completion time: T".
//  - Tombs of Amascut and The Fractured Archive: "Challenge complete: X. Duration: T. Total: T[. Personal best: P]",
//    "... challenge completion time: T" and "... total completion time: T".
// Points exist only in Chambers of Xeric (party and personal score varbits, as RuneLite's raids plugin reads).
public class RaidRunTracker
{
	static final String COX = "cox";
	static final String TOB = "tob";
	static final String TOA = "toa";
	static final String TFA = "tfa";

	// Kill count can arrive a few ticks before the last completion messages, so a run is closed a bit later.
	static final int FINISH_DELAY_TICKS = 3;
	// A room message long after the previous one belongs to a new raid (raid left without a kill count).
	static final int STALE_TICKS = 3000;
	static final int MAX_ROOMS = 40;
	private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9 _\\-]{1,12}$");

	private static final String T = DurationParser.TIME;
	private static final Pattern COX_START = Pattern.compile("^The raid has begun!");
	private static final Pattern TOA_ENTER = Pattern.compile("^You enter the Tombs of Amascut");
	private static final Pattern COX_FLOOR = Pattern.compile("^(?<name>[A-Za-z]+) level complete! Duration:\\s*(?<d>" + T + ")(?:\\s*Total:\\s*(?<t>" + T + "))?");
	private static final Pattern COX_ROOM = Pattern.compile("^(?:Combat room|Puzzle) [`'\u2018\u2019](?<name>[^`'\u2018\u2019]{1,40})[`'\u2018\u2019] complete! Duration:\\s*(?<d>" + T + ")");
	private static final Pattern COX_COMPLETE = Pattern.compile("^Congratulations - your raid is complete!.*?Duration:\\s*(?<t>" + T + ")");
	private static final Pattern TOB_WAVE = Pattern.compile("^Wave '(?<name>[^']{1,40})'(?: \\([A-Za-z]+ Mode\\))? complete!\\s*Duration:\\s*(?<d>" + T + ")");
	private static final Pattern CHALLENGE = Pattern.compile("^Challenge complete: (?<name>[^.]{1,40})\\. Duration:\\s*(?<d>" + T + ")");
	private static final Pattern TOTAL_TIME = Pattern.compile("total completion time:?\\s*(?<t>" + T + ")", Pattern.CASE_INSENSITIVE);
	private static final Pattern CHALLENGE_TIME = Pattern.compile("(?<![Tt]otal )completion time:?\\s*(?<t>" + T + ")");
	private static final Pattern PERSONAL_BEST = Pattern.compile("Personal best:?\\s*(?<t>" + T + ")", Pattern.CASE_INSENSITIVE);
	private static final Pattern PB_RIGHT_AFTER = Pattern.compile("^\\s*\\.?\\s*(?:Personal best:?\\s*(?<t>" + T + ")|(?<new>\\(new personal best\\)))", Pattern.CASE_INSENSITIVE);
	private static final Set<String> TOA_ROOMS = new HashSet<>(Arrays.asList("path of crondis", "zebak", "path of apmeken", "ba-ba",
		"path of scabaras", "kephri", "path of het", "akkha", "the wardens"));

	static final int[] TOB_SLOTS = {VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4};
	static final int[] TOA_SLOTS = {VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
		VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7};
	static final int[] TOB_NAMES = {VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME1, VarClientID.TOB_CLIENT_NAME2, VarClientID.TOB_CLIENT_NAME3, VarClientID.TOB_CLIENT_NAME4};
	static final int[] TOA_NAMES = {VarClientID.TOA_CLIENT_NAME0, VarClientID.TOA_CLIENT_NAME1, VarClientID.TOA_CLIENT_NAME2, VarClientID.TOA_CLIENT_NAME3,
		VarClientID.TOA_CLIENT_NAME4, VarClientID.TOA_CLIENT_NAME5, VarClientID.TOA_CLIENT_NAME6, VarClientID.TOA_CLIENT_NAME7};
	static final int[] TFA_NAMES = {VarClientID.TFA_CLIENT_NAME0, VarClientID.TFA_CLIENT_NAME1, VarClientID.TFA_CLIENT_NAME2, VarClientID.TFA_CLIENT_NAME3,
		VarClientID.TFA_CLIENT_NAME4, VarClientID.TFA_CLIENT_NAME5, VarClientID.TFA_CLIENT_NAME6, VarClientID.TFA_CLIENT_NAME7};

	private final DurationParser durations = new DurationParser();

	private String family;
	private int lastTick;
	private final List<RaidRoom> rooms = new ArrayList<>();
	// Raid time so far at the end of each Chambers of Xeric floor, same order as the floor rooms.
	private final List<Long> floorTotals = new ArrayList<>();
	private Long totalTimeMs;
	private Long totalPersonalBestMs;
	private boolean totalNewPersonalBest;
	private Long challengeTimeMs;
	private Long challengePersonalBestMs;
	private boolean totalPrecise;
	private TeamSize messageTeamSize;
	private List<String> partyNames = Collections.emptyList();
	private Integer slotTeamSize;
	private Integer raidLevel;
	private Integer personalPoints;
	private Integer totalPoints;
	private KillCount pending;

	public void onMessage(String message, int tick, RaidContext context)
	{
		String text = clean(message);
		if (COX_START.matcher(text).find())
		{
			reset(COX, tick);
			return;
		}
		if (TOA_ENTER.matcher(text).find())
		{
			reset(TOA, tick);
			return;
		}
		Matcher m;
		if ((m = COX_FLOOR.matcher(text)).find())
		{
			Long split = time(m.group("d"));
			Long total = m.group("t") != null ? time(m.group("t")) : split;
			if (total != null && addRoom(COX, tick, m.group("name") + " level", RaidRoom.KIND_FLOOR, 0, null, false))
			{
				floorTotals.add(total);
			}
		}
		else if ((m = COX_ROOM.matcher(text)).find())
		{
			Long time = time(m.group("d"));
			if (time != null)
			{
				addRoom(COX, tick, m.group("name").trim(), RaidRoom.KIND_ROOM, time, null, false);
			}
		}
		else if ((m = COX_COMPLETE.matcher(text)).find())
		{
			begin(COX, tick);
			totalTimeMs = time(m.group("t"));
			totalPrecise = DurationParser.isPrecise(m.group("t"));
			String rest = text.substring(m.end());
			Matcher best = PERSONAL_BEST.matcher(rest);
			totalNewPersonalBest = rest.toLowerCase(Locale.ROOT).contains("(new personal best)");
			totalPersonalBestMs = totalNewPersonalBest ? totalTimeMs : best.find() ? time(best.group("t")) : null;
			messageTeamSize = TeamSize.parse(text);
			snapshot(context);
			return;
		}
		else if ((m = TOB_WAVE.matcher(text)).find())
		{
			room(TOB, tick, m, text);
		}
		else if ((m = CHALLENGE.matcher(text)).find())
		{
			room(TOA_ROOMS.contains(m.group("name").trim().toLowerCase(Locale.ROOT)) ? TOA : TFA, tick, m, text);
		}
		totals(text, tick, context);
		if (family != null && (pending != null || tick == lastTick))
		{
			snapshot(context);
		}
	}

	private void room(String raid, int tick, Matcher m, String text)
	{
		Long time = time(m.group("d"));
		if (time == null)
		{
			return;
		}
		Long best = null;
		boolean newBest = false;
		String rest = text.substring(m.end());
		// "Challenge complete: The Wardens. Duration: T Tombs of Amascut challenge completion time: T. Personal best: P"
		// carries the raid's personal best, not the room's.
		if (!rest.toLowerCase(Locale.ROOT).contains("completion time"))
		{
			newBest = rest.toLowerCase(Locale.ROOT).contains("(new personal best)");
			Matcher pb = PERSONAL_BEST.matcher(rest);
			best = newBest ? time : pb.find() ? time(pb.group("t")) : null;
		}
		addRoom(raid, tick, m.group("name").trim(), RaidRoom.KIND_ROOM, time, best, newBest);
	}

	private void totals(String text, int tick, RaidContext context)
	{
		String lower = text.toLowerCase(Locale.ROOT);
		String raid = lower.contains("theatre of blood") ? TOB : lower.contains("tombs of amascut") ? TOA : lower.contains("fractured archive") ? TFA : null;
		if (raid == null)
		{
			return;
		}
		Matcher total = TOTAL_TIME.matcher(text);
		Matcher challenge = CHALLENGE_TIME.matcher(text);
		boolean hasTotal = total.find();
		boolean hasChallenge = challenge.find();
		if (!hasTotal && !hasChallenge)
		{
			return;
		}
		begin(raid, tick);
		if (hasTotal)
		{
			totalTimeMs = time(total.group("t"));
			totalPrecise = DurationParser.isPrecise(total.group("t"));
			Matcher pb = PB_RIGHT_AFTER.matcher(text.substring(total.end()));
			boolean found = pb.find();
			totalNewPersonalBest = found && pb.group("new") != null;
			totalPersonalBestMs = totalNewPersonalBest ? totalTimeMs : found ? time(pb.group("t")) : null;
		}
		if (hasChallenge)
		{
			challengeTimeMs = time(challenge.group("t"));
			Matcher pb = PB_RIGHT_AFTER.matcher(text.substring(challenge.end()));
			if (pb.find())
			{
				challengePersonalBestMs = pb.group("new") != null ? challengeTimeMs : time(pb.group("t"));
			}
		}
		snapshot(context);
	}

	public void onKillCount(KillCount killCount, RaidContext context)
	{
		if (killCount == null || !KillCount.isRaid(killCount.getName()))
		{
			return;
		}
		String raid = familyOf(killCount.getName());
		if (family != null && !family.equals(raid))
		{
			reset(raid, killCount.getTick());
		}
		family = raid;
		lastTick = killCount.getTick();
		pending = killCount;
		snapshot(context);
	}

	/**
	 * The team size of the raid in progress, for the kill time of the given raid kill count; null when unknown.
	 * The party slots give the exact size, otherwise the completion message's (possibly ranged) size is used.
	 */
	public TeamSize teamSizeFor(String raid)
	{
		if (family == null || raid == null || !KillCount.isRaid(raid) || !family.equals(familyOf(raid)))
		{
			return null;
		}
		if (slotTeamSize != null)
		{
			return TeamSize.exact(slotTeamSize);
		}
		if (messageTeamSize != null)
		{
			return messageTeamSize;
		}
		return partyNames.isEmpty() ? null : TeamSize.exact(partyNames.size());
	}

	/**
	 * The Tombs of Amascut raid level of the raid in progress, for the kill time of the given raid; null otherwise.
	 */
	public Integer raidLevelFor(String raid)
	{
		return TOA.equals(family) && raid != null && TOA.equals(familyOf(raid)) ? raidLevel : null;
	}

	// Returns the finished run once the kill count is a few ticks old, otherwise null.
	public RaidRun poll(int tick)
	{
		if (pending == null || tick - pending.getTick() < FINISH_DELAY_TICKS)
		{
			return null;
		}
		RaidRun run = build();
		reset(null, tick);
		return run;
	}

	private RaidRun build()
	{
		RaidRun run = new RaidRun();
		run.setRaid(pending.getName());
		run.setKillCount(pending.getCount());
		if (!partyNames.isEmpty())
		{
			run.setTeam(new ArrayList<>(partyNames));
			run.setTeamSource(RaidRun.TEAM_PARTY);
		}
		TeamSize size = slotTeamSize != null ? TeamSize.exact(slotTeamSize) : messageTeamSize;
		if (size == null && RaidRun.TEAM_PARTY.equals(run.getTeamSource()))
		{
			size = TeamSize.exact(run.getTeam().size());
		}
		run.setTeamSize(size == null ? null : size.getMin());
		if (TOA.equals(family))
		{
			run.setRaidLevel(raidLevel);
		}
		run.setTimePrecise(totalPrecise);
		if (COX.equals(family))
		{
			run.setPersonalPoints(personalPoints);
			run.setTotalPoints(totalPoints);
		}
		run.setTotalTimeMs(totalTimeMs);
		run.setTotalPersonalBestMs(totalPersonalBestMs);
		run.setTotalNewPersonalBest(totalNewPersonalBest);
		run.setChallengeTimeMs(challengeTimeMs);
		run.setChallengePersonalBestMs(challengePersonalBestMs);
		long previousFloor = 0;
		int floor = 0;
		for (RaidRoom room : rooms)
		{
			if (RaidRoom.KIND_FLOOR.equals(room.getKind()))
			{
				long total = floorTotals.get(floor++);
				if (total <= previousFloor)
				{
					continue;
				}
				room.setTimeMs(total - previousFloor);
				previousFloor = total;
			}
			run.getRooms().add(room);
		}
		if (COX.equals(family) && floor > 0 && totalTimeMs != null && totalTimeMs > previousFloor)
		{
			RaidRoom rest = new RaidRoom();
			rest.setName("Olm");
			rest.setKind(RaidRoom.KIND_REST);
			rest.setTimeMs(totalTimeMs - previousFloor);
			run.getRooms().add(rest);
		}
		return run;
	}

	private boolean addRoom(String raid, int tick, String name, String kind, long timeMs, Long best, boolean newBest)
	{
		begin(raid, tick);
		for (RaidRoom room : rooms)
		{
			if (room.getName().equalsIgnoreCase(name) && room.getKind().equals(kind))
			{
				// A raid never repeats a room, so this is the next raid after one that ended without a kill count.
				reset(raid, tick);
				break;
			}
		}
		if (rooms.size() >= MAX_ROOMS)
		{
			return false;
		}
		RaidRoom room = new RaidRoom();
		room.setName(name);
		room.setKind(kind);
		room.setTimeMs(timeMs);
		room.setGamePersonalBestMs(best);
		room.setNewPersonalBest(newBest);
		rooms.add(room);
		return true;
	}

	private void begin(String raid, int tick)
	{
		if (pending != null && raid.equals(family))
		{
			lastTick = tick;
			return;
		}
		if (family == null || !family.equals(raid) || tick - lastTick > STALE_TICKS || pending != null)
		{
			reset(raid, tick);
		}
		lastTick = tick;
	}

	private void reset(String raid, int tick)
	{
		family = raid;
		lastTick = tick;
		rooms.clear();
		floorTotals.clear();
		totalTimeMs = null;
		totalPrecise = false;
		totalPersonalBestMs = null;
		totalNewPersonalBest = false;
		challengeTimeMs = null;
		challengePersonalBestMs = null;
		messageTeamSize = null;
		partyNames = Collections.emptyList();
		slotTeamSize = null;
		raidLevel = null;
		personalPoints = null;
		totalPoints = null;
		pending = null;
	}

	// Reads the raid's own party slots and score varbits. Only names in the raid party are read; other players
	// around you are never looked at.
	private void snapshot(RaidContext context)
	{
		if (context == null || family == null)
		{
			return;
		}
		int[] names = TOB.equals(family) ? TOB_NAMES : TOA.equals(family) ? TOA_NAMES : TFA.equals(family) ? TFA_NAMES : null;
		if (names != null)
		{
			List<String> party = new ArrayList<>();
			for (int id : names)
			{
				String name = name(context.varcStr(id));
				if (name != null && !party.contains(name))
				{
					party.add(name);
				}
			}
			if (!party.isEmpty())
			{
				partyNames = party;
			}
		}
		Integer slots = null;
		if (TOB.equals(family))
		{
			slots = count(context, TOB_SLOTS);
		}
		else if (TOA.equals(family))
		{
			slots = count(context, TOA_SLOTS);
			int level = context.varbit(VarbitID.TOA_CLIENT_RAID_LEVEL);
			if (level > 0)
			{
				raidLevel = level;
			}
		}
		else if (TFA.equals(family))
		{
			slots = TfaTeamSize.count(context::varbit);
		}
		else if (COX.equals(family))
		{
			int size = context.varbit(VarbitID.RAIDS_CLIENT_PARTYSIZE);
			slots = size > 0 ? size : null;
			int party = context.varbit(VarbitID.RAIDS_CLIENT_PARTYSCORE);
			int own = context.varp(VarPlayerID.RAIDS_PLAYERSCORE);
			if (party > 0)
			{
				totalPoints = party;
			}
			if (own > 0)
			{
				personalPoints = own;
			}
		}
		if (slots != null)
		{
			slotTeamSize = slots;
		}
	}

	private static Integer count(RaidContext context, int[] slots)
	{
		int size = 0;
		for (int slot : slots)
		{
			if (context.varbit(slot) != 0)
			{
				size++;
			}
		}
		return size > 0 ? size : null;
	}

	static String name(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String name = raw.replaceAll("<[^>]*>", "").replace('\u00A0', ' ').replace('_', ' ').trim();
		return NAME.matcher(name).matches() ? name : null;
	}

	static String familyOf(String raid)
	{
		String key = SnapshotDiff.key(raid);
		if (key.contains(SnapshotDiff.key("Chambers of Xeric")))
		{
			return COX;
		}
		if (key.contains(SnapshotDiff.key("Theatre of Blood")))
		{
			return TOB;
		}
		if (key.contains(SnapshotDiff.key("Tombs of Amascut")))
		{
			return TOA;
		}
		return TFA;
	}

	private Long time(String text)
	{
		return text == null ? null : durations.toMillis(text);
	}

	static String clean(String message)
	{
		return message.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "").replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}
}
