package com.arc1s.clansync;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory TextStore for tests, so no test touches the disk.
 */
class MemoryStore implements TextStore
{
	final Map<String, String> files = new HashMap<>();

	@Override
	public String read(String name)
	{
		return files.get(name);
	}

	@Override
	public void write(String name, String text)
	{
		files.put(name, text);
	}

	@Override
	public void delete(String name)
	{
		files.remove(name);
	}
}
