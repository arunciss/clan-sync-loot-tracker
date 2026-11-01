package com.arc1s.clansync;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationParser
{
	public static final String TIME = "\\d{1,3}(?::\\d{2}){1,2}(?:\\.\\d{1,2})?";
	private static final Pattern PARTS = Pattern.compile("^(?:(\\d+):)?(\\d+):(\\d{2})(?:\\.(\\d{1,2}))?$");

	// "1:23.40" (game setting "Precise timing") versus "1:23".
	public static boolean isPrecise(String text)
	{
		return text != null && text.indexOf('.') >= 0;
	}

	public Long toMillis(String text)
	{
		Matcher matcher = PARTS.matcher(text.trim());
		if (!matcher.matches())
		{
			return null;
		}
		long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
		long minutes = Long.parseLong(matcher.group(2));
		long seconds = Long.parseLong(matcher.group(3));
		String fraction = matcher.group(4);
		long millis = fraction == null ? 0 : Long.parseLong(fraction) * (fraction.length() == 1 ? 100 : 10);
		if (seconds > 59 || (matcher.group(1) != null && minutes > 59))
		{
			return null;
		}
		return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis;
	}
}
