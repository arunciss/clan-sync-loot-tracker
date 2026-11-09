package com.arc1s.clansync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * "Clan tasks" part of the side panel: a card with the current task and Done, Skip and Take buttons.
 * Skip opens an inline list (re-roll, and the free skips the task allows). Must be used on the Swing thread.
 */
class ClanTasksSection extends JPanel
{
	interface Actions
	{
		void done(ClanTask task);

		/** A Skip-menu row the member chose. The caller confirms, then sends it. */
		void skip(ClanTask task, ClanTaskState state, SkipOption option);

		void take();

		void refresh();
	}

	static final Color POINTS_COLOR = new Color(0xFFD54F);
	static final Color WILDY_COLOR = new Color(0xFF5252);
	static final Color WORKING_ORANGE = new Color(0xFF9800);
	static final Color FREE_GREEN = new Color(0x69F0AE);
	static final Color SUCCESS_GREEN = new Color(0x69F0AE);
	static final Color DISABLED_GREY = new Color(0x757575);
	static final String SENDING = "Sending to the website. Please wait, do not press again.";
	static final String UNSUPPORTED_BUTTON = "The website does not support this button yet.";
	static final String NETWORK_FAILURE = "Could not reach the website. Nothing changed. Press again to retry.";
	private static final int BUTTON_HEIGHT = 26;
	/** Wrapping width (CSS units) inside the card: the section and card padding take about 30 px. */
	private static final int CARD_TEXT_WIDTH = ClanSyncPanel.TEXT_WIDTH - 22;

	private final Actions actions;
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
	private final JButton skipButton = button("Skip \u25BE", "Skip this task");
	private final JPanel optionsPanel = new JPanel(new GridBagLayout());
	private final JButton takeButton = button("Take a new task", "Get your next clan task now");
	private final JButton refreshButton = new JButton("Refresh");
	private final Border doneBorder;
	private final Border skipBorder;
	private final Border takeBorder;
	private final Border refreshBorder;
	private ClanTask task;
	private ClanTaskState state;
	private boolean busy;
	private boolean skipOpen;
	private String workingWhich;

