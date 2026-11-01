package com.arc1s.clansync;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SiteUploaderTest
{
	private final List<Request> requests = new ArrayList<>();

	private SiteUploader uploader(String body)
	{
		return uploader(200, body);
	}

	private SiteUploader uploader(int code, String body)
	{
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			requests.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("X")
				.body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
		}).build();
		return new SiteUploader(client, new Gson());
	}

	@Test
	public void sendsOnlyThePersonalCode() throws IOException
	{
		uploader("{\"status\":200}").post("https://example.com/api/plugin/loot", " code ", new HashMap<String, Object>());
		Request request = requests.get(0);
		assertEquals("code", request.header(SiteUploader.MEMBER_HEADER));
		assertNull(request.header("Auth-Osrs"));
		assertTrue(request.header("User-Agent").startsWith("RuneLite-"));
	}

	@Test
	public void refusesWithoutPersonalCode()
	{
		for (String code : new String[]{null, "  "})
		{
			try
			{
				uploader("{\"status\":200}").post("https://example.com/api/plugin/loot", code, new HashMap<String, Object>());
				fail("expected IOException");
			}
			catch (IOException e)
			{
				assertTrue(e.getMessage().contains("personal code"));
			}
		}
		assertTrue(requests.isEmpty());
	}

	@Test
	public void refusesPlainHttpForRemoteHosts()
	{
		try
		{
			uploader("{\"status\":200}").post("http://example.com/api/plugin/loot", "code", new HashMap<String, Object>());
			fail("expected IOException");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("https"));
		}
		assertTrue(requests.isEmpty());
	}

	@Test
	public void jsonErrorStatusStillFails()
	{
		try
		{
			uploader("{\"status\":403,\"message\":\"Invalid code\"}").post("https://example.com/api/plugin/loot", "bad", new HashMap<String, Object>());
			fail("expected IOException");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("Invalid code"));
		}
	}

	@Test
	public void nonJsonReplyFails()
	{
		try
		{
			uploader("<html>maintenance</html>").post("https://example.com/api/plugin/loot", "code", new HashMap<String, Object>());
			fail("expected IOException");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("JSON"));
		}
	}

	@Test
	public void resolvesEndpointsFromAnyUserUrl()
	{
		assertEquals("https://example.com/api/plugin/loot", SiteUploader.resolve("https://example.com/api/plugin", "loot"));
		assertEquals("https://example.com/api/plugin/loot", SiteUploader.resolve(" https://example.com/api/plugin/ ", "loot"));
		assertEquals("https://example.com/api/plugin/loot", SiteUploader.resolve("https://example.com/api/plugin/clan-sync", "loot"));
		assertEquals("https://example.com/api/plugin/clan-sync", SiteUploader.resolve("https://example.com/api/plugin/loot", "clan-sync"));
		assertEquals("https://example.com/api/plugin/clan-sync", SiteUploader.resolve("https://example.com/api/plugin/clan-sync", "clan-sync"));
		assertNull(SiteUploader.resolve("not a url", "loot"));
		assertNull(SiteUploader.resolve("", "loot"));
	}

	@Test
	public void siteRefusalIsReportedAsRejected() throws IOException
	{
		for (SiteUploader site : new SiteUploader[]{
			uploader(200, "{\"status\":403,\"message\":\"bad\"}"),
			uploader(403, "{\"status\":403,\"message\":\"bad\"}"),
			uploader(401, "{\"status\":401,\"message\":\"bad\"}")})
		{
			try
			{
				site.post("https://example.com/api/plugin/loot", "code", new HashMap<String, Object>());
				fail("expected RejectedException");
			}
			catch (SiteUploader.RejectedException e)
			{
				assertTrue(e.getMessage().contains("bad"));
			}
		}
	}

	@Test
	public void firewallPageIsNotARefusal()
	{
		try
		{
			uploader(403, "<html>Access denied</html>").post("https://example.com/api/plugin/loot", "code", new HashMap<String, Object>());
			fail("expected IOException");
		}
		catch (SiteUploader.RejectedException e)
		{
			fail("a CDN page must not pause uploads");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("403"));
		}
	}

	@Test
	public void rateLimitIsNotARefusal()
	{
		try
		{
			uploader("{\"status\":429,\"message\":\"slow down\"}").post("https://example.com/api/plugin/loot", "code", new HashMap<String, Object>());
			fail("expected IOException");
		}
		catch (SiteUploader.RejectedException e)
		{
			fail("429 must only delay");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("slow down"));
		}
	}

	@Test
	public void serverTextIsShownAsAscii()
	{
		String lt = "Asmeninis plugino kodas at\u0161auktas \u2013 narys neb\u0117ra klane \u201Eok\u201C \u4E2D";
		String out = SiteUploader.ascii(lt);
		assertEquals("Asmeninis plugino kodas atsauktas - narys nebera klane \"ok\" ?", out);
		for (char c : out.toCharArray())
		{
			assertTrue(c >= 0x20 && c < 0x7F);
		}
	}

	@Test
	public void lootStopsAfterRefusalUntilTheCodeChanges() throws IOException
	{
		String[] code = {"code_old"};
		ClanSyncConfig config = new ClanSyncConfig()
		{
			@Override
			public String endpoint()
			{
				return "https://example.com/api/plugin";
			}

			@Override
			public String memberToken()
			{
				return code[0];
			}

			@Override
			public boolean trackLoot()
			{
				return true;
			}
		};
		LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
		LootRecord record = new LootRecord();
		record.setUid("u1");
		record.setPlayer("Someone");
		queue.add(record, new HashMap<>(), new HashMap<>());
		LootSender sender = new LootSender(queue, uploader("{\"status\":403,\"message\":\"revoked\"}"), config);

		sender.sendAll();
		assertTrue(sender.isRejected());
		assertTrue(sender.describe().startsWith("Paused"));
		assertEquals(1, queue.size());
		sender.sendAll();
		assertEquals("no retry with a refused code", 1, requests.size());

		code[0] = "code_new";
		assertTrue(!sender.isRejected());
		sender.sendAll();
		assertEquals(2, requests.size());
	}
	private static ClanSyncConfig config(String endpoint, String token)
	{
		return new ClanSyncConfig()
		{
			@Override
			public String endpoint()
			{
				return endpoint;
			}

			@Override
			public String memberToken()
			{
				return token;
			}

			@Override
			public boolean trackLoot()
			{
				return true;
			}
		};
	}

	private static LootRecord record(String uid, String player)
	{
		LootRecord record = new LootRecord();
		record.setUid(uid);
		record.setPlayer(player);
		return record;
	}

	@Test
	public void sendsNothingWithoutUrlOrCode()
	{
		for (String[] pair : new String[][]{{"", "code"}, {"https://example.com/api/plugin", ""}, {" ", " "}})
		{
			LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
			queue.add(record("u1", "Me"), new HashMap<>(), new HashMap<>());
			ClanSyncConfig config = config(pair[0], pair[1]);
			assertTrue(!LootSender.isConfigured(config));
			LootSender sender = new LootSender(queue, uploader("{\"status\":200}"), config);
			sender.sendAll();
			assertEquals(LootSender.NOT_CONFIGURED, sender.describe());
		}
		assertTrue(requests.isEmpty());
	}

	@Test
	public void nameMismatchStopsOnlyThatCharacter() throws IOException
	{
		LootQueue queue = new LootQueue(new MemoryStore(), new Gson());
		queue.add(record("a1", "Alt Name"), new HashMap<>(), new HashMap<>());
		queue.add(record("m1", "Main"), new HashMap<>(), new HashMap<>());
		queue.add(record("a2", "alt_name"), new HashMap<>(), new HashMap<>());
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			requests.add(chain.request());
			okio.Buffer buffer = new okio.Buffer();
			chain.request().body().writeTo(buffer);
			boolean alt = buffer.readUtf8().contains("Alt Name");
			String body = alt ? "{\"status\":403,\"message\":\"This personal code belongs to another character\",\"data\":{\"reason\":\"rsn_mismatch\"}}"
				: "{\"status\":200,\"message\":\"ok\",\"data\":[]}";
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(alt ? 403 : 200).message("X")
				.body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
		}).build();
		LootSender sender = new LootSender(queue, new SiteUploader(client, new Gson()), config("https://example.com/api/plugin", "code"));

		sender.sendAll();
		assertEquals("one batch per character", 2, requests.size());
		assertTrue(!sender.isRejected());
		assertTrue(sender.isRejected("Alt\u00A0Name"));
		assertTrue(!sender.isRejected("Main"));
		assertEquals("the other character's data stays queued", 2, queue.size());
		assertTrue(sender.describe().startsWith("Paused for alt name"));

		sender.sendAll();
		assertEquals("no retry for the refused character", 2, requests.size());
	}

	@Test
	public void skippedReplyIsSuccess() throws IOException
	{
		SiteUploader.Reply reply = uploader("{\"status\":200,\"message\":\"Already synced today; skipped.\",\"data\":{\"skipped\":true}}")
			.post("https://example.com/api/plugin/clan-sync", "code", new HashMap<String, Object>());
		assertTrue(reply.skipped);
		assertEquals("Already synced today; skipped.", reply.message);
		assertTrue(!uploader("{\"status\":200,\"data\":[]}").post("https://example.com/api/plugin/clan-sync", "code", new HashMap<String, Object>()).skipped);
	}

	@Test
	public void refusalCarriesTheReason()
	{
		try
		{
			uploader(401, "{\"status\":401,\"message\":\"Personal code is invalid\",\"data\":{\"reason\":\"invalid\"}}")
				.post("https://example.com/api/plugin/loot", "code", new HashMap<String, Object>());
			fail("expected RejectedException");
		}
		catch (SiteUploader.RejectedException e)
		{
			assertEquals("invalid", e.getReason());
			assertTrue(!e.isNameMismatch());
		}
		catch (IOException e)
		{
			fail("expected RejectedException, got " + e);
		}
	}
}
