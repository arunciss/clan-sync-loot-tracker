package com.arc1s.clansync;

import lombok.Data;

/**
 * A level-up to 92 or 99, or an XP milestone (every 5m XP from 15m to 200m), of the local player's own skill.
 * The website queues it for an admin to approve; nothing is credited automatically.
 */
@Data
public class SkillEvent
{
	public static final String LEVEL = "LEVEL";
	public static final String XP_MILESTONE = "XP_MILESTONE";

	private String uid;
	private String player;
	private String recordedAt;
	private String skill;
	// LEVEL or XP_MILESTONE.
	private String type;
	// The level reached (LEVEL), or the current level (XP_MILESTONE).
	private int level;
	// Total XP in the skill when the event happened.
	private int xp;
	// The milestone reached, e.g. 50000000 (XP_MILESTONE only).
	private Integer milestoneXp;
}
