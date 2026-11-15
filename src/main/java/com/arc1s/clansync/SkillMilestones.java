package com.arc1s.clansync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns skill XP updates into level-up (92, 99) and XP milestone (15m, 20m, ... 200m) events.
 * The first value seen for a skill after login or a world hop is only a baseline, so nothing already reached is
 * reported again; a jump larger than a normal XP drop is treated as a new baseline too.
 */
public class SkillMilestones
{
	static final int LEVEL_92_XP = 6_517_253;
	static final int LEVEL_99_XP = 13_034_431;
	static final int MILESTONE_STEP = 5_000_000;
	static final int FIRST_MILESTONE = 15_000_000;
	static final int MAX_XP = 200_000_000;
	/** Larger single XP changes are not a normal XP drop (e.g. stats loading late); they only reset the baseline. */
	static final int MAX_JUMP = 2_000_000;

	private final Map<String, Integer> baseline = new HashMap<>();

	/** Forget all baselines (logout, world hop, lost connection). */
	public void reset()
	{
		baseline.clear();
	}

	/** Events reached by this update; uid, player and time are left for the caller to fill in. */
	public List<SkillEvent> update(String skill, int xp, int level)
	{
		List<SkillEvent> events = new ArrayList<>();
		if (skill == null || skill.isEmpty() || "Overall".equalsIgnoreCase(skill) || xp < 0)
		{
			return events;
		}
		Integer before = baseline.put(skill, xp);
		if (before == null || xp <= before || xp - before > MAX_JUMP)
		{
			return events;
		}
		if (before < LEVEL_92_XP && xp >= LEVEL_92_XP)
		{
			events.add(level(skill, 92, xp));
		}
		if (before < LEVEL_99_XP && xp >= LEVEL_99_XP)
		{
			events.add(level(skill, 99, xp));
		}
		for (int milestone = FIRST_MILESTONE; milestone > 0 && milestone <= MAX_XP; milestone += MILESTONE_STEP)
		{
			if (before < milestone && xp >= milestone)
			{
				SkillEvent event = new SkillEvent();
				event.setSkill(skill);
				event.setType(SkillEvent.XP_MILESTONE);
				event.setLevel(level);
				event.setXp(xp);
				event.setMilestoneXp(milestone);
				events.add(event);
			}
		}
		return events;
	}

	private static SkillEvent level(String skill, int level, int xp)
	{
		SkillEvent event = new SkillEvent();
		event.setSkill(skill);
		event.setType(SkillEvent.LEVEL);
		event.setLevel(level);
		event.setXp(xp);
		return event;
	}
}
