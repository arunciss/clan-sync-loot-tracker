package com.arc1s.clansync;

import lombok.Value;

@Value
public class DurationMessage
{
	long timeMs;
	Long personalBestMs;
	boolean newPersonalBest;
	int priority;
	int tick;
	TeamSize teamSize;
	// True when the game printed hundredths of a second ("Precise timing" setting on).
	boolean precise;
}
