package com.arc1s.clansync;

// What the raid tracker reads from the client, behind an interface so the logic can be unit tested.
public interface RaidContext
{
	int varbit(int id);

	int varp(int id);

	String varcStr(int id);
}
