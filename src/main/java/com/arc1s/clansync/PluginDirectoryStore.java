package com.arc1s.clansync;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import net.runelite.client.util.Filepath;

/**
 * {@link TextStore} inside the directory RuneLite gives this plugin ({@code Plugin#getPluginDirectory()}).
 * Writes go to a temporary file first and are then moved over the old one, so a crash never leaves half a file.
 */
class PluginDirectoryStore implements TextStore
{
	private final Filepath directory;

	PluginDirectoryStore(Filepath directory)
	{
		this.directory = directory;
	}

	@Override
	public String read(String name) throws IOException
	{
		Filepath file = directory.joinSegment(name);
		if (!file.isFile())
		{
			return null;
		}
		try (InputStream in = file.openInputStream())
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Override
	public void write(String name, String text) throws IOException
	{
		directory.createDirectories();
		Filepath temp = directory.joinSegment(name + ".tmp");
		temp.write(text.getBytes(StandardCharsets.UTF_8));
		temp.moveTo(directory.joinSegment(name), StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public void delete(String name) throws IOException
	{
		directory.joinSegment(name).deleteIfExists();
	}
}
