package com.arc1s.clansync;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the last roster snapshot so the next sync can tell what changed.
 */
@Slf4j
public class SnapshotStore
{
	static final String LATEST = "snapshot.json";
	static final String PENDING = "snapshot-pending.json";

	private final TextStore store;
	private final Gson gson;

	public SnapshotStore(TextStore store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
	}

	public ClanSnapshot loadLatest()
	{
		try
		{
			String json = store.read(LATEST);
			return json == null ? null : gson.fromJson(json, ClanSnapshot.class);
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Could not read previous clan snapshot", e);
			return null;
		}
	}

	public void saveLatest(ClanSnapshot snapshot) throws IOException
	{
		store.write(LATEST, gson.toJson(snapshot));
		store.delete(PENDING);
	}

	/**
	 * A snapshot the website has not accepted yet. It is never used as the diff baseline,
	 * so changes are not lost when an upload fails.
	 */
	public void savePending(ClanSnapshot snapshot) throws IOException
	{
		store.write(PENDING, gson.toJson(snapshot));
	}
}
