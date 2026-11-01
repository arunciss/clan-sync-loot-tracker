package com.arc1s.clansync;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Remembers what the website refused for the current website address and personal code:
 * the code itself (wrong, revoked, or the member left the clan), or single character names the code does not
 * belong to. While the address and code are unchanged nothing more is sent for them; entering a new code or address
 * lifts the pause. Kept in memory only, so a client restart allows one new attempt (for example after a name change
 * has reached the website).
 */
final class AuthGate
{
	private String rejectedCode;
	private String namesFor;
	private final Set<String> rejectedNames = new TreeSet<>();

	private static String key(String endpoint, String token)
	{
		return (endpoint == null ? "" : endpoint.trim()) + '\n' + (token == null ? "" : token.trim());
	}

	synchronized boolean isRejected(String endpoint, String token)
	{
		return rejectedCode != null && rejectedCode.equals(key(endpoint, token));
	}

	synchronized void reject(String endpoint, String token)
	{
		rejectedCode = key(endpoint, token);
	}

	/** True when the website said this code does not belong to the given character. */
	synchronized boolean isNameRejected(String endpoint, String token, String name)
	{
		return name != null && key(endpoint, token).equals(namesFor) && rejectedNames.contains(SnapshotDiff.key(name));
	}

	synchronized void rejectName(String endpoint, String token, String name)
	{
		String current = key(endpoint, token);
		if (!current.equals(namesFor))
		{
			namesFor = current;
			rejectedNames.clear();
		}
		rejectedNames.add(SnapshotDiff.key(name));
	}

	/** Normalised names refused for this address and code. */
	synchronized Set<String> rejectedNames(String endpoint, String token)
	{
		return key(endpoint, token).equals(namesFor) ? new TreeSet<>(rejectedNames) : Collections.emptySet();
	}

	synchronized void clear()
	{
		rejectedCode = null;
		namesFor = null;
		rejectedNames.clear();
	}
}
