package com.arc1s.clansync;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Data;

/** One "Your X kill count is: N" message. The website dedupes these by {@code uid}. */
@Data
public class KillCountReport
{
	private String uid;
	private String player;
	private String source;
	private int killCount;
	private String recordedAt;

	static KillCountReport of(String player, String source, int killCount, Instant when)
	{
		KillCountReport report = new KillCountReport();
		report.player = player;
		report.source = source;
		report.killCount = killCount;
		report.recordedAt = when.toString();
		report.uid = uidFor(player, source, killCount);
		return report;
	}

	/** Same player, boss and count always produce the same id, so a repeated chat line is one report. */
	static String uidFor(String player, String source, int killCount)
	{
		String key = "kc|" + lower(player) + "|" + lower(source) + "|" + killCount;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}

	static String lower(String text)
	{
		return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
	}

	boolean olderThan(Instant now, long maxAgeMillis)
	{
		if (recordedAt == null || recordedAt.isEmpty())
		{
			return false;
		}
		try
		{
			return now.toEpochMilli() - Instant.parse(recordedAt).toEpochMilli() > maxAgeMillis;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}
}
