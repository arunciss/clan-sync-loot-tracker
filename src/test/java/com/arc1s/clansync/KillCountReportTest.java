package com.arc1s.clansync;

import com.google.gson.Gson;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KillCountReportTest
{
	@Test
	public void sameKillCountHasOneUid()
	{
		assertEquals(KillCountReport.uidFor("Some Name", "TzTok-Jad", 12), KillCountReport.uidFor("some name", "tztok-jad", 12));
		LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
		Instant now = Instant.parse("2026-11-13T12:00:00Z");
		queue.add(KillCountReport.of("Some Name", "TzTok-Jad", 12, now));
		queue.add(KillCountReport.of("some name", "tztok-jad", 12, now));
		assertEquals(1, queue.size());
	}

	@Test
	public void killCountsRideAlongAndStayUntilTheSiteConfirms()
	{
		LootQueue queue = queueWithLootAndKillCount();
		LootBatch held = queue.next(200, Collections.emptySet(), false, false);
		assertEquals(1, held.getRecords().size());
		assertEquals(1, held.getKillCounts().size());

		new LootSender(queue, uploader("{\"status\":200,\"message\":\"ok\",\"data\":{}}"), config()).sendAll();
		assertEquals("the drop was accepted", 0, queue.next(20, Collections.emptySet(), false, false).getRecords().size());
		assertEquals("the kill count is still queued", 1, killCounts(queue));
	}

	@Test
	public void confirmedKillCountsAreRemovedAndCanThenBeSentAlone()
	{
		LootQueue queue = queueWithLootAndKillCount();
		LootSender sender = new LootSender(queue, uploader("{\"status\":200,\"message\":\"ok\",\"data\":{\"killCounts\":true}}"), config());
		sender.sendAll();
		assertEquals(0, killCounts(queue));

		queue.add(KillCountReport.of("Me", "Vorkath", 41, Instant.now()));
		sender.sendAll();
		assertEquals(0, killCounts(queue));
	}

	@Test
	public void killCountsOnlyBatchNeedsConfirmation()
	{
		LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
		queue.add(KillCountReport.of("Me", "Vorkath", 40, Instant.now()));
		assertTrue(queue.next(200, Collections.emptySet(), false, false).getKillCounts().isEmpty());
		assertEquals(1, queue.next(200, Collections.emptySet(), false, true).getKillCounts().size());
		assertEquals(1, queue.size());
	}

	@Test
	public void unconfirmedKillCountsOlderThanADayAreDropped()
	{
		LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
		queue.add(KillCountReport.of("Me", "Vorkath", 1, Instant.now().minus(Duration.ofHours(25))));
		queue.add(KillCountReport.of("Me", "Vorkath", 2, Instant.now()));
		LootBatch batch = queue.next(10, Collections.emptySet(), false, true);
		assertEquals(1, batch.getKillCounts().size());
		assertEquals(2, batch.getKillCounts().get(0).getKillCount());
		assertEquals(1, queue.size());
	}

	private static LootQueue queueWithLootAndKillCount()
	{
		LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
		queue.add(KillCountReport.of("Me", "Vorkath", 40, Instant.now()));
		LootRecord record = new LootRecord();
		record.setUid("loot-1");
		record.setPlayer("Me");
		queue.add(record, new HashMap<>(), new HashMap<>());
		return queue;
	}

	private static int killCounts(LootQueue queue)
	{
		return queue.next(50, Collections.emptySet(), false, true).getKillCounts().size();
	}

	private static ClanSyncConfig config()
	{
		return new ClanSyncConfig()
		{
			@Override
			public String endpoint()
			{
				return "https://example.com/api/plugin";
			}

			@Override
			public String memberToken()
			{
				return "code";
			}

			@Override
			public boolean trackLoot()
			{
				return true;
			}
		};
	}

	private static SiteUploader uploader(String body)
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
			new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
				.body(ResponseBody.create(MediaType.parse("application/json"), body)).build()).build();
		return new SiteUploader(http, new Gson());
	}
}
