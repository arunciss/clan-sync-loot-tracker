package com.arc1s.clansync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class ClanSyncPanel extends PluginPanel
{
	/**
	 * Wrapping width in HTML/CSS units for labels in the side panel (225 px minus borders and the scroll bar).
	 * Swing scales CSS px by about 1.3, so 145 is about 190 screen pixels.
	 */
	static final int TEXT_WIDTH = 145;

	private final JLabel statusLabel = wrappingLabel();
	private final JLabel lastSyncLabel = wrappingLabel();
	private final JLabel membersLabel = wrappingLabel();
	private final JLabel lootLabel = wrappingLabel();
	private final JLabel setupLabel = wrappingLabel();
	private final JButton syncButton = new JButton("Sync clan members");
	private final JButton changesToggle = new JButton();
	private final JTextArea changesArea = new JTextArea();
	private final JPanel changesPanel = new JPanel(new BorderLayout(0, 4));
	/** Roster status, last sync, members, Sync button and roster changes: only for the clan Owner and Deputy Owners. */
	private final JPanel rosterPanel = new JPanel(new GridBagLayout());
	private final ClanTasksSection tasksSection;
	private String changesTitle = "";

	ClanSyncPanel(Runnable onSyncRequested, ClanTasksSection.Actions taskActions)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Clan Sync & Loot Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		JPanel content = new JPanel(new GridBagLayout());
		stack(content, title, 6);
		stack(content, lootLabel, 6);
		stack(content, setupLabel, 6);

		JLabel rosterTitle = new JLabel("Clan roster (leaders)");
		rosterTitle.setFont(FontManager.getRunescapeBoldFont());
		rosterTitle.setForeground(Color.WHITE);
		rosterPanel.setOpaque(false);
		stack(rosterPanel, rosterTitle, 4);
		stack(rosterPanel, statusLabel, 4);
		stack(rosterPanel, lastSyncLabel, 4);
		stack(rosterPanel, membersLabel, 6);
		stack(rosterPanel, syncButton, 6);

		changesToggle.setFocusable(false);
		changesToggle.setHorizontalAlignment(JButton.LEFT);
		changesToggle.setMargin(new Insets(2, 4, 2, 4));
		changesToggle.addActionListener(e -> {
			changesArea.setVisible(!changesArea.isVisible());
			updateChangesToggle();
			revalidate();
		});
		changesArea.setEditable(false);
		changesArea.setLineWrap(true);
		changesArea.setWrapStyleWord(true);
		changesArea.setFont(FontManager.getRunescapeSmallFont());
		changesArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		changesArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		changesArea.setBorder(new EmptyBorder(6, 6, 6, 6));
		changesArea.setVisible(false);
		changesPanel.setOpaque(false);
		changesPanel.add(changesToggle, BorderLayout.NORTH);
		changesPanel.add(changesArea, BorderLayout.CENTER);
		changesPanel.setVisible(false);
		stack(rosterPanel, changesPanel, 0);

		tasksSection = new ClanTasksSection(taskActions);
		tasksSection.setVisible(false);
		stack(content, tasksSection, 10);
		// Hidden until the game says the player is the clan Owner or a Deputy Owner.
		rosterPanel.setVisible(false);
		stack(content, rosterPanel, 0);
		add(content, BorderLayout.NORTH);

		lootLabel.setFont(FontManager.getRunescapeSmallFont());
		setupLabel.setFont(FontManager.getRunescapeSmallFont());
		syncButton.setFocusable(false);
		syncButton.setToolTipText("Clan Owner and Deputy Owners: upload every member's name, rank and join date now"
			+ " (also picks up rank and name changes). Runs automatically once a day as well.");
		syncButton.addActionListener(e -> onSyncRequested.run());

		setStatus("Idle", false);
		setLastSync("never");
		setMembers("-");
		setLoot(LootSender.NOT_CONFIGURED);
		setSetupHint(true);
	}

	/** Adds a full-width row; rows that are hidden take no space. */
	static void stack(JPanel panel, Component component, int gap)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = GridBagConstraints.RELATIVE;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, 0, gap, 0);
		panel.add(component, c);
	}

	/** A label that wraps long text instead of cutting it off. */
	static JLabel wrappingLabel()
	{
		JLabel label = new JLabel();
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	static void setWrapped(JLabel label, String text)
	{
		setWrapped(label, text, TEXT_WIDTH);
	}

	static void setWrapped(JLabel label, String text, int width)
	{
		String safe = text == null ? "" : text;
		label.setText("<html><body style='width:" + width + "px'>" + escape(safe) + "</body></html>");
		label.setToolTipText(safe.isEmpty() ? null : safe);
	}

	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
	}

	ClanTasksSection getTasksSection()
	{
		return tasksSection;
	}

	void setLoot(String text)
	{
		SwingUtilities.invokeLater(() -> setWrapped(lootLabel, "Loot: " + text));
	}

	/** The setup help shows only while the URL or personal code is missing. */
	void setSetupHint(boolean show)
	{
		SwingUtilities.invokeLater(() ->
		{
			setWrapped(setupLabel, "Setup: open the plugin settings, enter the website API URL and your personal code, then turn on"
				+ " the uploads you want. Nothing is recorded or sent until both are filled in.");
			setupLabel.setVisible(show);
		});
	}

	/** Shows the roster part only to the clan Owner and Deputy Owners; everyone else never sees it. */
	void setRosterVisible(boolean visible)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (rosterPanel.isVisible() != visible)
			{
				rosterPanel.setVisible(visible);
				revalidate();
				repaint();
			}
		});
	}

	boolean isRosterShown()
	{
		return rosterPanel.isVisible();
	}

	void setStatus(String text, boolean error)
	{
		SwingUtilities.invokeLater(() ->
		{
			setWrapped(statusLabel, "Status: " + text);
			statusLabel.setForeground(error ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		});
	}

	void setLastSync(String text)
	{
		SwingUtilities.invokeLater(() -> setWrapped(lastSyncLabel, "Last roster sync: " + text));
	}

	void setMembers(String text)
	{
		SwingUtilities.invokeLater(() -> setWrapped(membersLabel, "Members: " + text));
	}

	/** Roster changes from the last sync; a small closed section, hidden when there is nothing to show. */
	void setChanges(String title, String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean show = text != null && !text.trim().isEmpty();
			changesTitle = title == null ? "Roster changes" : title;
			changesArea.setText(show ? text : "");
			changesArea.setVisible(false);
			changesPanel.setVisible(show);
			updateChangesToggle();
			revalidate();
		});
	}

	private void updateChangesToggle()
	{
		changesToggle.setText((changesArea.isVisible() ? "[-] " : "[+] ") + changesTitle);
	}

	/** Show or hide the clan tasks section; state is null while loading. */
	void showTasks(boolean visible, ClanTaskState state)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (visible)
			{
				tasksSection.showState(state);
			}
			tasksSection.setVisible(visible);
			revalidate();
			repaint();
		});
	}

	void setTasksMessage(String text, boolean error)
	{
		SwingUtilities.invokeLater(() -> tasksSection.setMessage(text, error));
	}

	/** Green tick on success, red cross and the retry line on failure. */
	void setTasksResult(boolean success, String message)
	{
		SwingUtilities.invokeLater(() -> tasksSection.showResult(success, message));
	}

	/** {@code which} is done, skip, take or refresh. A null which clears the working state. */
	void setTasksWorking(String which, String label)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (which == null)
			{
				tasksSection.endWork();
			}
			else
			{
				tasksSection.beginWork(which, label);
			}
		});
	}

	void setBusy(boolean busy)
	{
		SwingUtilities.invokeLater(() -> syncButton.setEnabled(!busy));
	}
}
