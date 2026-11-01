package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

/**
 * The in-game collection log layout (tabs, pages and their items), read from the game cache so the
 * website can present logs the same way the game does.
 */
@Data
public class CollectionLogLayout
{
	private static final int TABS_ENUM = 2102;
	private static final int TAB_NAME_PARAM = 682;
	private static final int TAB_PAGES_ENUM_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_ENUM_PARAM = 690;
	private static final int ITEM_REPLACEMENTS_ENUM = 3721;

	private List<Tab> tabs = new ArrayList<>();

	@Data
	public static class Tab
	{
		private String name;
		private List<Page> pages = new ArrayList<>();
	}

	@Data
	public static class Page
	{
		private String name;
		private List<Integer> items = new ArrayList<>();
	}

	/**
	 * Must run on the client thread.
	 */
	public static CollectionLogLayout read(Client client)
	{
		// Some items are stored under a different id than the one a log actually records (satchels, Flamtaer bag).
		Map<Integer, Integer> replacements = new HashMap<>();
		EnumComposition replacementEnum = client.getEnum(ITEM_REPLACEMENTS_ENUM);
		for (int key : replacementEnum.getKeys())
		{
			replacements.put(key, replacementEnum.getIntValue(key));
		}

		CollectionLogLayout layout = new CollectionLogLayout();
		for (int tabStructId : orderedValues(client.getEnum(TABS_ENUM)))
		{
			StructComposition tabStruct = client.getStructComposition(tabStructId);
			Tab tab = new Tab();
			tab.setName(tabStruct.getStringValue(TAB_NAME_PARAM));
			for (int pageStructId : orderedValues(client.getEnum(tabStruct.getIntValue(TAB_PAGES_ENUM_PARAM))))
			{
				StructComposition pageStruct = client.getStructComposition(pageStructId);
				Page page = new Page();
				page.setName(pageStruct.getStringValue(PAGE_NAME_PARAM));
				for (int itemId : orderedValues(client.getEnum(pageStruct.getIntValue(PAGE_ITEMS_ENUM_PARAM))))
				{
					page.getItems().add(replacements.getOrDefault(itemId, itemId));
				}
				tab.getPages().add(page);
			}
			layout.getTabs().add(tab);
		}
		return layout;
	}

	private static int[] orderedValues(EnumComposition composition)
	{
		int[] keys = composition.getKeys().clone();
		Arrays.sort(keys);
		int[] values = new int[keys.length];
		for (int i = 0; i < keys.length; i++)
		{
			values[i] = composition.getIntValue(keys[i]);
		}
		return values;
	}
}
