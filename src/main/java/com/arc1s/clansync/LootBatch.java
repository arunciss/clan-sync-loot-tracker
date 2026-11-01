package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;

@Data
public class LootBatch
{
	public static final int SCHEMA_VERSION = 2;

	private int schemaVersion = SCHEMA_VERSION;
	private List<LootRecord> records = new ArrayList<>();
	private List<KillTime> killTimes = new ArrayList<>();
	// Optional (added after schema 2): older sites ignore it, older queue files load it as empty.
	private List<RaidRun> raidRuns = new ArrayList<>();
	private Map<Integer, String> itemNames = new TreeMap<>();
	private Map<Integer, Boolean> itemTradeable = new TreeMap<>();
}
