package com.arc1s.clansync;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkillMilestonesTest
{
	@Test
	public void firstValueIsOnlyABaseline()
	{
		SkillMilestones skills = new SkillMilestones();
		assertTrue(skills.update("Slayer", 13_100_000, 99).isEmpty());
		assertTrue(skills.update("Slayer", 13_200_000, 99).isEmpty());
	}

	@Test
	public void reportsLevel92And99()
	{
		SkillMilestones skills = new SkillMilestones();
		skills.update("Magic", 6_517_000, 91);
		List<SkillEvent> events = skills.update("Magic", 6_517_300, 92);
		assertEquals(1, events.size());
		assertEquals(SkillEvent.LEVEL, events.get(0).getType());
		assertEquals(92, events.get(0).getLevel());
		assertEquals("Magic", events.get(0).getSkill());
		assertNull(events.get(0).getMilestoneXp());

		skills.update("Ranged", 13_034_000, 98);
		events = skills.update("Ranged", 13_035_000, 99);
		assertEquals(1, events.size());
		assertEquals(99, events.get(0).getLevel());
		assertTrue(skills.update("Ranged", 13_036_000, 99).isEmpty());
	}

	@Test
	public void reportsEveryFiveMillionFrom15m()
	{
		SkillMilestones skills = new SkillMilestones();
		skills.update("Attack", 9_999_000, 95);
		assertTrue(skills.update("Attack", 10_001_000, 96).isEmpty());
		skills.update("Attack", 14_999_990, 99);
		List<SkillEvent> events = skills.update("Attack", 15_000_010, 99);
		assertEquals(1, events.size());
		assertEquals(SkillEvent.XP_MILESTONE, events.get(0).getType());
		assertEquals(Integer.valueOf(15_000_000), events.get(0).getMilestoneXp());
		assertTrue(skills.update("Attack", 17_000_000, 99).isEmpty());

		skills.update("Hitpoints", 199_999_000, 99);
		events = skills.update("Hitpoints", 200_000_000, 99);
		assertEquals(Integer.valueOf(200_000_000), events.get(0).getMilestoneXp());
	}

	@Test
	public void ignoresHugeJumpsOverallAndReset()
	{
		SkillMilestones skills = new SkillMilestones();
		skills.update("Mining", 0, 1);
		assertTrue(skills.update("Mining", 13_100_000, 99).isEmpty());
		skills.update("Overall", 1, 1);
		assertTrue(skills.update("Overall", 15_000_000, 1).isEmpty());
		skills.update("Fishing", 6_500_000, 91);
		skills.reset();
		assertTrue(skills.update("Fishing", 6_600_000, 92).isEmpty());
	}

	@Test
	public void skillEventsWaitForSitesThatAcceptThem()
	{
		MemoryStore files = new MemoryStore();
		LootQueue queue = new LootQueue(files, new Gson());
		SkillEvent event = new SkillEvent();
		event.setUid("s1");
		event.setPlayer("Some Name");
		event.setSkill("Slayer");
		event.setType(SkillEvent.LEVEL);
		event.setLevel(99);
		event.setXp(13_034_431);
		queue.add(event);
		assertEquals(1, queue.size());
		// Alone they are not sent until the site has confirmed it accepts them.
		assertTrue(queue.next(200, Collections.emptySet(), false).getSkillEvents().isEmpty());
		assertEquals(1, queue.next(200, Collections.emptySet(), true).getSkillEvents().size());

		KillTime time = new KillTime();
		time.setUid("t1");
		time.setPlayer("Some Name");
		queue.add(time);
		LootBatch batch = queue.next(200, Collections.emptySet(), false);
		assertEquals(1, batch.getKillTimes().size());
		assertEquals(1, batch.getSkillEvents().size());

		LootQueue reloaded = new LootQueue(files, new Gson());
		assertEquals(2, reloaded.size());
		reloaded.remove(new HashSet<>(Collections.singletonList("t1")));
		assertEquals(1, reloaded.size());
		reloaded.remove(Collections.singleton("s1"));
		assertEquals(0, reloaded.size());
	}
}
