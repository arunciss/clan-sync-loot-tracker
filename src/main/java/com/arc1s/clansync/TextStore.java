package com.arc1s.clansync;

import java.io.IOException;

/**
 * Small named text files kept between sessions (the upload queue and the last roster snapshot).
 * Backed by the plugin's own data directory (see {@link PluginDirectoryStore}); tests use an in-memory map.
 */
public interface TextStore
{
	/**
	 * The file's text, or null when it does not exist.
	 */
	String read(String name) throws IOException;

	void write(String name, String text) throws IOException;

	void delete(String name) throws IOException;
}
