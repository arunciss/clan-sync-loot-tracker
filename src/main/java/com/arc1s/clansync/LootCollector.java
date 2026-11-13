package com.arc1s.clansync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;

@Slf4j
public class LootCollector
{
	private static final int CONTAINER_WINDOW_TICKS = 3;
	private static final int KILL_COUNT_WINDOW_TICKS = 6;
	static final String PVP_SOURCE = "PvP";
	static final String LOOT_CHEST = "Loot Chest";
	private static final Set<String> OPEN_OPTIONS = new HashSet<>(Arrays.asList("open", "search", "unlock", "loot"));

	private final Client client;
	private final ItemManager itemManager;
	private final ClanSyncConfig config;
	private final LootQueue queue;
	private final KillCountParser killCountParser = new KillCountParser();
	private final DurationMessageParser durationParser = new DurationMessageParser();
	private final KillTimeTracker killTimes = new KillTimeTracker();
	private final RaidRunTracker raidRuns = new RaidRunTracker();
	private final SkillMilestones skillMilestones = new SkillMilestones();
	private final CaveRewardDetector caves = new CaveRewardDetector();
	private final ContainerLedger containers;
	private final Runnable onRecorded;
	private final Predicate<String> paused;
	/** RuneLite's scheduled executor. Disk writes must not run on the client thread. */
	private final Executor io;
	private ContainerOpening opening;
	private KillCount lastKillCount;
	private volatile TaskListener taskListener;

	/** Gets every kill count message and loot record as it happens, for the clan task progress. */
	interface TaskListener
	{
		void onKillCount(String name, int count);

		void onLoot(String type, String source, Map<String, Integer> items);
	}

	void setTaskListener(TaskListener listener)
	{
		taskListener = listener;
	}

	public LootCollector(Client client, ItemManager itemManager, ClanSyncConfig config, LootQueue queue, ContainerLedger containers, Runnable onRecorded, Predicate<String> paused, Executor io)
	{
		this.paused = paused;
		this.containers = containers;
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
		this.queue = queue;
		this.onRecorded = onRecorded;
		this.io = io;
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!isTracking())
		{
			return;
		}
		LootRecord record = new LootRecord();
		record.setType(LootSourceType.valueOf(event.getType().name()));
		record.setSource(event.getName() == null || event.getName().trim().isEmpty() ? "Unknown" : Text.removeTags(event.getName()));
		record.setCombatLevel(event.getCombatLevel() > 0 ? event.getCombatLevel() : null);
		record.setAmount(Math.max(1, event.getAmount()));
		for (ItemStack stack : event.getItems())
		{
			record.getItems().add(new LootItem(stack.getId(), stack.getQuantity()));
		}
		// Player kills and PvP loot keys are recorded as "PvP" loot only: who the other player was is never sent.
		if (record.getType() == LootSourceType.PLAYER || (record.getType() == LootSourceType.EVENT && LOOT_CHEST.equals(event.getName())))
		{
			record.setType(LootSourceType.PLAYER);
			record.setSource(PVP_SOURCE);
			record.setCombatLevel(null);
		}
		else if (opening != null && (record.getType() == LootSourceType.EVENT || record.getType() == LootSourceType.UNKNOWN))
		{
			record.setContainerItemId(opening.getItemId());
			opening.claim();
		}
		if (caves.ignoreLoot(record.getSource()))
		{
			return;
		}
		save(record);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		int itemId = event.getItemId();
		if (itemId <= 0 || !OPEN_OPTIONS.contains(Text.removeTags(event.getMenuOption()).toLowerCase()))
		{
			return;
		}
		Map<Integer, Integer> inventory = inventory();
		if (!inventory.containsKey(itemId))
		{
			return;
		}
		opening = new ContainerOpening(itemId, itemManager.getItemComposition(itemId).getName(), inventory, client.getTickCount());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			Map<Integer, Integer> contents = inventory();
			if (opening != null)
			{
				opening.inventoryChanged(contents, client.getTickCount());
			}
			if (isTracking())
			{
				recordCape(caves.fromInventory(localPlayerName(), regionId(), contents, Instant.now()));
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		// Raid completion times arrive as FRIENDSCHATNOTIFICATION, boss fight times as GAMEMESSAGE.
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM && type != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}
		int tick = client.getTickCount();
		KillCount parsed = killCountParser.parse(event.getMessage(), tick);
		trackRaid(event.getMessage(), parsed, tick);
		if (parsed != null)
		{
			lastKillCount = parsed;
			queueKillCount(parsed);
			if (isTracking())
			{
				recordCape(caves.fromKillCount(localPlayerName(), parsed.getName(), parsed.getCount(), Instant.now()));
			}
			saveTime(killTimes.onKillCount(parsed));
			notifyKillCount(parsed);
			return;
		}
		if (isTracking())
		{
			recordCape(caves.fromCollectionLog(localPlayerName(), event.getMessage(), Instant.now()));
		}
		DurationMessage duration = durationParser.parse(event.getMessage(), tick);
		if (duration != null)
		{
			saveTime(killTimes.onDuration(duration));
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		int region = regionId();
		if (region >= 0 && CaveRewardDetector.capeForRegion(region) < 0)
		{
			caves.resetRun();
		}
		finishRaid();
		if (opening == null || !opening.isExpired(client.getTickCount(), CONTAINER_WINDOW_TICKS))
		{
			return;
		}
		LootRecord result = opening.isClaimed() ? null : opening.getResult();
		opening = null;
		if (result != null && isTracking())
		{
			save(result);
		}
	}

