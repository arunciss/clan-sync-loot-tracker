package com.arc1s.clansync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * "Clan tasks" part of the side panel: a card with the current task (title, boss, difficulty, points, progress)
 * and the Done / Skip-Re-roll buttons, or a Take a new task button when there is no task. (A free green-log skip,
 * if the clan offers one, is not done from the plugin.)
 * Shown only when the website supports clan tasks and the option is on. Must be used on the Swing thread.
 */
class ClanTasksSection extends JPanel
{
	interface Actions
	{
		void done(ClanTask task);

		/** Skip the task for one re-roll (the normal skip). */
		void reroll(ClanTask task, ClanTaskState state);

		void take();

		void refresh();
	}

	static final Color POINTS_COLOR = new Color(0xFFD54F);
	static final Color WILDY_COLOR = new Color(0xFF5252);
	private static final int BUTTON_HEIGHT = 26;
	/** Wrapping width (CSS units) inside the card: the section and card padding take about 30 px. */
	private static final int CARD_TEXT_WIDTH = ClanSyncPanel.TEXT_WIDTH - 22;

	private final JPanel card = new JPanel(new GridBagLayout());
	private final JLabel titleLabel = ClanSyncPanel.wrappingLabel();
	private final JLabel bossLabel = ClanSyncPanel.wrappingLabel();
	private final JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
	private final JLabel difficultyBadge = badge();
	private final JLabel wildyBadge = badge();
	private final JLabel pointsBadge = badge();
	private final JLabel needsLabel = ClanSyncPanel.wrappingLabel();
	private final JProgressBar progress = new JProgressBar();
	private final JLabel progressNote = ClanSyncPanel.wrappingLabel();
	private final JLabel rerollsLabel = new JLabel();
	private final JLabel totalLabel = new JLabel("", SwingConstants.RIGHT);
	private final JLabel messageLabel = ClanSyncPanel.wrappingLabel();
	private final JButton doneButton = button("Done", "I finished this task: add the points and give me a new task");
	private final JButton rerollButton = button("Skip / Re-roll", null);
	private final JButton takeButton = button("Take a new task", "Get your next clan task now");
	private final JButton refreshButton = new JButton("Refresh");
	private ClanTask task;
	private ClanTaskState state;
	private boolean busy;

	ClanTasksSection(Actions actions)
	{
		setLayout(new GridBagLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 6, 8, 6));

		JLabel heading = new JLabel("Clan tasks");
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(Color.WHITE);
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(heading, BorderLayout.CENTER);
		refreshButton.setFocusable(false);
		refreshButton.setFont(FontManager.getRunescapeSmallFont());
		refreshButton.setMargin(new Insets(0, 6, 0, 6));
		refreshButton.setToolTipText("Check the website for your current task now");
		refreshButton.addActionListener(e -> actions.refresh());
		header.add(refreshButton, BorderLayout.EAST);

