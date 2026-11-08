package com.arc1s.clansync;

import java.util.Collections;
import java.util.List;

// What the raid tracker reads from the client, behind an interface so the logic can be unit tested.
public interface RaidContext
{
	int varbit(int id);

	int varp(int id);

	String varcStr(int id);

	// Chambers of Xeric raid party list as the raids side panel shows it (raw entries, tags allowed).
	default List<String> coxPartyNames()
	{
		return Collections.emptyList();
	}

	// Names of the players in the current scene; asked only during a Chambers of Xeric raid (a private instance).
	default List<String> raidPlayerNames()
	{
		return Collections.emptyList();
	}

	default String localPlayerName()
	{
		return null;
	}
}
