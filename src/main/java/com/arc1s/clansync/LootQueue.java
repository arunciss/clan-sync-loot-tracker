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

	private final TextStore store;
	private final Gson gson;
	private LootBatch pending;

	public LootQueue(TextStore store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
		this.pending = load();
	}

	public synchronized void add(LootRecord record, Map<Integer, String> names, Map<Integer, Boolean> tradeable)
	{
		pending.getRecords().add(record);
		while (pending.getRecords().size() > MAX_RECORDS)
		{
			pending.getRecords().remove(0);
		}
		pending.getItemNames().putAll(names);
		pending.getItemTradeable().putAll(tradeable);
		save();
	}

	public synchronized void add(KillTime killTime)
	{
		pending.getKillTimes().add(killTime);
		while (pending.getKillTimes().size() > MAX_RECORDS)
		{
			pending.getKillTimes().remove(0);
		}
		save();
	}

	public synchronized void add(RaidRun run)
	{
		pending.getRaidRuns().add(run);
		while (pending.getRaidRuns().size() > MAX_RECORDS)
		{
			pending.getRaidRuns().remove(0);
		}
		save();
	}

	public synchronized int size()
	{
		return pending.getRecords().size() + pending.getKillTimes().size() + pending.getRaidRuns().size();
	}

	public synchronized LootBatch next(int maxRecords)
	{
		return next(maxRecords, Collections.emptySet());
	}

	/**
	 * Up to maxRecords of each kind, all about one character (the website checks that the personal code belongs to
	 * the character the data is about). Characters whose normalised name is in skip are left in the queue.
	 * The batch is empty when nothing else is waiting.
	 */
	public synchronized LootBatch next(int maxRecords, Set<String> skip)
	{
		LootBatch batch = new LootBatch();
		String player = firstPlayer(skip);
		if (player == null)
		{
			return batch;
		}
		batch.setRecords(take(pending.getRecords(), LootRecord::getPlayer, player, maxRecords));
		batch.setKillTimes(take(pending.getKillTimes(), KillTime::getPlayer, player, maxRecords));
		batch.setRaidRuns(take(pending.getRaidRuns(), RaidRun::getPlayer, player, maxRecords));
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

	private String firstPlayer(Set<String> skip)
	{
		List<String> players = new ArrayList<>();
		pending.getRecords().forEach(record -> players.add(record.getPlayer()));
		pending.getKillTimes().forEach(time -> players.add(time.getPlayer()));
		pending.getRaidRuns().forEach(run -> players.add(run.getPlayer()));
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

	public synchronized void remove(Set<String> uids)
	{
		pending.getRecords().removeIf(record -> uids.contains(record.getUid()));
		pending.getKillTimes().removeIf(killTime -> uids.contains(killTime.getUid()));
		pending.getRaidRuns().removeIf(run -> uids.contains(run.getUid()));
		if (pending.getRecords().isEmpty())
		{
			pending.getItemNames().clear();
			pending.getItemTradeable().clear();
		}
		save();
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

	private void save()
	{
		try
		{
			store.write(FILE, gson.toJson(pending));
		}
		catch (IOException e)
		{
			log.warn("Could not save the loot queue", e);
		}
	}
}
