package com.arc1s.clansync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
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
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
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
	static final Color REST_BG = new Color(0x2B2B2B);
	static final Color HOVER_BG = new Color(0x3F3F3F);
	static final Color PRESSED_BG = new Color(0x1A1A1A);
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
	private final JButton skipButton = button("Skip", "Skip this task");
	private final JPanel optionsPanel = new JPanel(new GridBagLayout());
	private final JButton takeButton = button("Take a new task", "Get your next clan task now");
	private final JButton refreshButton = new JButton("Refresh");
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
		installHover(doneButton);
		installHover(skipButton);
		installHover(takeButton);
		installHover(refreshButton);
		skipButton.setHorizontalTextPosition(SwingConstants.LEFT);
		skipButton.setIconTextGap(6);
		syncSkipChrome();

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

	/** Result text only. A drawn icon, not a glyph, marks success or failure. */
	static String successLine(String message)
	{
		String text = message == null ? "" : message.trim();
		return text.isEmpty() ? "Sent." : text;
	}

	/**
	 * Refusal text in the website's own words (a 409 already says nothing changed).
	 * An empty message becomes the network failure line.
	 */
	static String failureLine(String message)
	{
		String text = message == null ? "" : message.trim();
		return text.isEmpty() ? NETWORK_FAILURE : text;
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
		messageLabel.setIcon(null);
		messageLabel.setVisible(text != null && !text.isEmpty());
		messageLabel.setForeground(error ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
	}

	void showResult(boolean success, String message)
	{
		String line = success ? successLine(message) : failureLine(message);
		ClanSyncPanel.setWrapped(messageLabel, line, ClanSyncPanel.TEXT_WIDTH - 12);
		messageLabel.setIcon(success ? PanelIcons.CHECK : PanelIcons.CROSS);
		messageLabel.setIconTextGap(6);
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
		}
		else if ("take".equals(which))
		{
			takeButton.setText(label);
		}
		else if ("refresh".equals(which))
		{
			refreshButton.setText(label);
		}
		else
		{
			skipButton.setText(label);
			skipButton.setIcon(null);
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

	Icon skipIcon()
	{
		return skipButton.getIcon();
	}

	Icon messageIcon()
	{
		return messageLabel.getIcon();
	}

	JButton taskButton(String which)
	{
		if ("skip".equals(which))
		{
			return skipButton;
		}
		if ("take".equals(which))
		{
			return takeButton;
		}
		if ("refresh".equals(which))
		{
			return refreshButton;
		}
		return doneButton;
	}

	JPanel optionRow(String id)
	{
		return findNamed(optionsPanel, id);
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
		syncSkipChrome();
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
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(true);
		row.setBackground(REST_BG);
		row.setBorder(restBorder());
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
		String detail = on ? firstText(option.getHint(), option.getReason()) : firstText(option.getReason(), option.getHint());
		JLabel info = new JLabel(on ? PanelIcons.INFO : PanelIcons.INFO_MUTED);
		info.setName("info");
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		right.setOpaque(false);
		if (!option.costTag().isEmpty())
		{
			right.add(cost);
		}
		if (detail != null)
		{
			right.add(info);
		}
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		line.setBorder(new EmptyBorder(4, 6, 4, 6));
		line.add(name, BorderLayout.CENTER);
		line.add(right, BorderLayout.EAST);
		row.add(line, BorderLayout.CENTER);
		String tip = htmlTip(detail);
		row.setToolTipText(tip);
		name.setToolTipText(tip);
		cost.setToolTipText(tip);
		info.setToolTipText(tip);
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

				@Override
				public void mousePressed(MouseEvent e)
				{
					paintRow(row, PRESSED_BG, true);
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					paintRow(row, row.contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row)) ? HOVER_BG : REST_BG,
						row.contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row)));
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					paintRow(row, HOVER_BG, true);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					if (!row.contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row)))
					{
						paintRow(row, REST_BG, false);
					}
				}
			};
			listen(row, click);
		}
		else
		{
			row.setCursor(Cursor.getDefaultCursor());
		}
		row.setName(option.getId());
		return row;
	}

	private static void paintRow(JPanel row, Color background, boolean accent)
	{
		row.setBackground(background);
		row.setBorder(accent ? accentBorder() : restBorder());
		row.repaint();
	}

	private static String firstText(String preferred, String fallback)
	{
		if (preferred != null && !preferred.trim().isEmpty())
		{
			return preferred.trim();
		}
		if (fallback != null && !fallback.trim().isEmpty())
		{
			return fallback.trim();
		}
		return null;
	}

	private static String htmlTip(String text)
	{
		if (text == null || text.isEmpty())
		{
			return null;
		}
		return "<html><body style='width:180px'>" + ClanSyncPanel.escape(text) + "</body></html>";
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
		syncSkipChrome();
	}

	private void syncSkipChrome()
	{
		if (busy && "skip".equals(workingWhich))
		{
			skipButton.setIcon(null);
			return;
		}
		skipButton.setText("Skip");
		skipButton.setIcon(skipOpen ? PanelIcons.UP : PanelIcons.DOWN);
	}

	private void installHover(JButton button)
	{
		button.setUI(new BasicButtonUI());
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setFocusPainted(false);
		button.setRolloverEnabled(false);
		button.setForeground(Color.WHITE);
		button.setBackground(REST_BG);
		button.setBorder(restBorder());
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.putClientProperty("clan.hover", Boolean.TRUE);
				restyle(button);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.putClientProperty("clan.hover", Boolean.FALSE);
				button.putClientProperty("clan.pressed", Boolean.FALSE);
				restyle(button);
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (button.isEnabled() && !isWorking(button))
				{
					button.putClientProperty("clan.pressed", Boolean.TRUE);
					restyle(button);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				button.putClientProperty("clan.pressed", Boolean.FALSE);
				restyle(button);
			}
		});
	}

	private boolean isWorking(JButton button)
	{
		return busy && button == workingButton();
	}

	private JButton workingButton()
	{
		if ("skip".equals(workingWhich))
		{
			return skipButton;
		}
		if ("take".equals(workingWhich))
		{
			return takeButton;
		}
		if ("refresh".equals(workingWhich))
		{
			return refreshButton;
		}
		if ("done".equals(workingWhich))
		{
			return doneButton;
		}
		return null;
	}

	private void restyle(JButton button)
	{
		boolean working = isWorking(button);
		boolean hover = Boolean.TRUE.equals(button.getClientProperty("clan.hover"));
		boolean pressed = Boolean.TRUE.equals(button.getClientProperty("clan.pressed"));
		if (!button.isEnabled() && !working)
		{
			button.setBackground(REST_BG);
			button.setForeground(DISABLED_GREY);
			button.setBorder(restBorder());
			button.setCursor(Cursor.getDefaultCursor());
			return;
		}
		button.setForeground(Color.WHITE);
		button.setCursor(button.isEnabled()
			? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
			: Cursor.getDefaultCursor());
		if (working)
		{
			button.setBackground(REST_BG);
			button.setBorder(accentBorder());
		}
		else if (pressed && button.isEnabled())
		{
			button.setBackground(PRESSED_BG);
			button.setBorder(accentBorder());
		}
		else if (hover && button.isEnabled())
		{
			button.setBackground(HOVER_BG);
			button.setBorder(accentBorder());
		}
		else
		{
			button.setBackground(REST_BG);
			button.setBorder(restBorder());
		}
	}

	private static Border restBorder()
	{
		return BorderFactory.createCompoundBorder(new LineBorder(REST_BG, 1), new EmptyBorder(3, 6, 3, 6));
	}

	private static Border accentBorder()
	{
		return BorderFactory.createCompoundBorder(new LineBorder(WORKING_ORANGE, 1), new EmptyBorder(3, 6, 3, 6));
	}

	private static boolean outlined(JButton button)
	{
		Border border = button.getBorder();
		if (border instanceof CompoundBorder)
		{
			border = ((CompoundBorder) border).getOutsideBorder();
		}
		return border instanceof LineBorder && WORKING_ORANGE.equals(((LineBorder) border).getLineColor());
	}

	private void updateButtons()
	{
		doneButton.setEnabled(!busy && task != null);
		skipButton.setEnabled(!busy && task != null);
		takeButton.setEnabled(!busy);
		refreshButton.setEnabled(!busy);
		restyle(doneButton);
		restyle(skipButton);
		restyle(takeButton);
		restyle(refreshButton);
	}

	private static JPanel findNamed(java.awt.Container parent, String name)
	{
		for (java.awt.Component child : parent.getComponents())
		{
			if (name.equals(child.getName()) && child instanceof JPanel)
			{
				return (JPanel) child;
			}
			if (child instanceof java.awt.Container)
			{
				JPanel found = findNamed((java.awt.Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
