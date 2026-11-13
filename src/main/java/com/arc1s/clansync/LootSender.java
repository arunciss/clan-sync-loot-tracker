package com.arc1s.clansync;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LootSender
{
	private static final int BATCH_SIZE = 200;

	private final LootQueue queue;
	private final SiteUploader uploader;
	private final ClanSyncConfig config;
	private final AtomicBoolean sending = new AtomicBoolean();
	private final AtomicInteger sessionDrops = new AtomicInteger();
	private final AtomicInteger sessionKills = new AtomicInteger();
	private final AtomicInteger sessionTimes = new AtomicInteger();
	private final AtomicInteger sessionSkills = new AtomicInteger();
	// The website address that confirmed it accepts skill events this session (null: not known yet).
	private volatile String skillsEndpoint;
	/** The website address that confirmed it accepts kill count reports this session (null: not known yet). */
	private volatile String killCountsEndpoint;
	private volatile boolean failing;
	private volatile String lastError;
	private final AuthGate gate = new AuthGate();
	static final String NOT_CONFIGURED = "Enter the website API URL and your personal code in the plugin settings."
		+ " Nothing is recorded or sent until both are filled in.";

	public LootSender(LootQueue queue, SiteUploader uploader, ClanSyncConfig config)
	{
		this.queue = queue;
		this.uploader = uploader;
		this.config = config;
	}

	public boolean isFailing()
	{
		return failing;
	}

	/**
	 * True while the website has refused the current personal code (wrong, revoked, or no longer in the clan).
	 * Nothing is sent and no new loot is recorded until the code or website URL is changed.
	 */
	public boolean isRejected()
	{
		return gate.isRejected(config.endpoint(), config.memberToken());
	}

	/**
	 * True when nothing may be recorded or sent for this character: the code was refused, or the website said the
	 * code belongs to another character.
	 */
	public boolean isRejected(String player)
	{
		return isRejected() || gate.isNameRejected(config.endpoint(), config.memberToken(), player);
	}

	/** Both the website address and the personal code are filled in; until then nothing is recorded or sent. */
	static boolean isConfigured(ClanSyncConfig config)
	{
		return !config.endpoint().trim().isEmpty() && !config.memberToken().trim().isEmpty();
	}

	public String describe()
	{
		String sent = "sent " + sessionDrops.get() + " drops (" + sessionKills.get() + " kills), " + sessionTimes.get() + " kill times"
			+ (sessionSkills.get() > 0 ? ", " + sessionSkills.get() + " level/XP milestones" : "") + " this session";
		int waiting = queue.size();
		if (!isConfigured(config))
		{
			return NOT_CONFIGURED;
		}
		if (isRejected())
		{
			return "Paused: the website refused your personal code (wrong, revoked, or you are no longer in the clan). "
				+ "Recording and sending are stopped. Enter a new code to resume. " + waiting + " waiting. " + lastError;
		}
		Set<String> names = gate.rejectedNames(config.endpoint(), config.memberToken());
		if (!names.isEmpty())
		{
			return "Paused for " + String.join(", ", names) + ": your personal code belongs to another character."
				+ " Nothing is recorded or sent for that character. " + sent + ". " + waiting + " waiting.";
		}
		if (failing)
		{
			return sent + ". " + waiting + " waiting, retry in 5 min: " + lastError;
		}
		return waiting > 0 ? sent + ". " + waiting + " waiting" : sent;
	}

	public void sendAll()
	{
		String endpoint = config.endpoint().trim();
		// Turning the upload off stops sending too; anything queued waits until it is turned back on.
		if (!config.trackLoot() || !isConfigured(config) || queue.size() == 0 || isRejected() || !sending.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			String url = SiteUploader.resolve(endpoint, SiteUploader.LOOT_SEGMENT);
			if (url == null)
			{
				throw new IOException("Invalid website URL: " + endpoint);
			}
			while (true)
			{
				// Older websites reject a batch with nothing they know, so skill events and kill counts only travel
				// alone once this site has said it accepts that kind.
				boolean skillsAlone = endpoint.equals(skillsEndpoint);
				boolean countsAlone = endpoint.equals(killCountsEndpoint);
				LootBatch batch = queue.next(BATCH_SIZE, gate.rejectedNames(endpoint, config.memberToken()), skillsAlone, countsAlone);
				boolean core = !batch.getRecords().isEmpty() || !batch.getKillTimes().isEmpty() || !batch.getRaidRuns().isEmpty();
				boolean skills = skillsAlone && batch.getSkillEvents() != null && !batch.getSkillEvents().isEmpty();
				boolean counts = countsAlone && batch.getKillCounts() != null && !batch.getKillCounts().isEmpty();
				if (!core && !skills && !counts)
				{
					break;
				}
				SiteUploader.Reply reply;
				try
				{
					reply = uploader.post(url, config.memberToken(), batch);
				}
				catch (SiteUploader.RejectedException e)
				{
					if (!e.isNameMismatch())
					{
						throw e;
					}
					// The code is fine but is not this character's: keep its data queued, stop for it and go on with the rest.
					String player = batchPlayer(batch);
					gate.rejectName(endpoint, config.memberToken(), player);
					lastError = e.getMessage();
					log.warn("Loot upload for {} refused by the website: {}", player, e.getMessage());
					continue;
				}
				Set<String> uids = new HashSet<>();
				batch.getRecords().forEach(record ->
				{
					uids.add(record.getUid());
					sessionKills.addAndGet(record.getAmount());
				});
				batch.getKillTimes().forEach(killTime -> uids.add(killTime.getUid()));
				batch.getRaidRuns().forEach(run ->
				{
					uids.add(run.getUid());
					log.info("Raid run sent: {} KC {}, {} rooms, team size {}, {} party names ({}), points {}/{}",
						run.getRaid(), run.getKillCount(), run.getRooms() == null ? 0 : run.getRooms().size(), run.getTeamSize(),
						run.getTeam() == null ? 0 : run.getTeam().size(), run.getTeamSource(), run.getPersonalPoints(), run.getTotalPoints());
				});
				if (reply.skillEvents)
				{
					skillsEndpoint = endpoint;
					batch.getSkillEvents().forEach(event -> uids.add(event.getUid()));
					sessionSkills.addAndGet(batch.getSkillEvents().size());
				}
				if (reply.killCounts && batch.getKillCounts() != null)
				{
					killCountsEndpoint = endpoint;
					batch.getKillCounts().forEach(report -> uids.add(report.getUid()));
				}
				sessionDrops.addAndGet(batch.getRecords().size());
				sessionTimes.addAndGet(batch.getKillTimes().size());
				queue.remove(uids);
			}
			failing = false;
		}
		catch (SiteUploader.RejectedException e)
		{
			// Retrying with the same code cannot succeed: stop until the user enters a new code or URL.
			// Already queued records are kept so a new valid code can still deliver them.
			gate.reject(endpoint, config.memberToken());
			failing = true;
			lastError = e.getMessage();
			log.warn("Loot upload refused by the website: {}", e.getMessage());
		}
		catch (IOException e)
		{
			failing = true;
			lastError = e.getMessage();
			log.warn("Loot upload failed", e);
		}
		finally
		{
			sending.set(false);
		}
	}

	private static String batchPlayer(LootBatch batch)
	{
		if (!batch.getRecords().isEmpty())
		{
			return batch.getRecords().get(0).getPlayer();
		}
		if (!batch.getKillTimes().isEmpty())
		{
			return batch.getKillTimes().get(0).getPlayer();
		}
		if (!batch.getRaidRuns().isEmpty())
		{
			return batch.getRaidRuns().get(0).getPlayer();
		}
		if (batch.getSkillEvents() != null && !batch.getSkillEvents().isEmpty())
		{
			return batch.getSkillEvents().get(0).getPlayer();
		}
		return batch.getKillCounts().get(0).getPlayer();
	}
}
