package com.arc1s.clansync;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * The team size as the game prints it. Chambers of Xeric prints exact sizes only up to 10; bigger teams
 * appear as buckets ("11-15 players", "16-23 players", "24+ players"), so a size is a range:
 * min == max for exact sizes, max == null for open-ended buckets.
 */
@Value
public class TeamSize
{
	private static final Pattern TEAM_SIZE = Pattern.compile(
		"Team size\\s*:?\\s*(?:(?<solo>Solo)|(?<min>\\d{1,3})(?:\\s*(?<plus>\\+)|\\s*-\\s*(?<max>\\d{1,3}))?(?:\\s*players?)?)",
		Pattern.CASE_INSENSITIVE);

	int min;
	Integer max;
	String label;

	static TeamSize exact(int size)
	{
		return new TeamSize(size, size, size == 1 ? "Solo" : size + " players");
	}

	/**
	 * The team size in a chat message, or null when it has none.
	 */
	static TeamSize parse(String text)
	{
		Matcher matcher = TEAM_SIZE.matcher(text);
		if (!matcher.find())
		{
			return null;
		}
		if (matcher.group("solo") != null)
		{
			return new TeamSize(1, 1, "Solo");
		}
		int min = Integer.parseInt(matcher.group("min"));
		if (matcher.group("plus") != null)
		{
			return new TeamSize(min, null, min + "+ players");
		}
		if (matcher.group("max") != null)
		{
			int max = Integer.parseInt(matcher.group("max"));
			return max >= min ? new TeamSize(min, max, min + "-" + max + " players") : null;
		}
		return exact(min);
	}
}
