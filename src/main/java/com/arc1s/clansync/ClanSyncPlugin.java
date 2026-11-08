package com.arc1s.clansync;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

/**
 * Sends your own drops, kill counts, kill and raid times to a clan website you choose, and lets the clan
 * Owner and Deputy Owners upload the clan roster. Nothing is recorded or sent until the user enters the website
 * address and their personal code and turns an upload on.
 */
@Slf4j
@PluginDescriptor(
	name = "Clan Sync & Loot Tracker",
	internalName = "clan-sync-loot-tracker",
	description = "Sends your drops, kill counts, kill times and raid times (and the clan roster for clan leaders) to your clan's website",
	tags = {"clan", "loot", "drops", "personal best", "kill count", "raids", "roster", "website"}
)
public class ClanSyncPlugin extends Plugin
{
	private static final int AUTO_CHECK_TICKS = 50;
	private static final int LOOT_SEND_TICKS = 500;
	private static final int LOOT_SEND_DELAY_TICKS = 10;
	private static final long AUTO_RETRY_SECONDS = 30 * 60;
	/** The automatic full roster upload runs at most once a day; the website also skips it if another leader already sent one. */
	static final long AUTO_INTERVAL_SECONDS = 24 * 60 * 60;
	private static final DateTimeFormatter DISPLAY_TIME =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
	/** Clan task checks: every 90 seconds while logged in (the website allows 120 requests per 10 minutes). */
	static final long TASK_POLL_MILLIS = 90_000;
	private static final long TASK_RETRY_MILLIS = 180_000;
	private static final long TASK_BUSY_MILLIS = 600_000;
	private static final long TASK_UNSUPPORTED_MILLIS = 30 * 60_000;
	/**
	 * After a kill or drop that counts for the task has been uploaded, the task is checked again early, but at most
	 * once per 30 seconds, so uploads and checks together stay well under the website's limit.
	 */
	static final long TASK_EVENT_GAP_MILLIS = 30_000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClanSyncConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OverlayManager overlayManager;

	private final AtomicBoolean running = new AtomicBoolean();
	/** Stops a slow upload from queueing another send on every 10th tick. There is only one client executor thread. */
	private final AtomicBoolean lootSendQueued = new AtomicBoolean();
	private SnapshotStore store;
	private SiteUploader uploader;
	private ClanSyncPanel panel;
	private NavigationButton navButton;
	private int tickCounter;
	private volatile int lootPausedUntil;
	private int lootPendingSince = -1;
	private LootQueue lootQueue;
	private LootSender lootSender;
	private LootCollector lootCollector;
	private long nextAutoAttempt;
	private final AuthGate rosterGate = new AuthGate();

	private ClanTasksClient tasksClient;
	private TaskCompleteOverlay taskOverlay;
	private ClanTaskOverlay taskProgressOverlay;
	/** The task preferences saved on the website (null = not known yet, not joined, or an older website). */
	private volatile Boolean serverWilderness;
	private volatile Boolean serverSlayerTasks;
	/** The user changed a preference that could not be sent yet: send it with the next state check. */
	private volatile boolean prefsDirty;
	/** True while the settings are being set to the website's values (those changes must not be sent back). */
	private volatile boolean applyingServerPrefs;
	private final AuthGate tasksGate = new AuthGate();
	private final AtomicBoolean tasksBusy = new AtomicBoolean();
	private final Map<String, String> pendingTaskRequests = new ConcurrentHashMap<>();
	private volatile long nextTaskPoll;
	private volatile long lastTaskRefresh;
	private volatile boolean tasksSeen;
	/** The website's latest clan task state; the panel and overlay show it with the local progress on top. */
	private volatile ClanTaskState lastTaskState;
	private final TaskProgressTracker taskProgress = new TaskProgressTracker();
	/** Client thread only: whether the roster part of the panel is shown (Owner / Deputy Owner). */
	private boolean rosterShown;

