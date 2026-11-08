package com.arc1s.clansync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ClanSyncLogicTest
{
	private final Gson gson = new Gson();

	private static ClanSnapshot.Member member(String name, int rank, String title)
	{
		ClanSnapshot.Member member = new ClanSnapshot.Member();
		member.setName(name);
		member.setRank(rank);
		member.setTitle(title);
		return member;
	}

	private static ClanSnapshot snapshot(String collectedAt, ClanSnapshot.Member... members)
	{
		ClanSnapshot snapshot = new ClanSnapshot();
		snapshot.setClanName("Example Clan");
		snapshot.setCollectedAt(collectedAt);
		snapshot.getMembers().addAll(Arrays.asList(members));
		snapshot.setMemberCount(members.length);
		return snapshot;
	}

	@Test
	public void firstSyncHasNoChanges()
	{
		assertNull(SnapshotDiff.compute(null, snapshot("t1", member("Jonas", 10, "Recruit"))));
	}

	@Test
	public void detectsJoinedLeftAndRankChanges()
	{
		ClanSnapshot before = snapshot("t1",
			member("Jonas", 10, "Recruit"), member("Petras", 50, "Sergeant"), member("Old Guy", 10, "Recruit"));
		// Same player, different spacing/case: must not count as left + joined.
		ClanSnapshot after = snapshot("t2",
			member("jonas", 10, "Recruit"), member("Petras", 100, "Captain"), member("New Guy", 10, "Recruit"));

		ClanSnapshot.Changes changes = SnapshotDiff.compute(before, after);

		assertEquals("t1", changes.getPreviousCollectedAt());
		assertEquals(Arrays.asList("New Guy"), changes.getJoined());
		assertEquals(Arrays.asList("Old Guy"), changes.getLeft());
		assertEquals(1, changes.getRankChanges().size());
		ClanSnapshot.RankChange change = changes.getRankChanges().get(0);
		assertEquals("Petras", change.getName());
		assertEquals(50, change.getFromRank());
		assertEquals("Captain", change.getToTitle());
		assertFalse(changes.isEmpty());
		assertTrue(ClanSyncPlugin.describeChanges(changes).contains("Petras: Sergeant -> Captain"));
		assertEquals("Roster changes: +1 -1, 1 rank", ClanSyncPlugin.changesTitle(changes));
	}

	@Test
	public void pendingSnapshotIsNotTheBaseline() throws IOException
	{
		MemoryStore files = new MemoryStore();
		SnapshotStore store = new SnapshotStore(files, gson);
		assertNull(store.loadLatest());

		store.saveLatest(snapshot("t1", member("Jonas", 10, "Recruit")));
		store.savePending(snapshot("t2", member("Petras", 10, "Recruit")));
		assertEquals("t1", store.loadLatest().getCollectedAt());

		store.saveLatest(snapshot("t3", member("Petras", 10, "Recruit")));
		assertEquals("t3", store.loadLatest().getCollectedAt());
		assertFalse(files.files.containsKey(SnapshotStore.PENDING));
	}

	@Test
	public void rosterSyncKeepsTheLastCollectionLogLayout()
	{
		ClanSnapshot previous = snapshot("t1", member("Jonas", 10, "Recruit"));
		java.util.Map<Integer, String> names = new java.util.TreeMap<>();
		names.put(6570, "Fire cape");
		previous.setItemNames(names);

		ClanSnapshot roster = snapshot("t2", member("jonas", 20, "Corporal"));
		roster.setSyncType(ClanSnapshot.SYNC_ROSTER);
		ClanSnapshot stored = ClanSyncPlugin.mergeForStorage(previous, roster);
		assertEquals("t2", stored.getCollectedAt());
		assertEquals(20, stored.getMembers().get(0).getRank());
		assertEquals(names, stored.getItemNames());
		assertEquals(roster, ClanSyncPlugin.mergeForStorage(null, roster));
	}

	@Test
	public void snapshotCarriesNoHiscoresOrOtherPlayersData()
	{
		ClanSnapshot snapshot = snapshot("t1", member("Jonas", 10, "Recruit"));
		snapshot.setSyncType(ClanSnapshot.SYNC_FULL);
		JsonObject json = gson.fromJson(gson.toJson(snapshot), JsonObject.class);
		assertFalse(json.get("hiscoresIncluded").getAsBoolean());
		JsonObject jonas = json.getAsJsonArray("members").get(0).getAsJsonObject();
		assertFalse(jonas.has("hiscore"));
		assertFalse(jonas.has("collectionLog"));
	}

	@Test
	public void onlyOwnerAndDeputyOwnerMayUploadTheRoster()
	{
		assertTrue(ClanSyncPlugin.isRosterRank(126));
		assertTrue(ClanSyncPlugin.isRosterRank(125));
		assertFalse("Jagex moderator", ClanSyncPlugin.isRosterRank(127));
		assertFalse(ClanSyncPlugin.isRosterRank(124));
		assertFalse("Administrator", ClanSyncPlugin.isRosterRank(100));
		assertFalse(ClanSyncPlugin.isRosterRank(-1));
	}

	@Test
	public void snapshotSaysHowItWasStarted()
	{
		ClanSnapshot snapshot = new ClanSnapshot();
		assertEquals("manual", gson.toJsonTree(snapshot).getAsJsonObject().get("trigger").getAsString());
		snapshot.setTrigger(ClanSnapshot.TRIGGER_AUTO);
		assertEquals("auto", gson.toJsonTree(snapshot).getAsJsonObject().get("trigger").getAsString());
		assertEquals(24 * 3600, ClanSyncPlugin.AUTO_INTERVAL_SECONDS);
	}
}
