package com.arc1s.clansync;

import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

// The Fractured Archive does not print the team size in its completion messages. Inside the raid the game
// keeps one varbit per party slot (TFA_CLIENT_P0..P7), non-zero while a player is in that slot; counting the
// filled slots gives the team size. Same approach RuneLite uses for Tombs of Amascut (TOA_CLIENT_P0..P7).
final class TfaTeamSize
{
	static final int[] SLOTS = {
		VarbitID.TFA_CLIENT_P0, VarbitID.TFA_CLIENT_P1, VarbitID.TFA_CLIENT_P2, VarbitID.TFA_CLIENT_P3,
		VarbitID.TFA_CLIENT_P4, VarbitID.TFA_CLIENT_P5, VarbitID.TFA_CLIENT_P6, VarbitID.TFA_CLIENT_P7,
	};

	private TfaTeamSize()
	{
	}

	// Null when no slot is filled (outside the raid), so an unknown size is never sent as a guess.
	static Integer count(IntUnaryOperator varbit)
	{
		int size = 0;
		for (int slot : SLOTS)
		{
			if (varbit.applyAsInt(slot) != 0)
			{
				size++;
			}
		}
		return size > 0 ? size : null;
	}
}
