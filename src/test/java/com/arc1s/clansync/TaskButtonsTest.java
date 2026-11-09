package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskButtonsTest
{
	@Test
	public void localRulesWhenTheWebsiteSendsNoSkipOptions()
	{
		ClanTaskState item = state("item", null, 3);
		List<SkipOption> itemOptions = SkipOptions.resolve(item);
		assertEquals(Arrays.asList("reroll", "haveitem", "noaccess"), ids(itemOptions));
		assertTrue(byId(itemOptions, "haveitem").isAvailable());
		assertEquals("skip-haveitem", byId(itemOptions, "haveitem").endpoint());
		assertFalse(byId(itemOptions, "noaccess").isAvailable());
		assertEquals("Only for tasks that need a quest.", byId(itemOptions, "noaccess").getReason());

		ClanTaskState boss = state("kc", "Secrets of the North", 0);
		List<SkipOption> bossOptions = SkipOptions.resolve(boss);
		assertEquals(Arrays.asList("reroll", "greenlog", "noaccess"), ids(bossOptions));
		assertFalse(byId(bossOptions, "reroll").isAvailable());
		assertTrue(byId(bossOptions, "greenlog").isAvailable());
		assertEquals("skip-greenlog", byId(bossOptions, "greenlog").endpoint());
		assertTrue(byId(bossOptions, "noaccess").isAvailable());
		assertTrue(byId(bossOptions, "noaccess").getHint().contains("Secrets of the North"));
	}

	@Test
	public void serverSkipOptionsReplaceLocalRules()
	{
		SkipOption only = new SkipOption();
		only.setId("greenlog");
		only.setLabel("Green log");
		only.setAction("skip-greenlog");
		only.setAvailable(true);
		only.setCost("free");
		only.setHint("Server hint");
		ClanTaskState state = state("item", null, 5);
		state.setSkipOptions(Collections.singletonList(only));
		List<SkipOption> options = SkipOptions.resolve(state);
		assertEquals(1, options.size());
		assertEquals("skip-greenlog", options.get(0).endpoint());
		assertEquals("Server hint", options.get(0).getHint());
		ClanTaskState copy = state.withTask(state.getTask());
		assertEquals(1, SkipOptions.resolve(copy).size());
	}

	@Test
	public void workingStateDisablesEveryTaskButtonThenRestoresThem() throws Exception
	{
		ClanTaskState state = state("kc", "Dragon Slayer II", 3);
		ClanTasksSection[] section = new ClanTasksSection[1];
		SwingUtilities.invokeAndWait(() ->
		{
			section[0] = new ClanTasksSection(null);
			section[0].showState(state);
		});
		assertEquals("Skip \u25BE", section[0].skipLabel());
		assertTrue(section[0].doneEnabled());
		assertTrue(section[0].refreshEnabled());
		SwingUtilities.invokeAndWait(() -> section[0].beginWork("skip", "Working... (green log)"));
		assertEquals("Working... (green log)", section[0].skipLabel());
		assertTrue(section[0].skipOutlined());
		assertFalse(section[0].doneEnabled());
		assertFalse(section[0].skipEnabled());
		assertFalse(section[0].refreshEnabled());
		assertFalse(section[0].takeEnabled());
		assertEquals(ClanTasksSection.SENDING, section[0].messageText());
		SwingUtilities.invokeAndWait(() ->
		{
			section[0].showResult(true, "Skipped for free (green log). New task: Kill Vorkath 50 times.");
			section[0].endWork();
		});
		assertEquals("Skip \u25BE", section[0].skipLabel());
		assertFalse(section[0].skipOutlined());
		assertTrue(section[0].doneEnabled());
		assertTrue(section[0].refreshEnabled());
		assertTrue(section[0].messageText().startsWith("\u2713 Skipped for free"));
	}

	@Test
	public void pressDuringCheckIsQueuedNotDropped() throws Exception
	{
		ExecutorService executor = Executors.newSingleThreadExecutor();
		TaskButtonQueue queue = new TaskButtonQueue(executor);
		CountDownLatch checkStarted = new CountDownLatch(1);
		CountDownLatch releaseCheck = new CountDownLatch(1);
		List<String> order = Collections.synchronizedList(new ArrayList<>());
		assertTrue(queue.submit(() ->
		{
			checkStarted.countDown();
			try
			{
				assertTrue(releaseCheck.await(2, TimeUnit.SECONDS));
			}
			catch (InterruptedException e)
			{
				throw new IllegalStateException(e);
			}
			order.add("check");
		}));
		assertTrue(checkStarted.await(2, TimeUnit.SECONDS));
		assertTrue(queue.submit(() -> order.add("press")));
		releaseCheck.countDown();
		executor.shutdown();
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
		assertEquals(Arrays.asList("check", "press"), order);
	}

	@Test
	public void requestIdIsReusedUntilTheWebsiteAnswers()
	{
		TaskRequestIds ids = new TaskRequestIds();
		assertEquals("one", ids.id("skip-greenlog", 7, () -> "one"));
		assertEquals("one", ids.id("skip-greenlog", 7, () -> "two"));
		ids.finish("skip-greenlog", 7, 500);
		assertEquals("one", ids.id("skip-greenlog", 7, () -> "three"));
		ids.finish("skip-greenlog", 7, 429);
		assertEquals("one", ids.id("skip-greenlog", 7, () -> "four"));
		ids.finish("skip-greenlog", 7, 200);
		assertEquals("five", ids.id("skip-greenlog", 7, () -> "five"));
		assertEquals("six", ids.id("reroll", 7, () -> "six"));
		ids.finish("skip-noaccess", 7, 409);
		assertFalse(ids.has("skip-noaccess", 7));
	}

	private static List<String> ids(List<SkipOption> options)
	{
		List<String> ids = new ArrayList<>();
		for (SkipOption option : options)
		{
			ids.add(option.getId());
		}
		return ids;
	}

	private static SkipOption byId(List<SkipOption> options, String id)
	{
		for (SkipOption option : options)
		{
			if (id.equals(option.getId()))
			{
				return option;
			}
		}
		throw new AssertionError(id);
	}

	private static ClanTaskState state(String type, String requirement, int rerolls)
	{
		ClanTask task = new ClanTask();
		task.setAssignmentId(1);
		task.setType(type);
		task.setTitle("kc".equals(type) ? "Kill Phantom Muspah 25 times" : "Obtain a Dragon defender");
		task.setBoss("kc".equals(type) ? "Phantom Muspah" : "Dragon defender");
		task.setRequirement(requirement);
		task.setDifficulty("mid");
		task.setPoints(35);
		ClanTaskState state = new ClanTaskState();
		state.setFeature(ClanTaskState.FEATURE);
		state.setAvailable(true);
		state.setJoined(true);
		state.setRerolls(rerolls);
		state.setTask(task);
		return state;
	}
}
