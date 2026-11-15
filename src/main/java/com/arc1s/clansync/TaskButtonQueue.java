package com.arc1s.clansync;

import java.util.concurrent.Executor;

/**
 * State checks and task-button actions share one single-thread executor, separate from loot uploads.
 * A press is always accepted: if a check is already running, the press runs immediately after it.
 */
final class TaskButtonQueue
{
	private final Executor executor;

	TaskButtonQueue(Executor executor)
	{
		this.executor = executor;
	}

	/** @return true. A press is queued, never dropped. */
	boolean submit(Runnable work)
	{
		executor.execute(work);
		return true;
	}
}
