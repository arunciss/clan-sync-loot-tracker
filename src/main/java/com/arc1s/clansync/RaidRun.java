package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

// One completed raid: who was in the party, the raid points (Chambers of Xeric only) and the room times.
// Every field except uid, player, recordedAt and raid is optional; the site shows only what was captured.
@Data
public class RaidRun
{
	public static final String TEAM_PARTY = "party";
	public static final String TEAM_SCENE = "scene";

	private String uid;
	private String player;
	private String recordedAt;
	// The raid and mode exactly as the kill count message names it, e.g. "Theatre of Blood: Hard Mode".
	private String raid;
	private Integer killCount;
	private Integer teamSize;
	private List<String> team = new ArrayList<>();
	// "party": names from the raid's own party list (Theatre of Blood, Tombs of Amascut, Fractured Archive, and the
	// Chambers of Xeric raids side panel). "scene": Chambers of Xeric only, the players inside the raid instance when
	// the party list could not be read. Players seen anywhere else are never sent.
	private String teamSource;
	// Tombs of Amascut raid level (invocation level), when known.
	private Integer raidLevel;
	// True when the total time has hundredths of a second (game setting "Precise timing").
	private boolean timePrecise;
	private Integer personalPoints;
	private Integer totalPoints;
	private Long totalTimeMs;
	private Long totalPersonalBestMs;
	private boolean totalNewPersonalBest;
	// Time spent in rooms only (Theatre of Blood and Tombs of Amascut "completion time").
	private Long challengeTimeMs;
	private Long challengePersonalBestMs;
	private List<RaidRoom> rooms = new ArrayList<>();
}
