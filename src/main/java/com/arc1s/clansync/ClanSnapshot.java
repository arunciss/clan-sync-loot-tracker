package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * One full reading of the clan roster as the game shows it to the clan Owner or a Deputy Owner.
 * This is also the JSON body sent to the website.
 */
@Data
public class ClanSnapshot
{
	public static final String SYNC_FULL = "full";
	public static final String SYNC_ROSTER = "roster";
	public static final String TRIGGER_MANUAL = "manual";
	public static final String TRIGGER_AUTO = "auto";

	private int schemaVersion = 1;
	private String syncType = SYNC_FULL;
	// "manual" (the panel button: always applied) or "auto" (daily: the website skips it if the roster was synced today).
	private String trigger = TRIGGER_MANUAL;
	private String clanName;
	private String collectedBy;
	private String collectedAt;
	private int memberCount;
	// Always false: the plugin no longer looks other players up on the hiscores (kept for older websites).
	private boolean hiscoresIncluded;
	private List<Member> members = new ArrayList<>();
	private Changes changes;
	// Collection log layout from the game cache (tabs, pages, item ids and names), sent with full syncs.
	private Map<Integer, String> itemNames;
	private CollectionLogLayout collectionLogLayout;

	@Data
	public static class Member
	{
		private String name;
		private int rank;
		private String title;
		private String joinDate;
	}

	/**
	 * Differences against the previous snapshot. Null on the very first sync.
	 */
	@Data
	public static class Changes
	{
		private String previousCollectedAt;
		private List<String> joined = new ArrayList<>();
		private List<String> left = new ArrayList<>();
		private List<RankChange> rankChanges = new ArrayList<>();

		public boolean isEmpty()
		{
			return joined.isEmpty() && left.isEmpty() && rankChanges.isEmpty();
		}
	}

	@Data
	public static class RankChange
	{
		private String name;
		private int fromRank;
		private String fromTitle;
		private int toRank;
		private String toTitle;
	}
}
