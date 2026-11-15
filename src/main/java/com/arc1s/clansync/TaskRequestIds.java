package com.arc1s.clansync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One id per action and task, reused when that action is retried after a network error or a busy website,
 * so the website can ignore the duplicate. A normal answer (including a refusal) retires the id.
 */
final class TaskRequestIds
{
	private final Map<String, String> pending = new ConcurrentHashMap<>();

	String id(String action, Integer assignmentId, Supplier<String> mint)
	{
		return pending.computeIfAbsent(key(action, assignmentId), k -> mint.get());
	}

	/** Keep the id for 429 and 5xx. Anything else, including success and a clear refusal, retires it. */
	void finish(String action, Integer assignmentId, int status)
	{
		if (status != 429 && status < 500)
		{
			pending.remove(key(action, assignmentId));
		}
	}

	boolean has(String action, Integer assignmentId)
	{
		return pending.containsKey(key(action, assignmentId));
	}

	void clear()
	{
		pending.clear();
	}

	static String key(String action, Integer assignmentId)
	{
		return action + ":" + assignmentId;
	}
}