	// Raid runs are extra: any problem here is logged and must never stop the loot and kill time tracking.
	private void trackRaid(String message, KillCount parsed, int tick)
	{
		try
		{
			finishRaid();
			RaidContext context = raidContext();
			if (parsed != null)
			{
				raidRuns.onKillCount(parsed, context);
			}
			else if (message != null)
			{
				raidRuns.onMessage(message, tick, context);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Raid run tracking failed", e);
		}
	}

	private void finishRaid()
	{
		try
		{
			RaidRun run = raidRuns.poll(client.getTickCount());
			if (run == null || !isTracking())
			{
				return;
			}
			run.setUid(UUID.randomUUID().toString());
			run.setPlayer(Text.sanitize(client.getLocalPlayer().getName()));
			run.setRecordedAt(Instant.now().toString());
			// Raid varbits were already read above. The queue file is written off the client thread.
			io.execute(() ->
			{
				try
				{
					queue.add(run);
					onRecorded.run();
				}
				catch (RuntimeException e)
				{
					log.warn("Saving a raid run failed", e);
				}
			});
		}
		catch (RuntimeException e)
		{
			log.warn("Raid run tracking failed", e);
		}
	}

	private RaidContext raidContext()
	{
		return new RaidContext()
		{
			@Override
			public int varbit(int id)
			{
				return client.getVarbitValue(id);
			}

			@Override
			public int varp(int id)
			{
				return client.getVarpValue(id);
			}

			@Override
			public String varcStr(int id)
			{
				return client.getVarcStrValue(id);
			}

			@Override
			public List<String> coxPartyNames()
			{
				// The raids side panel lists the party; each row's widgets carry the member's name as their op name.
				List<String> names = new ArrayList<>();
				Widget list = client.getWidget(InterfaceID.RaidsSidepanel.LIST);
				if (list == null || list.isHidden())
				{
					return names;
				}
				Widget[] children = list.getDynamicChildren();
				if (children != null)
				{
					for (Widget child : children)
					{
						if (child != null && child.getName() != null && !child.getName().isEmpty())
						{
							names.add(child.getName());
						}
					}
				}
				return names;
			}

			@Override
			public List<String> raidPlayerNames()
			{
				// Only inside the Chambers of Xeric instance, where nobody but the raid party can be.
				List<String> names = new ArrayList<>();
				if (client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) != 1 || client.getTopLevelWorldView() == null)
				{
					return names;
				}
				for (Player player : client.getTopLevelWorldView().players())
				{
					if (player != null && player.getName() != null)
					{
						names.add(player.getName());
					}
				}
				return names;
			}

			@Override
			public String localPlayerName()
			{
				Player local = client.getLocalPlayer();
				return local == null ? null : local.getName();
			}
		};
	}

