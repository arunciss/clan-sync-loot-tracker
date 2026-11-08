package com.arc1s.clansync;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LootQueue
{
	static final String FILE = "loot-queue.json";
	static final int MAX_RECORDS = 20000;
	static final int MAX_SKILL_EVENTS = 500;
	static final int SKILL_BATCH_SIZE = 50;

	private final TextStore store;
	private final Gson gson;
	private final Object writeLock = new Object();
	private LootBatch pending;
	/** Bumped under the monitor on every mutation so a slower older write cannot replace a newer file. */
	private int saveGeneration;

	public LootQueue(TextStore store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
		this.pending = load();
	}

	public void add(LootRecord record, Map<Integer, String> names, Map<Integer, Boolean> tradeable)
	{
		LootBatch copy;
		int generation;
		synchronized (this)
		{
			pending.getRecords().add(record);
			trim(pending.getRecords(), MAX_RECORDS);
			pending.getItemNames().putAll(names);
			pending.getItemTradeable().putAll(tradeable);
			generation = ++saveGeneration;
			copy = snapshot(pending);
		}
		write(generation, copy);
	}

	public void add(KillTime killTime)
	{
		LootBatch copy;
		int generation;
		synchronized (this)
		{
			pending.getKillTimes().add(killTime);
			trim(pending.getKillTimes(), MAX_RECORDS);
			generation = ++saveGeneration;
			copy = snapshot(pending);
		}
		write(generation, copy);
	}

	public void add(RaidRun run)
	{
		LootBatch copy;
		int generation;
		synchronized (this)
		{
			pending.getRaidRuns().add(run);
			trim(pending.getRaidRuns(), MAX_RECORDS);
			generation = ++saveGeneration;
			copy = snapshot(pending);
		}
		write(generation, copy);
	}

	public void add(SkillEvent event)
	{
		LootBatch copy;
		int generation;
		synchronized (this)
		{
			pending.getSkillEvents().add(event);
			trim(pending.getSkillEvents(), MAX_SKILL_EVENTS);
			generation = ++saveGeneration;
			copy = snapshot(pending);
		}
		write(generation, copy);
	}

	public synchronized int size()
	{
		return pending.getRecords().size() + pending.getKillTimes().size() + pending.getRaidRuns().size() + pending.getSkillEvents().size();
	}

	public synchronized LootBatch next(int maxRecords)
	{
		return next(maxRecords, Collections.emptySet(), true);
	}

	public synchronized LootBatch next(int maxRecords, Set<String> skip)
	{
		return next(maxRecords, skip, true);
	}

	/**
	 * Up to maxRecords of each kind, all about one character (the website checks that the personal code belongs to
	 * the character the data is about). Characters whose normalised name is in skip are left in the queue.
	 * The batch is empty when nothing else is waiting. Skill events go along with the same character's other data;
	 * a batch of only skill events is made only when skillsAlone is true (the site is known to accept them).
	 */
	public synchronized LootBatch next(int maxRecords, Set<String> skip, boolean skillsAlone)
	{
		LootBatch batch = new LootBatch();
		String player = firstPlayer(skip, skillsAlone);
		if (player == null)
		{
			return batch;
		}
		batch.setRecords(take(pending.getRecords(), LootRecord::getPlayer, player, maxRecords));
		batch.setKillTimes(take(pending.getKillTimes(), KillTime::getPlayer, player, maxRecords));
		batch.setRaidRuns(take(pending.getRaidRuns(), RaidRun::getPlayer, player, maxRecords));
		batch.setSkillEvents(take(pending.getSkillEvents(), SkillEvent::getPlayer, player, Math.min(maxRecords, SKILL_BATCH_SIZE)));
		for (LootRecord record : batch.getRecords())
		{
			for (LootItem item : record.getItems())
			{
				String name = pending.getItemNames().get(item.getId());
				if (name != null)
				{
					batch.getItemNames().put(item.getId(), name);
				}
				Boolean tradeable = pending.getItemTradeable().get(item.getId());
				if (tradeable != null)
				{
					batch.getItemTradeable().put(item.getId(), tradeable);
				}
			}
		}
		return batch;
	}

	private String firstPlayer(Set<String> skip, boolean skillsAlone)
	{
		List<String> players = new ArrayList<>();
		pending.getRecords().forEach(record -> players.add(record.getPlayer()));
		pending.getKillTimes().forEach(time -> players.add(time.getPlayer()));
		pending.getRaidRuns().forEach(run -> players.add(run.getPlayer()));
		if (skillsAlone)
		{
			pending.getSkillEvents().forEach(event -> players.add(event.getPlayer()));
		}
		for (String player : players)
		{
			String key = SnapshotDiff.key(player);
			if (!skip.contains(key))
			{
				return key;
			}
		}
		return null;
	}

	private static <T> List<T> take(List<T> items, Function<T, String> player, String key, int max)
	{
		List<T> out = new ArrayList<>();
		for (T item : items)
		{
			if (out.size() >= max)
			{
				break;
			}
			if (SnapshotDiff.key(player.apply(item)).equals(key))
			{
				out.add(item);
			}
		}
		return out;
	}

	public void remove(Set<String> uids)
	{
		LootBatch copy;
		int generation;
		synchronized (this)
		{
			pending.getRecords().removeIf(record -> uids.contains(record.getUid()));
			pending.getKillTimes().removeIf(killTime -> uids.contains(killTime.getUid()));
			pending.getRaidRuns().removeIf(run -> uids.contains(run.getUid()));
			pending.getSkillEvents().removeIf(event -> uids.contains(event.getUid()));
			if (pending.getRecords().isEmpty())
			{
				pending.getItemNames().clear();
				pending.getItemTradeable().clear();
			}
			generation = ++saveGeneration;
			copy = snapshot(pending);
		}
		write(generation, copy);
	}

	/** Drop the oldest entries in one pass. Removing index 0 in a loop copies the whole list each time. */
	private static void trim(List<?> items, int max)
	{
		int extra = items.size() - max;
		if (extra > 0)
		{
			items.subList(0, extra).clear();
		}
	}

	private static LootBatch snapshot(LootBatch src)
	{
		LootBatch copy = new LootBatch();
		copy.setSchemaVersion(src.getSchemaVersion());
		copy.setRecords(new ArrayList<>(src.getRecords()));
		copy.setKillTimes(new ArrayList<>(src.getKillTimes()));
		copy.setRaidRuns(new ArrayList<>(src.getRaidRuns()));
		copy.setSkillEvents(new ArrayList<>(src.getSkillEvents()));
		copy.setItemNames(new TreeMap<>(src.getItemNames()));
		copy.setItemTradeable(new TreeMap<>(src.getItemTradeable()));
		return copy;
	}

	private LootBatch load()
	{
		try
		{
			String json = store.read(FILE);
			if (json != null)
			{
				LootBatch loaded = gson.fromJson(json, LootBatch.class);
				if (loaded != null && loaded.getRecords() != null && loaded.getItemNames() != null)
				{
					if (loaded.getKillTimes() == null)
					{
						loaded.setKillTimes(new ArrayList<>());
					}
					if (loaded.getRaidRuns() == null)
					{
						loaded.setRaidRuns(new ArrayList<>());
					}
					if (loaded.getSkillEvents() == null)
					{
						loaded.setSkillEvents(new ArrayList<>());
					}
					if (loaded.getItemTradeable() == null)
					{
						loaded.setItemTradeable(new TreeMap<>());
					}
					return loaded;
				}
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Could not read the loot queue", e);
		}
		return new LootBatch();
	}

	/**
	 * JSON and the file write stay off the monitor. The game thread calls {@link #size()} every tick;
	 * holding the monitor across a multi-megabyte rewrite stalls the whole client until the write returns.
	 */
	private void write(int generation, LootBatch copy)
	{
		synchronized (writeLock)
		{
			synchronized (this)
			{
				if (generation != saveGeneration)
				{
					return;
				}
			}
			try
			{
				store.write(FILE, gson.toJson(copy));
			}
			catch (IOException e)
			{
				log.warn("Could not save the loot queue", e);
			}
		}
	}
}
