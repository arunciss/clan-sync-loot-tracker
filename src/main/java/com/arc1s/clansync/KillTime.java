package com.arc1s.clansync;

import lombok.Data;

@Data
public class KillTime
{
	private String uid;
	private String player;
	private String recordedAt;
	private String source;
	private Integer killCount;
	private long timeMs;
	private Long personalBestMs;
	private boolean newPersonalBest;
	// Exact size, or the lower bound of a range such as "11-15 players" (kept for older sites).
	private Integer teamSize;
	// Upper bound of the range; equal to teamSize when exact, null when unknown or open-ended ("24+").
	private Integer teamSizeMax;
	// The size as the game printed it, e.g. "Solo", "3 players", "11-15 players".
	private String teamSizeLabel;
	// True when the time has hundredths of a second (game setting "Precise timing"), false for whole seconds.
	private boolean timePrecise;
	// Tombs of Amascut raid level (invocation level), when known.
	private Integer raidLevel;
}
