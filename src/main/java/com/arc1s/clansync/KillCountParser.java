package com.arc1s.clansync;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KillCountParser
{
	private static final List<Pattern> PATTERNS = Arrays.asList(
		Pattern.compile("^Your (?:completed |subdued )?(?<name>.+?) (?:kill |harvest |lap |success |completion |)count is: ?(?<count>[\\d,]+)"),
		Pattern.compile("^Your completion count for (?<name>.+?) is: ?(?<count>[\\d,]+)"),
		Pattern.compile("^Your completion count for (?<name>.+?): ?(?<count>[\\d,]+)")
	);

	public KillCount parse(String message, int tick)
	{
		String text = message.replaceAll("<[^>]*>", "").trim();
		for (Pattern pattern : PATTERNS)
		{
			Matcher matcher = pattern.matcher(text);
			if (matcher.find())
			{
				try
				{
					return new KillCount(matcher.group("name").trim(), Integer.parseInt(matcher.group("count").replace(",", "")), tick);
				}
				catch (NumberFormatException e)
				{
					return null;
				}
			}
		}
		return null;
	}
}
