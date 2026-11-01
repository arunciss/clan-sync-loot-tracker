package com.arc1s.clansync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanSyncPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanSyncPlugin.class);
		RuneLite.main(args);
	}
}
