package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class ContainerOpening
{
	private final int itemId;
	private final String name;
	private final Map<Integer, Integer> before;
	private final int startTick;
	private LootRecord result;
	private int resultTick;
	private boolean claimed;

	public ContainerOpening(int itemId, String name, Map<Integer, Integer> before, int startTick)
	{
		this.itemId = itemId;
		this.name = name;
		this.before = new HashMap<>(before);
		this.startTick = startTick;
	}

	public boolean inventoryChanged(Map<Integer, Integer> after, int tick)
	{
		if (claimed || result != null)
		{
			return false;
		}
		int used = before.getOrDefault(itemId, 0) - after.getOrDefault(itemId, 0);
		List<LootItem> gained = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : after.entrySet())
		{
			int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
			if (entry.getKey() != itemId && delta > 0)
			{
				gained.add(new LootItem(entry.getKey(), delta));
			}
		}
		if (used <= 0 || gained.isEmpty())
		{
			return false;
		}
		result = new LootRecord();
		result.setType(LootSourceType.CONTAINER);
		result.setSource(name);
		result.setAmount(used);
		result.setContainerItemId(itemId);
		result.setItems(gained);
		resultTick = tick;
		return true;
	}

	public void claim()
	{
		claimed = true;
	}

	public boolean isExpired(int tick, int windowTicks)
	{
		return claimed || tick - (result == null ? startTick : resultTick) > windowTicks;
	}
}
