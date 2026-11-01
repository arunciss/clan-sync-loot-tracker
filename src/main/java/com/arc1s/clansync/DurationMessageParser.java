package com.arc1s.clansync;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationMessageParser
{
	public static final int PRIORITY_ROOM = 1;
	public static final int PRIORITY_TOTAL = 2;

	private static final Pattern DURATION = Pattern.compile(
		"(?<label>Fight duration|Challenge duration|Lap duration|total completion time|completion time|Duration)\\s*:?\\s*(?<time>" + DurationParser.TIME + ")",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PERSONAL_BEST = Pattern.compile("Personal best\\s*:?\\s*(?<time>" + DurationParser.TIME + ")", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEW_PERSONAL_BEST = Pattern.compile("\\(new personal best\\)", Pattern.CASE_INSENSITIVE);

	private final DurationParser durations = new DurationParser();

	// No boss, raid or minigame is finished this fast: such a time comes from some other "Duration" message
	// (seen once as a 3 second King Black Dragon kill), so it is ignored.
	static final long MIN_TIME_MS = 5000;

	public DurationMessage parse(String message, int tick)
	{
		String text = message.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
		Matcher duration = DURATION.matcher(text);
		if (!duration.find())
		{
			return null;
		}
		Long time = durations.toMillis(duration.group("time"));
		if (time == null || time < MIN_TIME_MS)
		{
			return null;
		}
		boolean newBest = NEW_PERSONAL_BEST.matcher(text).find();
		Matcher best = PERSONAL_BEST.matcher(text);
		Long personalBest = best.find() ? durations.toMillis(best.group("time")) : null;
		if (newBest)
		{
			personalBest = time;
		}
		String label = duration.group("label").toLowerCase();
		boolean total = label.contains("completion") || text.toLowerCase().contains("raid is complete");
		return new DurationMessage(time, personalBest, newBest, total ? PRIORITY_TOTAL : PRIORITY_ROOM, tick, TeamSize.parse(text),
			DurationParser.isPrecise(duration.group("time")));
	}
}
