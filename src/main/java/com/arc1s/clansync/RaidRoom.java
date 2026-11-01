package com.arc1s.clansync;

import lombok.Data;

// One room, wave, path or floor of a completed raid, as the game (or the CoX Timers plugin) printed it.
@Data
public class RaidRoom
{
	public static final String KIND_ROOM = "room";
	public static final String KIND_FLOOR = "floor";
	// Time left after the last Chambers of Xeric floor until the raid ended (Great Olm plus walking there).
	public static final String KIND_REST = "rest";

	private String name;
	private String kind = KIND_ROOM;
	private long timeMs;
	// Personal best the game printed with this room's message, null when it printed none.
	private Long gamePersonalBestMs;
	private boolean newPersonalBest;
}
