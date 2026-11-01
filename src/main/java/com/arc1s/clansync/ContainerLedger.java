package com.arc1s.clansync;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Which loot drop each unopened container (lockbox, casket, ...) came from, oldest first, so an opening
 * can be attached to the drop that gave the container.
 */
@Slf4j
public class ContainerLedger
{
	static final String FILE = "containers.json";
	static final int MAX_PER_ITEM = 200;

	// Stored as a plain array so no generic type token (reflection) is needed to read it back.
	@Data
	static class Entry
	{
		private int itemId;
		private String[] drops;
	}

	private final TextStore store;
	private final Gson gson;
	private final Map<Integer, List<String>> drops;

	public ContainerLedger(TextStore store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
		this.drops = load();
	}

	public synchronized void received(String dropUid, int containerItemId, int quantity)
	{
		List<String> queue = drops.computeIfAbsent(containerItemId, id -> new ArrayList<>());
		for (int i = 0; i < Math.min(quantity, MAX_PER_ITEM); i++)
		{
			queue.add(dropUid);
		}
		while (queue.size() > MAX_PER_ITEM)
		{
			queue.remove(0);
		}
		save();
	}

	public synchronized String opened(int containerItemId, int used)
	{
		List<String> queue = drops.get(containerItemId);
		if (queue == null || queue.isEmpty())
		{
			return null;
		}
		String dropUid = queue.get(0);
		for (int i = 0; i < Math.max(1, used) && !queue.isEmpty(); i++)
		{
			queue.remove(0);
		}
		if (queue.isEmpty())
		{
			drops.remove(containerItemId);
		}
		save();
		return dropUid;
	}

	public synchronized int waiting(int containerItemId)
	{
		List<String> queue = drops.get(containerItemId);
		return queue == null ? 0 : queue.size();
	}

	private Map<Integer, List<String>> load()
	{
		Map<Integer, List<String>> loaded = new HashMap<>();
		try
		{
			String json = store.read(FILE);
			Entry[] entries = json == null ? null : gson.fromJson(json, Entry[].class);
			if (entries != null)
			{
				for (Entry entry : entries)
				{
					if (entry != null && entry.getDrops() != null && entry.getDrops().length > 0)
					{
						loaded.put(entry.getItemId(), new ArrayList<>(Arrays.asList(entry.getDrops())));
					}
				}
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Could not read the container ledger", e);
		}
		return loaded;
	}

	private void save()
	{
		List<Entry> entries = new ArrayList<>();
		drops.forEach((itemId, queue) ->
		{
			Entry entry = new Entry();
			entry.setItemId(itemId);
			entry.setDrops(queue.toArray(new String[0]));
			entries.add(entry);
		});
		try
		{
			store.write(FILE, gson.toJson(entries.toArray(new Entry[0])));
		}
		catch (IOException e)
		{
			log.warn("Could not save the container ledger", e);
		}
	}
}
