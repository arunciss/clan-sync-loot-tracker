package com.arc1s.clansync;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Deque;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A short "Task complete" popup at the top of the game screen, in the style of the collection log notification.
 * The border colour shows the difficulty (easy green, mid orange, hard red); wilderness tasks get a red marker.
 */
class TaskCompleteOverlay extends Overlay
{
	static final long SHOW_MILLIS = 7000;
	private static final int WIDTH = 240;
	private static final int HEIGHT = 62;
	private static final Color BACKGROUND = new Color(0x2B, 0x25, 0x1C, 235);
	private static final Color WILDERNESS = new Color(0xB71C1C);

	/** One popup: what to show and when it started (0 = not started yet). */
	static final class Popup
	{
		final String heading;
		final String title;
		final String detail;
		final Color color;
		final boolean wilderness;
		long startedAt;

		Popup(String heading, String title, String detail, Color color, boolean wilderness)
		{
			this.heading = heading;
			this.title = title;
			this.detail = detail;
			this.color = color;
			this.wilderness = wilderness;
		}
	}

	private final Deque<Popup> queue = new ArrayDeque<>();

	TaskCompleteOverlay()
	{
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	static Popup popupFor(ClanTask task, String heading)
	{
		String detail = ClanTask.difficultyName(task.getDifficulty()) + " task";
		if (task.getPoints() > 0)
		{
			detail += " - " + task.getPoints() + " points";
		}
		return new Popup(heading, task.displayTitle(), detail, ClanTask.color(task.getDifficulty()), task.isWilderness());
	}

	synchronized void show(Popup popup)
	{
		if (queue.size() < 5)
		{
			queue.addLast(popup);
		}
	}

	/** The popup to draw now, dropping the ones whose time is over. */
	synchronized Popup current(long now)
	{
		while (!queue.isEmpty())
		{
			Popup head = queue.peekFirst();
			if (head.startedAt == 0)
			{
				head.startedAt = now;
			}
			if (now - head.startedAt < SHOW_MILLIS)
			{
				return head;
			}
			queue.pollFirst();
		}
		return null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Popup popup = current(System.currentTimeMillis());
		if (popup == null)
		{
			return null;
		}
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setStroke(new BasicStroke(3));
		g.setColor(popup.color);
		g.drawRect(1, 1, WIDTH - 3, HEIGHT - 3);

		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bold = g.getFontMetrics();
		drawCentered(g, popup.heading, 18, popup.color, bold);

		g.setFont(FontManager.getRunescapeFont());
		FontMetrics plain = g.getFontMetrics();
		drawCentered(g, fit(popup.title, plain, WIDTH - 16), 36, Color.WHITE, plain);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics small = g.getFontMetrics();
		drawCentered(g, popup.detail, 52, new Color(0xC8C8C8), small);

		if (popup.wilderness)
		{
			String mark = "WILDY";
			int w = small.stringWidth(mark) + 8;
			g.setColor(WILDERNESS);
			g.fillRect(WIDTH - w - 6, 6, w, 14);
			g.setColor(Color.WHITE);
			g.drawString(mark, WIDTH - w - 2, 17);
		}
		return new Dimension(WIDTH, HEIGHT);
	}

	private static void drawCentered(Graphics2D g, String text, int y, Color color, FontMetrics metrics)
	{
		int x = (WIDTH - metrics.stringWidth(text)) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private static String fit(String text, FontMetrics metrics, int width)
	{
		if (metrics.stringWidth(text) <= width)
		{
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && metrics.stringWidth(cut + "...") > width)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}
}