	ClanTasksSection(Actions actions)
	{
		this.actions = actions;
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
		refreshButton.addActionListener(e -> {
			if (actions != null)
			{
				actions.refresh();
			}
		});
		header.add(refreshButton, BorderLayout.EAST);
		doneBorder = doneButton.getBorder();
		skipBorder = skipButton.getBorder();
		takeBorder = takeButton.getBorder();
		refreshBorder = refreshButton.getBorder();

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
		optionsPanel.setOpaque(false);
		optionsPanel.setVisible(false);
		optionsPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(WORKING_ORANGE), new EmptyBorder(4, 4, 4, 4)));

		doneButton.addActionListener(e -> {
			if (task != null && actions != null)
			{
				actions.done(task);
			}
		});
		skipButton.addActionListener(e -> toggleSkip());
		takeButton.addActionListener(e -> {
			if (actions != null)
			{
				actions.take();
			}
		});

		ClanSyncPanel.stack(this, header, 6);
		ClanSyncPanel.stack(this, card, 6);
		ClanSyncPanel.stack(this, stats, 8);
		ClanSyncPanel.stack(this, doneButton, 4);
		ClanSyncPanel.stack(this, skipButton, 4);
		ClanSyncPanel.stack(this, optionsPanel, 4);
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

	static String rerollsText(int rerolls)
	{
		return "Re-rolls left: " + Math.max(0, rerolls);
	}

	static String successLine(String message)
	{
		String text = message == null ? "" : message.trim();
		if (text.startsWith("\u2713"))
		{
			return text;
		}
		return text.isEmpty() ? "\u2713 Sent." : "\u2713 " + text;
	}

	/**
	 * Red result line. A refusal from the website is shown in its own words (a 409 already says nothing changed).
	 * A line that is only our own network text already includes the retry sentence.
	 */
	static String failureLine(String message)
	{
		String text = message == null ? "" : message.trim();
		if (text.isEmpty())
		{
			text = NETWORK_FAILURE;
		}
		if (text.startsWith("\u2717"))
		{
			return text;
		}
		return "\u2717 " + text;
	}

	/** Dark text on the light difficulty colours, white on red. */
	private static Color badgeText(String difficulty)
	{
		return "hard".equals(difficulty) ? Color.WHITE : Color.BLACK;
	}

	void showState(ClanTaskState newState)
	{
		int previousId = task == null ? -1 : task.getAssignmentId();
		state = newState;
		task = state == null ? null : state.getTask();
		if (task == null || task.getAssignmentId() != previousId)
		{
			skipOpen = false;
		}
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
		doneButton.setVisible(task != null);
		skipButton.setVisible(task != null);
		takeButton.setVisible(joined && task == null);
		rebuildOptions();
		if (!busy)
		{
			setMessage("", false);
			restoreLabels();
		}
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

	void showResult(boolean success, String message)
	{
		String line = success ? successLine(message) : failureLine(message);
		ClanSyncPanel.setWrapped(messageLabel, line, ClanSyncPanel.TEXT_WIDTH - 12);
		messageLabel.setVisible(true);
		messageLabel.setForeground(success ? SUCCESS_GREEN : ColorScheme.PROGRESS_ERROR_COLOR);
	}

	/**
	 * A button was pressed. {@code which} is {@code done}, {@code skip}, {@code take} or {@code refresh}.
	 * {@code label} is the "Working... (...)" text for that button.
	 */
	void beginWork(String which, String label)
	{
		busy = true;
		workingWhich = which;
		skipOpen = false;
		if ("done".equals(which))
		{
			doneButton.setText(label);
			outline(doneButton, true);
		}
		else if ("take".equals(which))
		{
			takeButton.setText(label);
			outline(takeButton, true);
		}
		else if ("refresh".equals(which))
		{
			refreshButton.setText(label);
			outline(refreshButton, true);
		}
		else
		{
			skipButton.setText(label);
			outline(skipButton, true);
		}
		rebuildOptions();
		setMessage(SENDING, false);
		updateButtons();
		revalidate();
		repaint();
	}

	void endWork()
	{
		busy = false;
		workingWhich = null;
		restoreLabels();
		updateButtons();
		revalidate();
		repaint();
	}

	String messageText()
	{
		return messageLabel.isVisible() ? messageLabel.getToolTipText() : "";
	}

	boolean doneEnabled()
	{
		return doneButton.isEnabled();
	}

	boolean refreshEnabled()
	{
		return refreshButton.isEnabled();
	}

	boolean takeEnabled()
	{
		return takeButton.isEnabled();
	}

	boolean skipEnabled()
	{
		return skipButton.isEnabled();
	}

	String skipLabel()
	{
		return skipButton.getText();
	}

	boolean skipOutlined()
	{
		return outlined(skipButton);
	}

	boolean doneOutlined()
	{
		return outlined(doneButton);
	}

	List<SkipOption> options()
	{
		return SkipOptions.resolve(state);
	}

	void setSkipOpen(boolean open)
	{
		skipOpen = open && task != null && !busy;
		rebuildOptions();
		revalidate();
	}

	boolean isSkipOpen()
	{
		return skipOpen;
	}

	private void toggleSkip()
	{
		if (busy || task == null)
		{
			return;
		}
		skipOpen = !skipOpen;
		rebuildOptions();
		revalidate();
		repaint();
	}

	private void rebuildOptions()
	{
		optionsPanel.removeAll();
		boolean show = skipOpen && task != null && !busy;
		optionsPanel.setVisible(show);
		skipButton.setText(busy && "skip".equals(workingWhich) ? skipButton.getText() : (skipOpen ? "Skip \u25B2" : "Skip \u25BE"));
		if (!show)
		{
			return;
		}
		boolean first = true;
		for (SkipOption option : SkipOptions.resolve(state))
		{
			if (!first)
			{
				ClanSyncPanel.stack(optionsPanel, Box.createVerticalStrut(4), 0);
			}
			first = false;
			ClanSyncPanel.stack(optionsPanel, optionRow(option), 2);
		}
	}

	private JPanel optionRow(SkipOption option)
	{
		boolean on = option.isAvailable();
		JPanel row = new JPanel(new BorderLayout(6, 2));
		row.setOpaque(true);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		JLabel name = new JLabel(option.getLabel() == null ? "" : option.getLabel());
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(on ? Color.WHITE : DISABLED_GREY);
		JLabel cost = new JLabel(option.costTag());
		cost.setFont(FontManager.getRunescapeSmallFont());
		cost.setOpaque(true);
		cost.setBorder(new EmptyBorder(0, 4, 0, 4));
		cost.setBackground(ColorScheme.DARK_GRAY_COLOR);
		if (!on)
		{
			cost.setForeground(DISABLED_GREY);
		}
		else if (SkipOption.COST_REROLL.equals(option.getCost()))
		{
			cost.setForeground(WORKING_ORANGE);
		}
		else
		{
			cost.setForeground(FREE_GREEN);
		}
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(name, BorderLayout.CENTER);
		if (!option.costTag().isEmpty())
		{
			top.add(cost, BorderLayout.EAST);
		}
		String detail = on ? option.getHint() : option.getReason();
		if (detail == null || detail.isEmpty())
		{
			detail = on ? "" : "";
		}
		JLabel hint = new JLabel(detail == null ? "" : detail);
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(on ? ColorScheme.LIGHT_GRAY_COLOR : DISABLED_GREY);
		row.add(top, BorderLayout.NORTH);
		if (detail != null && !detail.isEmpty())
		{
			row.add(hint, BorderLayout.SOUTH);
		}
		row.setToolTipText(detail == null || detail.isEmpty() ? null : detail);
		if (on)
		{
			MouseAdapter click = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (!busy && actions != null && task != null)
					{
						actions.skip(task, state, option);
					}
				}
			};
			listen(row, click);
		}
		row.setName(option.getId());
		return row;
	}

	/** Swing does not bubble clicks, so the label under the cursor has to carry the listener too. */
	private static void listen(java.awt.Component component, MouseAdapter click)
	{
		component.addMouseListener(click);
		component.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		if (component instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) component).getComponents())
			{
				listen(child, click);
			}
		}
	}

	private void restoreLabels()
	{
		doneButton.setText("Done");
		takeButton.setText("Take a new task");
		refreshButton.setText("Refresh");
		skipButton.setText(skipOpen ? "Skip \u25B2" : "Skip \u25BE");
		outline(doneButton, false);
		outline(skipButton, false);
		outline(takeButton, false);
		outline(refreshButton, false);
	}

	private void outline(JButton button, boolean on)
	{
		if (on)
		{
			button.setBorder(BorderFactory.createLineBorder(WORKING_ORANGE, 2));
			button.setBorderPainted(true);
		}
		else if (button == doneButton)
		{
			button.setBorder(doneBorder);
		}
		else if (button == skipButton)
		{
			button.setBorder(skipBorder);
		}
		else if (button == takeButton)
		{
			button.setBorder(takeBorder);
		}
		else
		{
			button.setBorder(refreshBorder);
		}
	}

	private static boolean outlined(JButton button)
	{
		Border border = button.getBorder();
		if (border instanceof javax.swing.border.LineBorder)
		{
			return WORKING_ORANGE.equals(((javax.swing.border.LineBorder) border).getLineColor());
		}
		return false;
	}

	private void updateButtons()
	{
		doneButton.setEnabled(!busy && task != null);
		skipButton.setEnabled(!busy && task != null);
		takeButton.setEnabled(!busy);
		refreshButton.setEnabled(!busy);
	}
}