	/** Level-ups to 92/99 and XP milestones of the local player's own skills (only what this client sees). */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == null)
		{
			return;
		}
		// The baseline is kept even while not tracking, so turning the upload on later does not report old levels.
		List<SkillEvent> events = skillMilestones.update(event.getSkill().getName(), event.getXp(), event.getLevel());
		if (events.isEmpty() || !isTracking())
		{
			return;
		}
		// Skill XP was already read above. The queue file is written off the client thread.
		String player = Text.sanitize(client.getLocalPlayer().getName());
		for (SkillEvent skillEvent : events)
		{
			skillEvent.setUid(UUID.randomUUID().toString());
			skillEvent.setPlayer(player);
			skillEvent.setRecordedAt(Instant.now().toString());
		}
		io.execute(() ->
		{
			try
			{
				for (SkillEvent skillEvent : events)
				{
					queue.add(skillEvent);
				}
				onRecorded.run();
			}
			catch (RuntimeException e)
			{
				log.warn("Saving a skill event failed", e);
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			skillMilestones.reset();
			caves.resetRun();
		}
	}

	private void saveTime(KillTime time)
	{
		if (time == null || !isTracking())
		{
			return;
		}
		addRaidDetails(time);
		time.setUid(UUID.randomUUID().toString());
		time.setPlayer(Text.sanitize(client.getLocalPlayer().getName()));
		time.setRecordedAt(Instant.now().toString());
		io.execute(() ->
		{
			try
			{
				queue.add(time);
				onRecorded.run();
			}
			catch (RuntimeException e)
			{
				log.warn("Saving a kill time failed", e);
			}
		});
	}

	// A raid's kill time gets the team size and raid level the raid tracker read from the party varbits.
	// Exact party sizes win over the bucket the completion message prints ("11-15 players").
	private void addRaidDetails(KillTime time)
	{
		try
		{
			TeamSize size = raidRuns.teamSizeFor(time.getSource());
			if (size == null && time.getTeamSize() == null && KillCount.isFracturedArchive(time.getSource()))
			{
				Integer slots = TfaTeamSize.count(client::getVarbitValue);
				size = slots == null ? null : TeamSize.exact(slots);
			}
			boolean exact = size != null && size.getMax() != null && size.getMin() == size.getMax();
			if (size != null && (time.getTeamSize() == null || exact))
			{
				time.setTeamSize(size.getMin());
				time.setTeamSizeMax(size.getMax());
				time.setTeamSizeLabel(size.getLabel());
			}
			time.setRaidLevel(raidRuns.raidLevelFor(time.getSource()));
		}
		catch (RuntimeException e)
		{
			log.warn("Raid details for a kill time failed", e);
		}
	}

	private void save(LootRecord record)
	{
		Map<Integer, Integer> merged = new LinkedHashMap<>();
		for (LootItem item : record.getItems())
		{
			merged.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
		}
		Map<Integer, String> names = new HashMap<>();
		Map<Integer, Boolean> tradeable = new HashMap<>();
		record.getItems().clear();
		for (Map.Entry<Integer, Integer> entry : merged.entrySet())
		{
			LootItem item = new LootItem(entry.getKey(), entry.getValue());
			item.setPrice((int) Math.min(Integer.MAX_VALUE, Math.max(0L, itemManager.getItemPrice(entry.getKey()))));
			record.getItems().add(item);
			names.put(entry.getKey(), itemManager.getItemComposition(entry.getKey()).getName());
			tradeable.put(entry.getKey(), itemManager.getItemComposition(entry.getKey()).isTradeable());
		}
		if (record.getContainerItemId() != null)
		{
			names.put(record.getContainerItemId(), itemManager.getItemComposition(record.getContainerItemId()).getName());
		}
		if (lastKillCount != null && lastKillCount.belongsTo(record.getSource(), client.getTickCount(), KillCount.windowFor(record.getSource(), KILL_COUNT_WINDOW_TICKS)))
		{
			record.setKillCount(lastKillCount.getCount());
			if (KillCount.isRaid(record.getSource()))
			{
				// One raid, one chest: the count must not carry over to a later chest.
				lastKillCount = null;
			}
		}
		if (record.getUid() == null || record.getUid().isEmpty())
		{
			record.setUid(UUID.randomUUID().toString());
		}
		record.setPlayer(Text.sanitize(client.getLocalPlayer().getName()));
		record.setRecordedAt(Instant.now().toString());
		// Item definitions have to be read here, on the client thread. The ledger and queue files do not.
		Integer parentId = null;
		List<int[]> received = new ArrayList<>();
		if (record.getType() != LootSourceType.PLAYER)
		{
			if (record.getContainerItemId() != null)
			{
				parentId = itemManager.canonicalize(record.getContainerItemId());
			}
			for (LootItem item : record.getItems())
			{
				if (isContainer(item.getId()))
				{
					received.add(new int[]{item.getId(), item.getQuantity()});
				}
			}
		}
		Integer parent = parentId;
		io.execute(() ->
		{
			try
			{
				if (parent != null)
				{
					record.setParentUid(containers.opened(parent, record.getAmount()));
				}
				for (int[] pair : received)
				{
					containers.received(record.getUid(), pair[0], pair[1]);
				}
				queue.add(record, names, tradeable);
				onRecorded.run();
			}
			catch (RuntimeException e)
			{
				log.warn("Saving loot failed", e);
			}
		});
		notifyLoot(record, names);
	}

	// Task progress is extra: a problem there must never stop loot tracking.
	private void notifyKillCount(KillCount parsed)
	{
		TaskListener listener = taskListener;
		if (listener == null || !isTracking())
		{
			return;
		}
		try
		{
			listener.onKillCount(parsed.getName(), parsed.getCount());
		}
		catch (RuntimeException e)
		{
			log.warn("Task progress update failed", e);
		}
	}

	private void notifyLoot(LootRecord record, Map<Integer, String> names)
	{
		TaskListener listener = taskListener;
		if (listener == null)
		{
			return;
		}
		try
		{
			Map<String, Integer> items = new LinkedHashMap<>();
			for (LootItem item : record.getItems())
			{
				String name = names.get(item.getId());
				if (name != null)
				{
					items.merge(name, item.getQuantity(), Integer::sum);
				}
			}
			listener.onLoot(record.getType().name(), record.getSource(), items);
		}
		catch (RuntimeException e)
		{
			log.warn("Task progress update failed", e);
		}
	}

	private boolean isContainer(int itemId)
	{
		String[] actions = itemManager.getItemComposition(itemId).getInventoryActions();
		return actions != null && Arrays.stream(actions).anyMatch(action -> action != null && OPEN_OPTIONS.contains(Text.removeTags(action).toLowerCase()));
	}

	private boolean isTracking()
	{
		if (!config.trackLoot() || !LootSender.isConfigured(config) || client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null
			|| paused.test(client.getLocalPlayer().getName()))
		{
			return false;
		}
		String expected = config.clanName().trim();
		if (expected.isEmpty())
		{
			return true;
		}
		ClanSettings settings = client.getClanSettings();
		return settings != null && settings.getName() != null && SnapshotDiff.key(expected).equals(SnapshotDiff.key(Text.sanitize(settings.getName())));
	}

	private void queueKillCount(KillCount parsed)
	{
		if (!isTracking() || parsed.getCount() < 0 || client.getLocalPlayer() == null)
		{
			return;
		}
		KillCountReport report = KillCountReport.of(localPlayerName(), parsed.getName(), parsed.getCount(), Instant.now());
		io.execute(() ->
		{
			try
			{
				queue.add(report);
				onRecorded.run();
			}
			catch (RuntimeException e)
			{
				log.warn("Saving a kill count failed", e);
			}
		});
	}

	private void recordCape(LootRecord record)
	{
		if (record == null || !isTracking())
		{
			return;
		}
		save(record);
	}

	private String localPlayerName()
	{
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			return "";
		}
		return Text.sanitize(client.getLocalPlayer().getName());
	}

	private int regionId()
	{
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getWorldLocation() == null)
		{
			return -1;
		}
		return client.getLocalPlayer().getWorldLocation().getRegionID();
	}

	private Map<Integer, Integer> inventory()
	{
		return contents(client.getItemContainer(InventoryID.INV));
	}

	private static Map<Integer, Integer> contents(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}
}
