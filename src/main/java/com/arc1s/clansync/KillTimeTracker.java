package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.List;

public class KillTimeTracker
{
	static final int WINDOW_TICKS = 5;

	private final List<DurationMessage> pending = new ArrayList<>();
	private KillCount unusedKillCount;

	public KillTime onKillCount(KillCount killCount)
	{
		pending.removeIf(message -> killCount.getTick() - message.getTick() > WINDOW_TICKS);
		DurationMessage best = null;
		for (DurationMessage message : pending)
		{
			if (best == null || message.getPriority() >= best.getPriority())
			{
				best = message;
			}
		}
		if (best == null)
		{
			unusedKillCount = killCount;
			return null;
		}
		pending.clear();
		unusedKillCount = null;
		return combine(killCount, best);
	}

	public KillTime onDuration(DurationMessage message)
	{
		if (unusedKillCount != null && message.getTick() - unusedKillCount.getTick() <= WINDOW_TICKS)
		{
			KillCount killCount = unusedKillCount;
			unusedKillCount = null;
			return combine(killCount, message);
		}
		pending.add(message);
		return null;
	}

	private static KillTime combine(KillCount killCount, DurationMessage message)
	{
		KillTime time = new KillTime();
		time.setSource(killCount.getName());
		time.setKillCount(killCount.getCount());
		time.setTimeMs(message.getTimeMs());
		time.setPersonalBestMs(message.getPersonalBestMs());
		time.setNewPersonalBest(message.isNewPersonalBest());
		TeamSize size = message.getTeamSize();
		if (size != null)
		{
			time.setTeamSize(size.getMin());
			time.setTeamSizeMax(size.getMax());
			time.setTeamSizeLabel(size.getLabel());
		}
		time.setTimePrecise(message.isPrecise());
		return time;
	}
}