		card.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		bossLabel.setFont(FontManager.getRunescapeSmallFont());
		needsLabel.setFont(FontManager.getRunescapeSmallFont());
		needsLabel.setForeground(ClanTask.MID_COLOR);
		badges.setOpaque(false);
		badges.add(difficultyBadge);
		badges.add(Box.createHorizontalStrut(4));
		badges.add(wildyBadge);
		badges.add(Box.createHorizontalStrut(4));
		badges.add(pointsBadge);
		wildyBadge.setText("Wilderness");
		wildyBadge.setForeground(WILDY_COLOR);
		wildyBadge.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wildyBadge.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(WILDY_COLOR), new EmptyBorder(0, 4, 0, 4)));
		pointsBadge.setForeground(POINTS_COLOR);
		pointsBadge.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progress.setStringPainted(true);
		progress.setFont(FontManager.getRunescapeSmallFont());
		progress.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progress.setForeground(ClanTask.MID_COLOR);
		progress.setPreferredSize(new Dimension(10, 18));
		progressNote.setFont(FontManager.getRunescapeSmallFont());

		ClanSyncPanel.stack(card, titleLabel, 3);
		ClanSyncPanel.stack(card, bossLabel, 6);
		ClanSyncPanel.stack(card, badges, 6);
		ClanSyncPanel.stack(card, needsLabel, 6);
		ClanSyncPanel.stack(card, progress, 2);
		ClanSyncPanel.stack(card, progressNote, 0);

		JPanel stats = new JPanel(new GridLayout(1, 2, 4, 0));
		stats.setOpaque(false);
		rerollsLabel.setFont(FontManager.getRunescapeSmallFont());
		rerollsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalLabel.setFont(FontManager.getRunescapeSmallFont());
		totalLabel.setForeground(POINTS_COLOR);
		stats.add(rerollsLabel);
		stats.add(totalLabel);

		messageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		doneButton.addActionListener(e -> {
			if (task != null)
			{
				actions.done(task);
			}
		});
		rerollButton.addActionListener(e -> {
			if (task != null)
			{
				actions.reroll(task, state);
			}
		});
		takeButton.addActionListener(e -> actions.take());

		ClanSyncPanel.stack(this, header, 6);
		ClanSyncPanel.stack(this, card, 6);
		ClanSyncPanel.stack(this, stats, 8);
		ClanSyncPanel.stack(this, doneButton, 4);
		ClanSyncPanel.stack(this, rerollButton, 4);
		ClanSyncPanel.stack(this, takeButton, 4);
		ClanSyncPanel.stack(this, messageLabel, 0);
		showState(null);
	}

	private static JLabel badge()
	{
		JLabel label = new JLabel();
		label.setOpaque(true);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(1, 5, 1, 5));
		return label;
	}

	private static JButton button(String text, String tooltip)
	{
		JButton button = new JButton(text);
		button.setFocusable(false);
		button.setToolTipText(tooltip);
		button.setPreferredSize(new Dimension(10, BUTTON_HEIGHT));
		return button;
	}

	/** Text on the re-roll button: how many re-rolls are left. */
	static String rerollButtonText(int rerolls)
	{
		return rerolls > 0 ? "Skip / Re-roll (" + rerolls + " left)" : "Skip / Re-roll (none left)";
	}

	static String rerollsText(int rerolls)
	{
		return "Re-rolls left: " + Math.max(0, rerolls);
	}

	/** Dark text on the light difficulty colours, white on red. */
	private static Color badgeText(String difficulty)
	{
		return "hard".equals(difficulty) ? Color.WHITE : Color.BLACK;
	}

	void showState(ClanTaskState newState)
	{
		state = newState;
		task = state == null ? null : state.getTask();
		boolean joined = state != null && state.isAvailable() && state.isJoined();
		if (task != null)
		{
			Color color = ClanTask.color(task.getDifficulty());
			card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, color), new EmptyBorder(7, 8, 8, 8)));
			ClanSyncPanel.setWrapped(titleLabel, task.displayTitle(), CARD_TEXT_WIDTH);
			ClanSyncPanel.setWrapped(bossLabel, "Boss: " + task.displayBoss(), CARD_TEXT_WIDTH);
			difficultyBadge.setText(ClanTask.difficultyName(task.getDifficulty()));
			difficultyBadge.setBackground(color);
			difficultyBadge.setForeground(badgeText(task.getDifficulty()));
			wildyBadge.setVisible(task.isWilderness());
			pointsBadge.setText(task.getPoints() + " pts");
			String needs = task.getRequirement() == null ? "" : SiteUploader.ascii(task.getRequirement()).trim();
			if (task.isSlayerTaskRequired())
			{
				needs = needs.isEmpty() ? "Only while on a Slayer task" : needs + "; only while on a Slayer task";
			}
			else if (!needs.isEmpty())
			{
				needs = "Needs: " + needs;
			}
			ClanSyncPanel.setWrapped(needsLabel, needs, CARD_TEXT_WIDTH);
			needsLabel.setVisible(!needs.isEmpty());
			String text = task.progressText();
			progress.setVisible(text != null);
			if (text != null)
			{
				int target = task.getProgress().getTarget();
				int current = Math.min(task.getProgress().getCurrent(), target);
				progress.setMaximum(target);
				progress.setValue(current);
				progress.setString(task.isReached() ? text + " - done!" : text + " (" + (current * 100 / target) + "%)");
				progress.setForeground(task.isReached() ? ClanTask.EASY_COLOR : color);
			}
			String note = text == null ? "Progress is not tracked for this task." : task.isReached() ? "Target reached! Press Done." : "";
			ClanSyncPanel.setWrapped(progressNote, note, CARD_TEXT_WIDTH);
			progressNote.setForeground(task.isReached() ? ClanTask.EASY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			progressNote.setVisible(!note.isEmpty());
			titleLabel.setVisible(true);
			bossLabel.setVisible(true);
			badges.setVisible(true);
		}
		else
		{
			card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, Color.GRAY), new EmptyBorder(7, 8, 8, 8)));
			ClanSyncPanel.setWrapped(titleLabel, state == null ? "Loading..." : joined ? "No active task" : "No task", CARD_TEXT_WIDTH);
			String hint = state == null ? "Checking the website." : SiteUploader.ascii(state.getMessage());
			ClanSyncPanel.setWrapped(progressNote, hint, CARD_TEXT_WIDTH);
			progressNote.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			progressNote.setVisible(!hint.isEmpty());
			bossLabel.setVisible(false);
			badges.setVisible(false);
			needsLabel.setVisible(false);
			progress.setVisible(false);
		}
		int rerolls = state == null ? 0 : state.getRerolls();
		rerollsLabel.setText(rerollsText(rerolls));
		totalLabel.setText(state == null ? "" : state.getPoints() + " pts total");
		rerollsLabel.getParent().setVisible(joined);
		rerollButton.setText(rerollButtonText(rerolls));
		rerollButton.setToolTipText(rerolls > 0
			? "I do not want this task: skip it for one re-roll and get a new task"
			: "No re-rolls left" + (state != null && state.getNextRerollIn() > 0
			? ": you get the next one after " + state.getNextRerollIn() + " more completed task" + (state.getNextRerollIn() == 1 ? "" : "s") : ""));
		doneButton.setVisible(task != null);
		rerollButton.setVisible(task != null);
		takeButton.setVisible(joined && task == null);
		setMessage("", false);
		updateButtons();
		revalidate();
		repaint();
	}

	void setMessage(String text, boolean error)
	{
		ClanSyncPanel.setWrapped(messageLabel, text, ClanSyncPanel.TEXT_WIDTH - 12);
		messageLabel.setVisible(text != null && !text.isEmpty());
		messageLabel.setForeground(error ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
	}

	void setBusy(boolean value)
	{
		busy = value;
		updateButtons();
	}

	/** For tests: whether Skip / Re-roll can be pressed right now. */
	boolean canReroll()
	{
		return rerollButton.isVisible() && rerollButton.isEnabled();
	}

	String rerollLabel()
	{
		return rerollButton.getText();
	}

	private void updateButtons()
	{
		doneButton.setEnabled(!busy && task != null);
		rerollButton.setEnabled(!busy && task != null && state != null && state.getRerolls() > 0);
		takeButton.setEnabled(!busy);
		refreshButton.setEnabled(!busy);
	}
}