	@Provides
	ClanSyncConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanSyncConfig.class);
	}

	@Override
	protected void startUp() throws IOException
	{
		TextStore files = new PluginDirectoryStore(getPluginDirectory());
		store = new SnapshotStore(files, gson);
		OkHttpClient http = withTimeouts(okHttpClient);
		uploader = new SiteUploader(http, gson);
		running.set(false);
		nextAutoAttempt = 0;
		rosterShown = false;

		lootQueue = new LootQueue(files, gson);
		lootSender = new LootSender(lootQueue, uploader, config);
		lootCollector = new LootCollector(client, itemManager, config, lootQueue, new ContainerLedger(files, gson), this::showLootQueue,
			player -> lootSender.isRejected(player), executor);
		lootCollector.setTaskListener(new LootCollector.TaskListener()
		{
			@Override
			public void onKillCount(String name, int count)
			{
				if (tasksOn() && tasksSeen && taskProgress.onKillCount(name, count, System.currentTimeMillis()))
				{
					showLocalTaskProgress();
				}
			}

			@Override
			public void onLoot(String type, String source, Map<String, Integer> items)
			{
				if (tasksOn() && tasksSeen && taskProgress.onLoot(type, source, items, System.currentTimeMillis()))
				{
					showLocalTaskProgress();
				}
			}
		});
		eventBus.register(lootCollector);

		tasksClient = new ClanTasksClient(http, gson);
		taskOverlay = new TaskCompleteOverlay();
		overlayManager.add(taskOverlay);
		taskProgressOverlay = new ClanTaskOverlay(this, () -> config.taskOverlay() && tasksSeen && tasksOn()
			&& client.getGameState() == GameState.LOGGED_IN);
		overlayManager.add(taskProgressOverlay);
		prefsDirty = false;
		resetTasks();

		panel = new ClanSyncPanel(this::requestManualSync, new ClanTasksSection.Actions()
		{
			@Override
			public void done(ClanTask task)
			{
				confirmDone(task);
			}

			@Override
			public void reroll(ClanTask task, ClanTaskState state)
			{
				confirmReroll(task, state);
			}

			@Override
			public void take()
			{
				confirmTake();
			}

			@Override
			public void refresh()
			{
				nextTaskPoll = 0;
				refreshTasks();
			}
		});
		navButton = NavigationButton.builder()
			.tooltip("Clan Sync & Loot Tracker")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		long lastSync = lastSyncEpochSeconds();
		if (lastSync > 0)
		{
			panel.setLastSync(DISPLAY_TIME.format(Instant.ofEpochSecond(lastSync)));
		}
		ClanSnapshot latest = store.loadLatest();
		if (latest != null)
		{
			panel.setMembers(String.valueOf(latest.getMemberCount()));
			showChanges(panel, latest.getChanges());
		}
		showLootQueue();
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(lootCollector);
		// One last attempt to send what is queued; whatever fails stays in the queue file for next time.
		LootSender sender = lootSender;
		if (sender != null)
		{
			executor.execute(sender::sendAll);
		}
		lootCollector = null;
		overlayManager.remove(taskOverlay);
		taskOverlay = null;
		overlayManager.remove(taskProgressOverlay);
		taskProgressOverlay = null;
		clientToolbar.removeNavigation(navButton);
		rosterShown = false;
		panel = null;
		navButton = null;
	}

	// Runs on the client thread: only counts ticks and hands uploads to the executor, never does network IO itself.
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (lootQueue.size() > 0 && lootPendingSince < 0)
		{
			lootPendingSince = tickCounter;
		}
		boolean waitedEnough = lootPendingSince >= 0 && tickCounter - lootPendingSince >= LOOT_SEND_DELAY_TICKS;
		if (tickCounter >= lootPausedUntil && (waitedEnough || tickCounter % LOOT_SEND_TICKS == 0))
		{
			lootPendingSince = -1;
			requestLootSend();
		}
		if (tasksOn() && System.currentTimeMillis() >= nextTaskPoll)
		{
			refreshTasks();
		}
		showRosterIfLeader(canSyncRoster());
		if (++tickCounter % AUTO_CHECK_TICKS != 0 || !rosterUploadsOn() || running.get())
		{
			return;
		}

		long now = Instant.now().getEpochSecond();
		if (now < nextAutoAttempt || now - lastSyncEpochSeconds() < AUTO_INTERVAL_SECONDS)
		{
			return;
		}
		// The clan settings arrive a little after login; wait for them instead of failing.
		if (client.getClanSettings() == null || !canSyncRoster())
		{
			return;
		}

		nextAutoAttempt = now + AUTO_RETRY_SECONDS;
		startSync(ClanSnapshot.TRIGGER_AUTO);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ClanSyncConfig.GROUP.equals(event.getGroup()))
		{
			showLootQueue();
			String key = event.getKey();
			if ("endpoint".equals(key) || "memberToken".equals(key) || "clanTasks".equals(key))
			{
				resetTasks();
			}
			else if (("wildernessTasks".equals(key) || "slayerBossTasks".equals(key)) && !applyingServerPrefs)
			{
				sendPrefs();
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			requestLootSend();
			showRosterIfLeader(false);
		}
	}

	/** The roster part of the panel is only for the clan Owner and Deputy Owners (unknown rank or no clan: hidden). */
	private void showRosterIfLeader(boolean leader)
	{
		ClanSyncPanel ui = panel;
		if (ui != null && leader != rosterShown)
		{
			rosterShown = leader;
			ui.setRosterVisible(leader);
		}
	}

	private boolean rosterUploadsOn()
	{
		// After the website refused the code, automatic roster uploads wait for a new code or a manual sync.
		return config.syncRoster() && LootSender.isConfigured(config) && !rosterGate.isRejected(config.endpoint(), config.memberToken());
	}

	/**
	 * Must run on the client thread. True when the logged-in player is the clan Owner or a Deputy Owner,
	 * the only people who may upload the roster.
	 */
	private boolean canSyncRoster()
	{
		ClanSettings settings = client.getClanSettings();
		Player local = client.getLocalPlayer();
		if (settings == null || local == null || local.getName() == null)
		{
			return false;
		}
		String key = SnapshotDiff.key(clean(local.getName()));
		for (ClanMember member : settings.getMembers())
		{
			if (SnapshotDiff.key(clean(member.getName())).equals(key))
			{
				return member.getRank() != null && isRosterRank(member.getRank().getRank());
			}
		}
		return false;
	}

	/** Owner (126) and Deputy Owner (125) only; Jagex moderators (127) and everyone else are excluded. */
	static boolean isRosterRank(int rank)
	{
		return rank == ClanRank.OWNER.getRank() || rank == ClanRank.DEPUTY_OWNER.getRank();
	}

	private void requestLootSend()
	{
		ClanSyncPanel ui = panel;
		if (lootQueue.size() == 0 || !LootSender.isConfigured(config))
		{
			return;
		}
		if (!lootSendQueued.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			executor.execute(() ->
			{
				try
				{
					lootSender.sendAll();
					if (lootSender.isFailing())
					{
						lootPausedUntil = tickCounter + LOOT_SEND_TICKS;
					}
					else if (taskProgress.takeUploadPending())
					{
						checkTasksSoon();
					}
					if (ui != null)
					{
						ui.setLoot(lootSender.describe());
					}
				}
				finally
				{
					lootSendQueued.set(false);
				}
			});
		}
		catch (RuntimeException e)
		{
			lootSendQueued.set(false);
			throw e;
		}
	}

	/**
	 * The shared client has a 10 second read timeout and no overall call timeout (OkHttp 3.14 defaults).
	 * {@code newBuilder()} keeps RuneLite's interceptors and dispatcher. A call that trickles bytes would
	 * otherwise pin the client's only scheduled thread until restart.
	 */
	private static OkHttpClient withTimeouts(OkHttpClient base)
	{
		return base.newBuilder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.writeTimeout(20, TimeUnit.SECONDS)
			.callTimeout(30, TimeUnit.SECONDS)
			.build();
	}

	private void showLootQueue()
	{
		ClanSyncPanel ui = panel;
		if (ui != null)
		{
			ui.setLoot(!LootSender.isConfigured(config) ? LootSender.NOT_CONFIGURED
				: config.trackLoot() ? lootSender.describe() : "upload is off (plugin settings)");
			ui.setSetupHint(!LootSender.isConfigured(config));
		}
	}

	/** The "Sync clan members" button: always uploads right away, whatever was sent before. */
	private void requestManualSync()
	{
		startSync(ClanSnapshot.TRIGGER_MANUAL);
	}

	private void startSync(String trigger)
	{
		ClanSyncPanel ui = panel;
		if (ui == null)
		{
			return;
		}
		if (!config.syncRoster())
		{
			ui.setStatus("Turn on \"Upload clan roster\" in the plugin settings first", true);
			return;
		}
		if (!LootSender.isConfigured(config))
		{
			ui.setStatus("Enter the website API URL and your personal code in the plugin settings first", true);
			return;
		}
		if (!running.compareAndSet(false, true))
		{
			return;
		}
		ui.setBusy(true);
		ui.setStatus("Reading clan roster...", false);

		clientThread.invokeLater(() ->
		{
			try
			{
				if (!canSyncRoster())
				{
					throw new IllegalStateException("Only the clan Owner and Deputy Owners can upload the roster");
				}
				ClanSnapshot snapshot = readRoster();
				snapshot.setSyncType(ClanSnapshot.SYNC_FULL);
				snapshot.setTrigger(trigger);
				addCollectionLogLayout(snapshot);
				executor.execute(() -> runSync(snapshot, ui));
			}
			catch (RuntimeException e)
			{
				fail(ui, e.getMessage(), e instanceof IllegalStateException ? null : e);
			}
		});
	}

	/**
	 * Must run on the client thread. Reads only the logged-in player's own clan settings.
	 */
	private ClanSnapshot readRoster()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			throw new IllegalStateException("Log in to the game first");
		}
		ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			throw new IllegalStateException("Clan data not loaded yet, or you are not in a clan");
		}

		String clanName = clean(settings.getName());
		String expected = config.clanName().trim();
		if (!expected.isEmpty() && !SnapshotDiff.key(expected).equals(SnapshotDiff.key(clanName)))
		{
			throw new IllegalStateException("You are in clan \"" + clanName + "\", not \"" + expected + "\"");
		}

		ClanSnapshot snapshot = new ClanSnapshot();
		snapshot.setClanName(clanName);
		snapshot.setCollectedAt(Instant.now().toString());
		if (client.getLocalPlayer() != null)
		{
			snapshot.setCollectedBy(clean(client.getLocalPlayer().getName()));
		}

		List<ClanSnapshot.Member> members = snapshot.getMembers();
		for (ClanMember clanMember : settings.getMembers())
		{
			ClanSnapshot.Member member = new ClanSnapshot.Member();
			member.setName(clean(clanMember.getName()));
			member.setRank(clanMember.getRank().getRank());
			ClanTitle title = settings.titleForRank(clanMember.getRank());
			member.setTitle(title == null ? null : clean(title.getName()));
			LocalDate joinDate = clanMember.getJoinDate();
			member.setJoinDate(joinDate == null ? null : joinDate.toString());
			members.add(member);
		}
		members.sort(Comparator.comparingInt(ClanSnapshot.Member::getRank).reversed()
			.thenComparing(ClanSnapshot.Member::getName, String.CASE_INSENSITIVE_ORDER));
		snapshot.setMemberCount(members.size());
		return snapshot;
	}

	/**
	 * Must run on the client thread. The collection log layout and item names come from the game cache,
	 * so the website can show logs the way the game does. Optional: a failure only leaves them out.
	 */
	private void addCollectionLogLayout(ClanSnapshot snapshot)
	{
		try
		{
			CollectionLogLayout layout = CollectionLogLayout.read(client);
			Set<Integer> ids = new TreeSet<>();
			for (CollectionLogLayout.Tab tab : layout.getTabs())
			{
				for (CollectionLogLayout.Page page : tab.getPages())
				{
					ids.addAll(page.getItems());
				}
			}
			Map<Integer, String> names = new TreeMap<>();
			for (int id : ids)
			{
				String name = itemManager.getItemComposition(id).getName();
				if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name))
				{
					names.put(id, name);
				}
			}
			snapshot.setCollectionLogLayout(layout);
			snapshot.setItemNames(names);
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read the collection log layout", e);
		}
	}

	private void runSync(ClanSnapshot snapshot, ClanSyncPanel ui)
	{
		boolean full = ClanSnapshot.SYNC_FULL.equals(snapshot.getSyncType());
		try
		{
			ClanSnapshot previous = store.loadLatest();
			snapshot.setChanges(SnapshotDiff.compute(previous, snapshot));
			ui.setMembers(String.valueOf(snapshot.getMemberCount()));
			showChanges(ui, snapshot.getChanges());

			String url = SiteUploader.resolve(config.endpoint(), SiteUploader.ROSTER_SEGMENT);
			if (url == null)
			{
				throw new IOException("Invalid website URL: " + config.endpoint());
			}
			ui.setStatus("Uploading to website...", false);
			SiteUploader.Reply reply;
			try
			{
				reply = uploader.post(url, config.memberToken(), snapshot);
			}
			catch (SiteUploader.RejectedException e)
			{
				store.savePending(snapshot);
				rosterGate.reject(config.endpoint(), config.memberToken());
				throw new IOException(e.getMessage() + ". Roster uploads are paused until you enter a new code.", e);
			}
			catch (IOException e)
			{
				store.savePending(snapshot);
				throw e;
			}
			rosterGate.clear();

			long now = Instant.now().getEpochSecond();
			if (full)
			{
				// Also after a skip: another leader already uploaded today, so the next automatic try is tomorrow.
				configManager.setConfiguration(ClanSyncConfig.GROUP, ClanSyncConfig.LAST_SYNC_KEY, now);
			}
			if (reply.skipped)
			{
				ui.setStatus(reply.message.isEmpty() ? "Clan members were already synced today; nothing to do" : reply.message, false);
			}
			else
			{
				store.saveLatest(mergeForStorage(previous, snapshot));
				if (full)
				{
					ui.setLastSync(DISPLAY_TIME.format(Instant.ofEpochSecond(now)));
				}
				ui.setStatus("Clan members synced to the website", false);
			}
		}
		catch (IOException | RuntimeException e)
		{
			fail(ui, e.getMessage(), e);
			return;
		}
		finally
		{
			running.set(false);
		}
		ui.setBusy(false);
	}

	/**
	 * The stored snapshot is only the diff baseline; a roster sync keeps the collection log layout of the
	 * last full sync so it is not lost.
	 */
	static ClanSnapshot mergeForStorage(ClanSnapshot previous, ClanSnapshot current)
	{
		if (previous == null)
		{
			return current;
		}
		if (current.getItemNames() == null)
		{
			current.setItemNames(previous.getItemNames());
		}
		if (current.getCollectionLogLayout() == null)
		{
			current.setCollectionLogLayout(previous.getCollectionLogLayout());
		}
		return current;
	}

	private static void showChanges(ClanSyncPanel ui, ClanSnapshot.Changes changes)
	{
		if (changes == null || changes.isEmpty())
		{
			ui.setChanges(null, null);
		}
		else
		{
			ui.setChanges(changesTitle(changes), describeChanges(changes));
		}
	}

	/** "Roster changes: +2 -1, 3 ranks" */
	static String changesTitle(ClanSnapshot.Changes changes)
	{
		StringBuilder title = new StringBuilder("Roster changes:");
		if (!changes.getJoined().isEmpty())
		{
			title.append(" +").append(changes.getJoined().size());
		}
		if (!changes.getLeft().isEmpty())
		{
			title.append(" -").append(changes.getLeft().size());
		}
		if (!changes.getRankChanges().isEmpty())
		{
			int ranks = changes.getRankChanges().size();
			title.append(title.charAt(title.length() - 1) == ':' ? " " : ", ").append(ranks).append(ranks == 1 ? " rank" : " ranks");
		}
		return title.toString();
	}

	static String describeChanges(ClanSnapshot.Changes changes)
	{
		if (changes == null)
		{
			return "First sync - changes will be shown from the next one.";
		}
		if (changes.isEmpty())
		{
			return "No changes since the last sync.";
		}

		StringBuilder text = new StringBuilder();
		if (!changes.getJoined().isEmpty())
		{
			text.append("Joined (").append(changes.getJoined().size()).append("):\n");
			changes.getJoined().forEach(name -> text.append("  + ").append(name).append('\n'));
		}
		if (!changes.getLeft().isEmpty())
		{
			text.append("Left (").append(changes.getLeft().size()).append("):\n");
			changes.getLeft().forEach(name -> text.append("  - ").append(name).append('\n'));
		}
		if (!changes.getRankChanges().isEmpty())
		{
			text.append("Rank changed (").append(changes.getRankChanges().size()).append("):\n");
			for (ClanSnapshot.RankChange change : changes.getRankChanges())
			{
				text.append("  ").append(change.getName()).append(": ")
					.append(rankLabel(change.getFromTitle(), change.getFromRank())).append(" -> ")
					.append(rankLabel(change.getToTitle(), change.getToRank())).append('\n');
			}
		}
		return text.toString();
	}

	private static String rankLabel(String title, int rank)
	{
		return title == null || title.isEmpty() ? String.valueOf(rank) : title;
	}

	// ---- Clan tasks (optional; only when the website supports them and the user turned them on) ----

	private boolean tasksOn()
	{
		return config.clanTasks() && LootSender.isConfigured(config) && !tasksGate.isRejected(config.endpoint(), config.memberToken());
	}

	/** Settings changed: forget what we knew and check again on the next game tick. */
	private void resetTasks()
	{
		tasksGate.clear();
		pendingTaskRequests.clear();
		tasksSeen = false;
		nextTaskPoll = 0;
		serverWilderness = null;
		serverSlayerTasks = null;
		lastTaskState = null;
		taskProgress.setServerTask(null, System.currentTimeMillis());
		showTaskOnOverlay(null);
		ClanSyncPanel ui = panel;
		if (ui != null)
		{
			ui.showTasks(false, null);
		}
	}

	/**
	 * A counted kill or drop was uploaded: check the task early so the website's count replaces the local one.
	 * Only moves a normal 90 second check forward (never a busy/retry wait), and keeps 30 seconds between checks.
	 */
	private void checkTasksSoon()
	{
		long now = System.currentTimeMillis();
		long at = Math.max(now, lastTaskRefresh + TASK_EVENT_GAP_MILLIS);
		long next = nextTaskPoll;
		if (next != 0 && next <= lastTaskRefresh + TASK_POLL_MILLIS && at < next)
		{
			nextTaskPoll = at;
		}
	}

	/** A kill or drop counted for the task: show it in the panel and overlay now, and pop up when the target is reached. */
	private void showLocalTaskProgress()
	{
		ClanSyncPanel ui = panel;
		ClanTaskState state = lastTaskState;
		if (ui == null || state == null)
		{
			return;
		}
		ClanTaskState shown = state.withTask(taskProgress.display(System.currentTimeMillis()));
		ui.showTasks(true, shown);
		showTaskOnOverlay(shown.getTask());
		if (shown.getTask() != null && shown.getTask().isReached())
		{
			popup(shown.getTask());
		}
	}

	private void refreshTasks()
	{
		ClanSyncPanel ui = panel;
		if (ui == null || !tasksOn() || !tasksBusy.compareAndSet(false, true))
		{
			return;
		}
		lastTaskRefresh = System.currentTimeMillis();
		nextTaskPoll = lastTaskRefresh + TASK_POLL_MILLIS;
		String endpoint = config.endpoint();
		String code = config.memberToken();
		executor.execute(() ->
		{
			try
			{
				handleTaskResult(ui, tasksClient.current(endpoint, code), endpoint, code, null, null);
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Clan task check failed", e);
				nextTaskPoll = System.currentTimeMillis() + TASK_RETRY_MILLIS;
				if (tasksSeen)
				{
					ui.setTasksMessage("Could not reach the website: " + e.getMessage(), true);
				}
			}
			finally
			{
				tasksBusy.set(false);
			}
		});
	}

	/** Shared handling for the state check and the buttons. Runs on the executor. */
	private void handleTaskResult(ClanSyncPanel ui, ClanTasksClient.Result result, String endpoint, String code, String action, ClanTask acted)
	{
		if (!result.supported && action != null)
		{
			// An older website without this button: keep the section and say so.
			ui.setTasksMessage("The website does not support this button yet. Use the Discord command instead.", true);
			return;
		}
		if (!result.supported)
		{
			// Not a clan tasks website (or not yet): keep the section hidden and look again much later.
			nextTaskPoll = System.currentTimeMillis() + TASK_UNSUPPORTED_MILLIS;
			tasksSeen = false;
			lastTaskState = null;
			taskProgress.setServerTask(null, System.currentTimeMillis());
			ui.showTasks(false, null);
			showTaskOnOverlay(null);
			return;
		}
		if (result.codeRefused())
		{
			tasksGate.reject(endpoint, code);
			if (tasksSeen)
			{
				ui.setTasksMessage(result.message + ". Clan task checks are paused until you enter a new code.", true);
			}
			return;
		}
		if (result.status == 429 || result.status >= 500)
		{
			nextTaskPoll = System.currentTimeMillis() + (result.status == 429 ? TASK_BUSY_MILLIS : TASK_RETRY_MILLIS);
			if (tasksSeen)
			{
				ui.setTasksMessage(result.message.isEmpty() ? "Website is busy, trying again later" : result.message, true);
			}
			return;
		}
		if (result.state != null)
		{
			tasksSeen = true;
			lastTaskState = result.state;
			long now = System.currentTimeMillis();
			taskProgress.setServerTask(result.state.getTask(), now);
			ClanTaskState shown = result.state.withTask(taskProgress.display(now));
			ui.showTasks(true, shown);
			ClanTask task = shown.getTask();
			showTaskOnOverlay(task);
			applyServerPrefs(result.state);
			if (task != null && task.isReached())
			{
				popup(task);
			}
		}
		if (action != null)
		{
			ui.setTasksMessage(result.message, !result.ok());
			if (result.ok() && ClanTasksClient.ACTION_DONE.equals(action))
			{
				popup(result.completed != null ? result.completed : acted);
			}
			if (!result.ok() && result.state == null)
			{
				// The task changed elsewhere (Discord, another device): show the current one.
				nextTaskPoll = 0;
			}
		}
	}

	private void popup(ClanTask task)
	{
		TaskCompleteOverlay overlay = taskOverlay;
		if (task == null || overlay == null || !config.taskPopup())
		{
			return;
		}
		String shown = configManager.getConfiguration(ClanSyncConfig.GROUP, ClanSyncConfig.TASK_POPUPS_KEY);
		if (PopupMemory.contains(shown, task.getAssignmentId()))
		{
			return;
		}
		configManager.setConfiguration(ClanSyncConfig.GROUP, ClanSyncConfig.TASK_POPUPS_KEY, PopupMemory.add(shown, task.getAssignmentId()));
		overlay.show(TaskCompleteOverlay.popupFor(task, "Clan task complete!"));
	}

	private void showTaskOnOverlay(ClanTask task)
	{
		ClanTaskOverlay overlay = taskProgressOverlay;
		if (overlay != null)
		{
			overlay.setTask(task);
		}
	}

	/**
	 * The website keeps the task preferences (Discord uses the same ones), so its values win: the settings show them.
	 * A change the user made while they could not be sent yet is sent now instead.
	 */
	private void applyServerPrefs(ClanTaskState state)
	{
		Boolean wilderness = state.getWildernessEnabled();
		Boolean slayer = state.getSlayerTasksEnabled();
		if (!state.isJoined() || wilderness == null || slayer == null)
		{
			serverWilderness = null;
			serverSlayerTasks = null;
			return;
		}
		serverWilderness = wilderness;
		serverSlayerTasks = slayer;
		if (prefsDirty)
		{
			prefsDirty = false;
			sendPrefs();
			return;
		}
		applyingServerPrefs = true;
		try
		{
			if (config.wildernessTasks() != wilderness)
			{
				configManager.setConfiguration(ClanSyncConfig.GROUP, "wildernessTasks", wilderness);
			}
			if (config.slayerBossTasks() != slayer)
			{
				configManager.setConfiguration(ClanSyncConfig.GROUP, "slayerBossTasks", slayer);
			}
		}
		finally
		{
			applyingServerPrefs = false;
		}
	}

	/** Sends the task preferences when they differ from the website's. They only affect the next task. */
	private void sendPrefs()
	{
		ClanSyncPanel ui = panel;
		Boolean knownWilderness = serverWilderness;
		Boolean knownSlayer = serverSlayerTasks;
		if (ui == null || !tasksOn() || knownWilderness == null || knownSlayer == null)
		{
			prefsDirty = true;
			return;
		}
		boolean wilderness = config.wildernessTasks();
		boolean slayer = config.slayerBossTasks();
		if (wilderness == knownWilderness && slayer == knownSlayer)
		{
			return;
		}
		String endpoint = config.endpoint();
		String code = config.memberToken();
		ui.setTasksMessage("Saving task preferences...", false);
		executor.execute(() ->
		{
			try
			{
				ClanTasksClient.Result result = tasksClient.prefs(endpoint, code, wilderness, slayer);
				if (result.ok() && result.state != null)
				{
					handleTaskResult(ui, result, endpoint, code, null, null);
					ui.setTasksMessage(result.message, false);
				}
				else if (!result.supported)
				{
					ui.setTasksMessage("This website cannot save task preferences yet (wilderness: use /pvm_wildy on Discord).", true);
				}
				else
				{
					if (result.status == 429 || result.status >= 500)
					{
						prefsDirty = true;
					}
					ui.setTasksMessage(result.message.isEmpty() ? "Could not save task preferences" : result.message, true);
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Saving clan task preferences failed", e);
				prefsDirty = true;
				ui.setTasksMessage("Could not reach the website: " + e.getMessage() + ". Preferences will be sent again.", true);
			}
		});
	}

	/** Asks first; the safe answer (Cancel) is the default button, so Enter or Space never confirms by accident. */
	private boolean confirm(String title, String message, String yes)
	{
		Object[] options = {yes, "Cancel"};
		return JOptionPane.showOptionDialog(panel, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
			null, options, options[1]) == 0;
	}

	/** Must run on the Swing thread. */
	private void confirmDone(ClanTask task)
	{
		String text = "Mark this clan task as done?\n\n" + task.displayTitle() + "\n\n";
		ClanTask.Progress progress = task.getProgress();
		if (progress != null && progress.getTarget() > 0 && progress.getCurrent() < progress.getTarget())
		{
			text += "The website only sees " + progress.getCurrent() + "/" + progress.getTarget() + " kills so far.\n";
		}
		text += "Clan tasks use the honour system: only press Done if you really finished it.\n"
			+ "You get " + task.getPoints() + " points and a new task right away. This cannot be undone.";
		if (confirm("Clan task: Done", text, "Yes, done"))
		{
			runTaskAction(ClanTasksClient.ACTION_DONE, task);
		}
	}

	private void confirmReroll(ClanTask task, ClanTaskState state)
	{
		int rerolls = state == null ? 0 : state.getRerolls();
		if (rerolls < 1)
		{
			JOptionPane.showMessageDialog(panel, noRerollsText(state == null ? 0 : state.getNextRerollIn()), "Clan task: Skip / Re-roll",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (confirm("Clan task: Skip / Re-roll", rerollConfirmText(task, rerolls), "Yes, re-roll"))
		{
			runTaskAction(ClanTasksClient.ACTION_REROLL, task);
		}
	}

	static String rerollConfirmText(ClanTask task, int rerolls)
	{
		return "Skip this task and get a new one?\n\n" + task.displayTitle() + "\n\n"
			+ "This uses 1 re-roll, the same as the Discord skip.\n"
			+ "Re-rolls left: " + rerolls + " now, " + (rerolls - 1) + " after this. This cannot be undone.";
	}

	static String noRerollsText(int nextIn)
	{
		return "You have no re-rolls left (0)." + (nextIn > 0
			? "\nYou get the next one after " + nextIn + " more completed task" + (nextIn == 1 ? "" : "s") + "." : "");
	}

	private void confirmTake()
	{
		if (confirm("Clan tasks", "Get a new clan task now?", "Get a task"))
		{
			runTaskAction(ClanTasksClient.ACTION_TAKE, null);
		}
	}

	private void runTaskAction(String action, ClanTask task)
	{
		ClanSyncPanel ui = panel;
		if (ui == null || !tasksOn())
		{
			return;
		}
		if (!tasksBusy.compareAndSet(false, true))
		{
			ui.setTasksMessage("Please wait, still talking to the website...", false);
			return;
		}
		Integer assignmentId = task == null ? null : task.getAssignmentId();
		// The same request id is reused if this exact action is retried after a network error, so it never runs twice.
		String key = action + ":" + assignmentId;
		String requestId = pendingTaskRequests.computeIfAbsent(key, k -> newRequestId());
		String endpoint = config.endpoint();
		String code = config.memberToken();
		ui.setTasksBusy(true);
		ui.setTasksMessage("Sending...", false);
		executor.execute(() ->
		{
			try
			{
				ClanTasksClient.Result result = tasksClient.act(endpoint, code, action, assignmentId, requestId);
				if (result.status != 429 && result.status < 500)
				{
					pendingTaskRequests.remove(key);
				}
				handleTaskResult(ui, result, endpoint, code, action, task);
				if (nextTaskPoll != 0)
				{
					nextTaskPoll = System.currentTimeMillis() + TASK_POLL_MILLIS;
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Clan task action failed", e);
				ui.setTasksMessage("Could not reach the website: " + e.getMessage() + ". Press the button again to retry.", true);
			}
			finally
			{
				tasksBusy.set(false);
				ui.setTasksBusy(false);
			}
		});
	}

	static String newRequestId()
	{
		return UUID.randomUUID().toString().replace("-", "");
	}

	private void fail(ClanSyncPanel ui, String message, Throwable cause)
	{
		if (cause != null)
		{
			log.warn("Clan sync failed", cause);
		}
		running.set(false);
		ui.setStatus(message == null ? "Failed" : message, true);
		ui.setBusy(false);
	}

	private long lastSyncEpochSeconds()
	{
		Long value = configManager.getConfiguration(ClanSyncConfig.GROUP, ClanSyncConfig.LAST_SYNC_KEY, Long.class);
		return value == null ? 0 : value;
	}

	private static String clean(String name)
	{
		return name == null ? null : Text.removeTags(name).replace('\u00A0', ' ').trim();
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// A plain banner: gold field with a dark stripe.
		g.setColor(new Color(0xC8A43B));
		g.fillRoundRect(2, 1, 12, 14, 4, 4);
		g.setColor(new Color(0x3A2F1B));
		g.fillRect(4, 6, 8, 3);
		g.dispose();
		return image;
	}
}
