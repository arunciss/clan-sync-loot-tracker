package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.List;

/** Remembers (in the RuneLite config) which task ids already had a "Task complete" popup, so each pops only once. */
final class PopupMemory
{
	static final int MAX_IDS = 20;

	private PopupMemory()
	{
	}

	static boolean contains(String stored, int id)
	{
		return ids(stored).contains(String.valueOf(id));
	}

	static String add(String stored, int id)
	{
		List<String> ids = ids(stored);
		String value = String.valueOf(id);
		ids.remove(value);
		ids.add(value);
		while (ids.size() > MAX_IDS)
		{
			ids.remove(0);
		}
		return String.join(",", ids);
	}

	private static List<String> ids(String stored)
	{
		List<String> ids = new ArrayList<>();
		if (stored != null)
		{
			for (String part : stored.split(","))
			{
				if (part.trim().matches("[0-9]{1,10}"))
				{
					ids.add(part.trim());
				}
			}
		}
		return ids;
	}
}
