package com.arc1s.clansync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ClanSyncConfig.GROUP)
public interface ClanSyncConfig extends Config
{
	String GROUP = "clansyncloottracker";
	String LAST_SYNC_KEY = "lastSyncEpochSeconds";
	String TASK_POPUPS_KEY = "taskPopupsShown";
	String WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

	@ConfigSection(
		name = "Website",
		description = "Where your data goes. Nothing is recorded or sent until you enter the address and your personal code and turn an upload on.",
		position = 0
	)
	String websiteSection = "website";

	@ConfigSection(
		name = "Clan roster (Owner and Deputy Owners)",
		description = "Roster uploads for the clan Owner and Deputy Owners only.",
		position = 10
	)
	String rosterSection = "roster";

	@ConfigSection(
		name = "Clan tasks",
		description = "Optional clan tasks (boss tasks), if your clan website offers them.",
		position = 20
	)
	String tasksSection = "tasks";

	@ConfigItem(
		keyName = "endpoint",
		name = "Website API URL",
		description = "Your clan website's plugin API address (https only), e.g. https://example.com/api/plugin."
			+ " Ask your clan for it. Nothing is sent while this is empty.",
		section = websiteSection,
		position = 1
	)
	default String endpoint()
	{
		return "";
	}

	@ConfigItem(
		keyName = "memberToken",
		name = "Personal code",
		description = "Your personal code from your clan website. It is sent with every upload so the website knows it is you."
			+ " Nothing is sent while this is empty.",
		secret = true,
		section = websiteSection,
		position = 2
	)
	default String memberToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "clanName",
		name = "Clan name",
		description = "Only record while you are in this clan. Leave empty to record in any clan or none.",
		section = websiteSection,
		position = 3
	)
	default String clanName()
	{
		return "";
	}

	@ConfigItem(
		keyName = "uploadLoot",
		name = "Upload drops and kill times",
		description = "Send your own drops (items, quantities, prices), kill counts, kill and raid times, personal bests,"
			+ " raid teammates' names and your own level 92/99 and XP milestone (15m, 20m ... 200m) level-ups to the website above.",
		warning = WARNING,
		section = websiteSection,
		position = 4
	)
	default boolean trackLoot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "uploadRoster",
		name = "Upload clan roster",
		description = "Owner and Deputy Owner only: send the clan member list (names, ranks, join dates)"
			+ " automatically once a day, and whenever you press \"Sync clan members\" in the side panel."
			+ " Other ranks never upload the roster and do not see the roster part of the panel.",
		warning = WARNING,
		section = rosterSection,
		position = 11
	)
	default boolean syncRoster()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clanTasks",
		name = "Show clan tasks",
		description = "If your clan website offers clan tasks, show your current task in the side panel with Done,"
			+ " Skip (re-roll or a free skip) and Take a new task. Checks the website about every 90 seconds while you are logged in.",
		warning = WARNING,
		section = tasksSection,
		position = 21
	)
	default boolean clanTasks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "taskPopup",
		name = "Task complete popup",
		description = "Show a popup when the website says you reached your task target, and when you press Done.",
		section = tasksSection,
		position = 22
	)
	default boolean taskPopup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "taskOverlay",
		name = "Show task overlay",
		description = "Show a small box on the game screen with your current clan task and its progress."
			+ " Drag it with Alt held to move it.",
		section = tasksSection,
		position = 23
	)
	default boolean taskOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wildernessTasks",
		name = "Wilderness tasks",
		description = "Allow clan tasks in the Wilderness. Saved on the clan website (the same setting as the Discord"
			+ " /pvm_wildy command) and used from your next task; your current task is not changed.",
		section = tasksSection,
		position = 24
	)
	default boolean wildernessTasks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "slayerBossTasks",
		name = "Slayer boss tasks",
		description = "Allow boss tasks that need a Slayer task (for example Abyssal Sire, Cerberus, Kraken)."
			+ " Saved on the clan website and used from your next task; your current task is not changed.",
		section = tasksSection,
		position = 25
	)
	default boolean slayerBossTasks()
	{
		return true;
	}
}
