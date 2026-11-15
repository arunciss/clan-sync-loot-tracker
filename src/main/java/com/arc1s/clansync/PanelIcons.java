package com.arc1s.clansync;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/** Marks drawn with Graphics2D. The RuneScape font has no arrows, ticks, or crosses. */
final class PanelIcons implements Icon
{
	static final PanelIcons DOWN = new PanelIcons(Kind.DOWN, Color.WHITE);
	static final PanelIcons UP = new PanelIcons(Kind.UP, Color.WHITE);
	static final PanelIcons INFO = new PanelIcons(Kind.INFO, new Color(0xBDBDBD));
	static final PanelIcons INFO_MUTED = new PanelIcons(Kind.INFO, new Color(0x757575));
	static final PanelIcons CHECK = new PanelIcons(Kind.CHECK, new Color(0x69F0AE));
	static final PanelIcons CROSS = new PanelIcons(Kind.CROSS, new Color(0xFF5252));

	private enum Kind
	{
		DOWN, UP, INFO, CHECK, CROSS
	}

	private final Kind kind;
	private final Color color;

	private PanelIcons(Kind kind, Color color)
	{
		this.kind = kind;
		this.color = color;
	}

	@Override
	public int getIconWidth()
	{
		return kind == Kind.INFO ? 14 : 12;
	}

	@Override
	public int getIconHeight()
	{
		return kind == Kind.INFO ? 14 : 12;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.translate(x, y);
			switch (kind)
			{
				case DOWN:
					g2.fillPolygon(new int[]{1, 11, 6}, new int[]{3, 3, 9}, 3);
					break;
				case UP:
					g2.fillPolygon(new int[]{1, 11, 6}, new int[]{9, 9, 3}, 3);
					break;
				case INFO:
					g2.setStroke(new BasicStroke(1.4f));
					g2.drawOval(1, 1, 11, 11);
					g2.fillOval(6, 3, 2, 2);
					g2.fillRect(6, 6, 2, 5);
					break;
				case CHECK:
					g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g2.drawLine(2, 6, 5, 10);
					g2.drawLine(5, 10, 10, 2);
					break;
				case CROSS:
					g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g2.drawLine(3, 3, 9, 9);
					g2.drawLine(9, 3, 3, 9);
					break;
				default:
					break;
			}
		}
		finally
		{
			g2.dispose();
		}
	}
}
