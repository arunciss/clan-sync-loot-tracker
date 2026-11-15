package com.arc1s.clansync;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClanTasksTest
{
	private static final String STATE = "{\"status\":200,\"message\":\"OK\",\"data\":{\"feature\":\"clan_tasks\",\"version\":1,"
		+ "\"available\":true,\"joined\":true,\"rerolls\":5,\"points\":40,\"completed\":3,\"message\":\"\",\"task\":{"
		+ "\"assignment_id\":189,\"task_id\":\"sire-kc\",\"title\":\"Kill Abyssal Sire 25 times\",\"type\":\"kc\",\"target\":\"Abyssal Sire\","
		+ "\"boss\":\"Abyssal Sire\",\"count\":25,\"difficulty\":\"mid\",\"wilderness\":false,\"points\":20,\"requirement\":null,"
		+ "\"slayer_task_required\":false,\"progress\":{\"current\":5,\"target\":25,\"source\":\"plugin\"},\"reached\":false,"
		+ "\"assigned_at\":\"2026-11-01 10:00:00\"}}}";

	private final List<Request> requests = new ArrayList<>();

	private ClanTasksClient client(int code, String body)
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			requests.add(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("X")
				.body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
		}).build();
		return new ClanTasksClient(http, new Gson());
	}

	@Test
	public void urlsSitUnderTheApiAddress()
	{
		assertEquals("https://example.com/api/plugin/pvm/current", ClanTasksClient.url("https://example.com/api/plugin", "current").toString());
		assertEquals("https://example.com/api/plugin/pvm/current", ClanTasksClient.url("https://example.com/api/plugin/", "current").toString());
		assertEquals("https://example.com/api/plugin/pvm/done", ClanTasksClient.url("https://example.com/api/plugin/loot", "done").toString());
		assertEquals("https://example.com/api/plugin/pvm/skip-greenlog",
			ClanTasksClient.url("https://example.com/api/plugin/clan-sync?x=1", "skip-greenlog").toString());
		assertNull(ClanTasksClient.url("not a url", "current"));
	}

	@Test
	public void readsTheCurrentTask() throws IOException
	{
		ClanTasksClient.Result result = client(200, STATE).current("https://example.com/api/plugin", " code ");
		assertTrue(result.ok());
		assertTrue(result.supported);
		Request request = requests.get(0);
		assertEquals("GET", request.method());
		assertEquals("code", request.header(SiteUploader.MEMBER_HEADER));
		assertNull(request.header("Auth-Osrs"));
		ClanTask task = result.state.getTask();
		assertEquals(189, task.getAssignmentId());
		assertEquals("Kill Abyssal Sire 25 times", task.displayTitle());
		assertEquals("5/25 kills", task.progressText());
		assertEquals(ClanTask.MID_COLOR, ClanTask.color(task.getDifficulty()));
		assertEquals(5, result.state.getRerolls());
	}

	@Test
	public void sitesWithoutClanTasksAreUnsupported() throws IOException
	{
		assertFalse(client(404, "<html>404 Not found</html>").current("https://example.com/api/plugin", "code").supported);
		assertFalse(client(200, "{\"status\":200,\"message\":\"OK\",\"data\":{}}").current("https://example.com/api/plugin", "code").supported);
		assertTrue(client(503, "<html>busy</html>").current("https://example.com/api/plugin", "code").supported);
	}

	@Test
	public void refusedCodesAreSeenEvenWithHttp200() throws IOException
	{
		ClanTasksClient.Result result = client(200, "{\"status\":403,\"message\":\"Nope\"}").current("https://example.com/api/plugin", "code");
		assertTrue(result.codeRefused());
		assertTrue(client(401, "{\"status\":401,\"message\":\"Bad code\"}").current("https://example.com/api/plugin", "code").codeRefused());
		ClanTasksClient.Result disabled = client(403, "{\"status\":403,\"message\":\"Off\",\"data\":{\"reason\":\"disabled\"}}")
			.act("https://example.com/api/plugin", "code", ClanTasksClient.ACTION_DONE, 1, ClanSyncPlugin.newRequestId());
		assertFalse(disabled.codeRefused());
	}

	@Test
	public void doneSendsAssignmentAndRequestId() throws IOException
	{
		String body = "{\"status\":200,\"message\":\"Task completed.\",\"data\":{\"reason\":\"done\",\"state\":"
			+ STATE.substring(STATE.indexOf("\"data\":") + 7, STATE.length() - 1)
			+ ",\"completed\":{\"assignment_id\":188,\"title\":\"Kill Vorkath 10 times\",\"difficulty\":\"hard\",\"wilderness\":true,\"points\":30}}}";
		String id = ClanSyncPlugin.newRequestId();
		assertTrue(id.matches("[0-9a-f]{32}"));
		ClanTasksClient.Result result = client(200, body).act("https://example.com/api/plugin", "code", ClanTasksClient.ACTION_DONE, 188, id);
		assertTrue(result.ok());
		assertEquals("done", result.reason);
		assertNotNull(result.state);
		assertEquals(188, result.completed.getAssignmentId());
		assertTrue(result.completed.isWilderness());
		Request request = requests.get(0);
		assertEquals("POST", request.method());
		assertEquals("https://example.com/api/plugin/pvm/done", request.url().toString());
		Buffer buffer = new Buffer();
		try
		{
			request.body().writeTo(buffer);
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
		assertEquals("{\"assignmentId\":188,\"requestId\":\"" + id + "\"}", buffer.readUtf8());
	}

	@Test
	public void takeSendsNoAssignment() throws IOException
	{
		client(200, "{\"status\":409,\"message\":\"You already have a task.\",\"data\":{\"reason\":\"has_task\"}}")
			.act("https://example.com/api/plugin", "code", ClanTasksClient.ACTION_TAKE, null, "0123456789abcdef0123456789abcdef");
		Buffer buffer = new Buffer();
		requests.get(0).body().writeTo(buffer);
		assertEquals("{\"requestId\":\"0123456789abcdef0123456789abcdef\"}", buffer.readUtf8());
	}

	@Test
	public void popupsShowOncePerTask()
	{
		String stored = PopupMemory.add(null, 189);
		assertTrue(PopupMemory.contains(stored, 189));
		assertFalse(PopupMemory.contains(stored, 18));
		for (int i = 0; i < 30; i++)
		{
			stored = PopupMemory.add(stored, 1000 + i);
		}
		assertFalse(PopupMemory.contains(stored, 189));
		assertTrue(PopupMemory.contains(stored, 1029));
		assertEquals(PopupMemory.MAX_IDS, stored.split(",").length);
		assertFalse(PopupMemory.contains("junk,,x", 1));
	}

	@Test
	public void difficultyColours()
	{
		assertEquals(0x4CAF50, ClanTask.color("easy").getRGB() & 0xFFFFFF);
		assertEquals(0xFFB300, ClanTask.color("mid").getRGB() & 0xFFFFFF);
		assertEquals(0xE53935, ClanTask.color("hard").getRGB() & 0xFFFFFF);
		assertEquals("Hard", ClanTask.difficultyName("hard"));
	}

	@Test
	public void popupsQueueAndExpire()
	{
		TaskCompleteOverlay overlay = new TaskCompleteOverlay();
		ClanTask task = new ClanTask();
		task.setAssignmentId(1);
		task.setTitle("Kill Callisto 5 times");
		task.setDifficulty("hard");
		task.setWilderness(true);
		TaskCompleteOverlay.Popup first = TaskCompleteOverlay.popupFor(task, "Clan task complete!");
		assertTrue(first.wilderness);
		assertEquals(ClanTask.HARD_COLOR, first.color);
		overlay.show(first);
		overlay.show(TaskCompleteOverlay.popupFor(task, "Second"));
		assertEquals(first, overlay.current(1000));
		assertEquals("Second", overlay.current(1000 + TaskCompleteOverlay.SHOW_MILLIS).heading);
		assertNull(overlay.current(1000 + 3 * TaskCompleteOverlay.SHOW_MILLIS));
	}

	@Test
	public void rerollPostsToItsOwnRoute() throws IOException
	{
		String body = "{\"status\":200,\"message\":\"Task re-rolled (-1 re-roll, 4 left). New task assigned.\",\"data\":{\"state\":"
			+ "{\"feature\":\"clan_tasks\",\"available\":true,\"joined\":true,\"rerolls\":4,\"next_reroll_in\":2,\"task\":null}}}";
		ClanTasksClient.Result result = client(200, body)
			.act("https://example.com/api/plugin", "code", ClanTasksClient.ACTION_REROLL, 189, "0123456789abcdef0123456789abcdef");
		assertTrue(result.ok());
		assertEquals("https://example.com/api/plugin/pvm/reroll", requests.get(0).url().toString());
		Buffer buffer = new Buffer();
		requests.get(0).body().writeTo(buffer);
		assertEquals("{\"assignmentId\":189,\"requestId\":\"0123456789abcdef0123456789abcdef\"}", buffer.readUtf8());
		assertEquals(4, result.state.getRerolls());
		assertEquals(2, result.state.getNextRerollIn());
		assertEquals("Task re-rolled (-1 re-roll, 4 left). New task assigned.", result.message);
	}

	@Test
	public void noRerollsLeftIsARefusalNotABadCode() throws IOException
	{
		ClanTasksClient.Result result = client(409, "{\"status\":409,\"message\":\"You have no re-rolls left.\",\"data\":{\"reason\":\"no_rerolls\"}}")
			.act("https://example.com/api/plugin", "code", ClanTasksClient.ACTION_REROLL, 189, ClanSyncPlugin.newRequestId());
		assertFalse(result.ok());
		assertFalse(result.codeRefused());
		assertTrue(result.supported);
		assertEquals("no_rerolls", result.reason);
	}

	@Test
	public void sendsTaskPreferences() throws IOException
	{
		String body = STATE.replace("\"message\":\"\",", "\"message\":\"\",\"wilderness_enabled\":true,\"slayer_tasks_enabled\":false,");
		ClanTasksClient.Result result = client(200, body).prefs("https://example.com/api/plugin/loot", "code", true, false);
		assertTrue(result.ok());
		Request request = requests.get(0);
		assertEquals("https://example.com/api/plugin/pvm/prefs", request.url().toString());
		assertEquals("POST", request.method());
		Buffer buffer = new Buffer();
		request.body().writeTo(buffer);
		assertEquals("{\"wilderness\":true,\"slayerTasks\":false}", buffer.readUtf8());
		assertEquals(Boolean.TRUE, result.state.getWildernessEnabled());
		assertEquals(Boolean.FALSE, result.state.getSlayerTasksEnabled());
	}

	@Test
	public void olderWebsiteSendsNoPreferences() throws IOException
	{
		ClanTasksClient.Result result = client(200, STATE).current("https://example.com/api/plugin", "code");
		assertNull(result.state.getWildernessEnabled());
		assertNull(result.state.getSlayerTasksEnabled());
	}

	@Test
	public void overlayShowsProgress() throws IOException
	{
		ClanTask task = client(200, STATE).current("https://example.com/api/plugin", "code").state.getTask();
		assertEquals("Abyssal Sire", ClanTaskOverlay.shortTitle(task));
		assertEquals("Kill 25", ClanTaskOverlay.whatText(task));
		assertEquals("12/50 (24%)", ClanTaskOverlay.progressText(12, 50));
		assertEquals("25/25 (100%)", ClanTaskOverlay.progressText(25, 25));
		List<net.runelite.client.ui.overlay.components.LayoutableRenderableEntity> lines = new ArrayList<>();
		ClanTaskOverlay.fill(lines, task);
		assertEquals(3, lines.size());
		task.setReached(true);
		lines.clear();
		ClanTaskOverlay.fill(lines, task);
		assertEquals(4, lines.size());
		task.setType("drop");
		task.setTarget("Unsired");
		task.setProgress(null);
		assertEquals("Get: Unsired", ClanTaskOverlay.whatText(task));
	}

	@Test
	public void rerollTextsShowRerollsLeft()
	{
		ClanTask task = new ClanTask();
		task.setTitle("Kill Abyssal Sire 25 times");
		task.setBoss("Abyssal Sire");
		String text = ClanSyncPlugin.rerollConfirmText(task, 5);
		assertTrue(text.contains("Kill Abyssal Sire 25 times"));
		assertTrue(text.contains("uses 1 re-roll"));
		assertTrue(text.contains("Re-rolls left: 5 now, 4 after this"));
		assertFalse(text.contains("Green log"));
		assertFalse(ClanSyncPlugin.noRerollsText(2).contains("Green log"));
		assertTrue(ClanSyncPlugin.noRerollsText(2).contains("after 2 more completed tasks"));
		assertTrue(ClanSyncPlugin.noRerollsText(1).contains("after 1 more completed task."));
	}

	@Test
	public void rosterPartIsHiddenUntilLeaderRankIsKnown() throws Exception
	{
		ClanSyncPanel[] panel = new ClanSyncPanel[1];
		javax.swing.SwingUtilities.invokeAndWait(() -> panel[0] = new ClanSyncPanel(() -> { }, null));
		javax.swing.SwingUtilities.invokeAndWait(() -> { });
		assertFalse(panel[0].isRosterShown());
		panel[0].setRosterVisible(true);
		javax.swing.SwingUtilities.invokeAndWait(() -> { });
		assertTrue(panel[0].isRosterShown());
		panel[0].setRosterVisible(false);
		javax.swing.SwingUtilities.invokeAndWait(() -> { });
		assertFalse(panel[0].isRosterShown());
		assertTrue(ClanSyncPlugin.isRosterRank(126));
		assertTrue(ClanSyncPlugin.isRosterRank(125));
		assertFalse(ClanSyncPlugin.isRosterRank(100));
		assertFalse(ClanSyncPlugin.isRosterRank(127));
	}

	@Test
	public void skipPathsSitUnderApiPluginPvm()
	{
		assertEquals("https://example.com/api/plugin/pvm/skip-haveitem",
			ClanTasksClient.url("https://example.com/api/plugin", SkipOption.defaultAction(SkipOption.HAVEITEM)).toString());
		assertEquals("https://example.com/api/plugin/pvm/skip-noaccess",
			ClanTasksClient.url("https://example.com/api/plugin/loot", "skip-noaccess").toString());
		assertEquals("https://example.com/api/plugin/pvm/skip-greenlog",
			ClanTasksClient.url("https://example.com/api/plugin", "skip-greenlog").toString());
		assertEquals("https://example.com/api/plugin/pvm/reroll",
			ClanTasksClient.url("https://example.com/api/plugin", "reroll").toString());
	}

	@Test
	public void haveitemPostsTheActionSuffix() throws IOException
	{
		String body = "{\"status\":200,\"message\":\"Task skipped for free (you have this item). New task: Kill Scurrius 25 times.\","
			+ "\"data\":{\"reason\":\"skip-haveitem\",\"state\":{\"feature\":\"clan_tasks\",\"available\":true,\"joined\":true,\"rerolls\":2,\"task\":null}}}";
		ClanTasksClient.Result result = client(200, body)
			.act("https://example.com/api/plugin", "code", "skip-haveitem", 418, "0123456789abcdef0123456789abcdef");
		assertTrue(result.accepted());
		assertFalse(result.replayed());
		assertEquals("https://example.com/api/plugin/pvm/skip-haveitem", requests.get(0).url().toString());
		Buffer buffer = new Buffer();
		requests.get(0).body().writeTo(buffer);
		assertEquals("{\"assignmentId\":418,\"requestId\":\"0123456789abcdef0123456789abcdef\"}", buffer.readUtf8());
		assertEquals("Task skipped for free (you have this item). New task: Kill Scurrius 25 times.", result.message);
		assertEquals("Task skipped for free (you have this item). New task: Kill Scurrius 25 times.",
			ClanTasksSection.successLine(result.message));
	}

	@Test
	public void refusal409ShowsTheServerMessage() throws IOException
	{
		String message = "Only for tasks that need a quest. Nothing changed, no re-roll used.";
		String body = "{\"status\":409,\"message\":\"" + message + "\",\"data\":{\"reason\":\"noaccess_no_quest\",\"state\":"
			+ "{\"feature\":\"clan_tasks\",\"available\":true,\"joined\":true,\"rerolls\":2,\"task\":null}}}";
		ClanTasksClient.Result result = client(409, body)
			.act("https://example.com/api/plugin", "code", "skip-noaccess", 420, ClanSyncPlugin.newRequestId());
		assertFalse(result.ok());
		assertFalse(result.accepted());
		assertFalse(result.codeRefused());
		assertEquals("noaccess_no_quest", result.reason);
		assertEquals(message, result.message);
		assertEquals(message, ClanTasksSection.failureLine(result.message));
		assertNotNull(result.state);
	}

	@Test
	public void replayedIsSuccess() throws IOException
	{
		String body = "{\"status\":200,\"message\":\"Already done.\",\"data\":{\"reason\":\"replayed\",\"state\":"
			+ "{\"feature\":\"clan_tasks\",\"available\":true,\"joined\":true,\"rerolls\":2,\"task\":null}}}";
		ClanTasksClient.Result result = client(200, body)
			.act("https://example.com/api/plugin", "code", "skip-greenlog", 7, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
		assertTrue(result.ok());
		assertTrue(result.replayed());
		assertTrue(result.accepted());
		assertEquals("Already done.", result.message);
		assertEquals("Already done.", ClanTasksSection.successLine(result.message));
	}

	@Test
	public void skipOptionsUseTheServersAction() throws IOException
	{
		String json = STATE.replace("\"message\":\"\",", "\"message\":\"\",\"skip_options\":["
			+ "{\"id\":\"reroll\",\"label\":\"Re-roll\",\"action\":\"reroll\",\"available\":true,\"cost\":\"reroll\",\"hint\":\"New random task. 2 left, 1 after.\"},"
			+ "{\"id\":\"haveitem\",\"label\":\"I have this item\",\"action\":\"skip-haveitem\",\"available\":false,\"cost\":\"free\",\"hint\":\"ignored\",\"reason\":\"Only for item tasks.\"},"
			+ "{\"id\":\"greenlog\",\"label\":\"Green log\",\"action\":\"skip-greenlog\",\"available\":true,\"cost\":\"free\",\"hint\":\"You have the whole collection log.\"},"
			+ "{\"id\":\"noaccess\",\"label\":\"No access\",\"action\":\"skip-noaccess\",\"available\":false,\"cost\":\"free\",\"reason\":\"Only for tasks that need a quest.\"}"
			+ "],");
		ClanTasksClient.Result result = client(200, json).current("https://example.com/api/plugin", "code");
		List<SkipOption> options = SkipOptions.resolve(result.state);
		assertEquals(4, options.size());
		assertEquals("reroll", options.get(0).endpoint());
		assertEquals("skip-haveitem", options.get(1).endpoint());
		assertFalse(options.get(1).isAvailable());
		assertEquals("Only for item tasks.", options.get(1).getReason());
		assertEquals("skip-greenlog", options.get(2).endpoint());
		assertEquals("skip-noaccess", options.get(3).endpoint());
		assertEquals(32, ClanSyncPlugin.newRequestId().length());
	}
}
