package com.arc1s.clansync;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.text.Normalizer;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sends JSON to the website address the user entered. Only the user's own personal code is sent as authentication.
 */
public class SiteUploader
{
	/** The user's personal plugin code, issued by their clan website. */
	static final String MEMBER_HEADER = "Auth-Member";
	/** Plugin version sent in the User-Agent; raise it with every Plugin Hub update. */
	static final String VERSION = "2.5.3";
	static final String ROSTER_SEGMENT = "clan-sync";
	static final String LOOT_SEGMENT = "loot";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final Gson gson;

	public SiteUploader(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Posts the payload and returns the website's reply. Throws when the upload was not accepted;
	 * {@link RejectedException} when the website refused the personal code itself.
	 */
	public Reply post(String url, String memberToken, Object payload) throws IOException
	{
		HttpUrl parsed = HttpUrl.parse(url == null ? "" : url.trim());
		if (parsed == null)
		{
			throw new IOException("Invalid website URL: " + url);
		}
		// The personal code travels in a header, so plain http is only acceptable for local development.
		if (!parsed.isHttps() && !isLocal(parsed.host()))
		{
			throw new IOException("Website URL must start with https://");
		}
		if (memberToken == null || memberToken.trim().isEmpty())
		{
			throw new IOException("Enter your personal code in the plugin settings");
		}

		Request request = new Request.Builder()
			.url(parsed)
			.header("User-Agent", "RuneLite-ClanSyncLootTracker/" + VERSION)
			.header(MEMBER_HEADER, memberToken.trim())
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody body = response.body();
			String text = body == null ? "" : body.string();
			// The site reports some failures (for example a wrong code) as HTTP 200 with the real status in the JSON body.
			ApiStatus api = parse(text);
			// Only the site's own JSON answer counts as a refusal; a 403 page from a firewall or CDN is a normal failure.
			if (api != null && (isAuthFailure(response.code()) || isAuthFailure(api.status)))
			{
				throw new RejectedException("Website refused access" + describe(text), api.reason());
			}
			if (!response.isSuccessful())
			{
				throw new IOException("Website responded with HTTP " + response.code() + describe(text));
			}
			if (api == null || api.status < 200 || api.status >= 300)
			{
				throw new IOException(api == null ? "Website did not return a JSON status" : "Website rejected the data" + describe(text));
			}
			return new Reply(ascii(api.message).trim(), api.flag("skipped"), api.flag("skillEvents"), api.flag("killCounts"));
		}
	}

	/**
	 * The address of one upload type under the user's API URL. Accepts the base ("https://site/api/plugin")
	 * as well as a full upload address ("https://site/api/plugin/clan-sync" or ".../loot"), whose last
	 * segment is then replaced. Returns null when the URL cannot be parsed.
	 */
	public static String resolve(String base, String segment)
	{
		HttpUrl url = HttpUrl.parse(base == null ? "" : base.trim());
		if (url == null)
		{
			return null;
		}
		HttpUrl.Builder builder = url.newBuilder().query(null).fragment(null);
		int last = url.pathSize() - 1;
		String lastSegment = url.pathSegments().get(last);
		if (lastSegment.isEmpty() || lastSegment.equals(ROSTER_SEGMENT) || lastSegment.equals(LOOT_SEGMENT))
		{
			builder.setPathSegment(last, segment);
		}
		else
		{
			builder.addPathSegment(segment);
		}
		return builder.build().toString();
	}

	/**
	 * The site's reason for an error response: the JSON "message", or the start of the raw body
	 * (a firewall or CDN page). Empty when there is nothing useful.
	 */
	private String describe(String body)
	{
		ApiStatus api = parse(body);
		String reason = ascii(api != null && api.message != null ? api.message : body.replaceAll("<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
		if (reason.isEmpty())
		{
			return "";
		}
		return ": " + (reason.length() > 150 ? reason.substring(0, 150) + "..." : reason);
	}

	private static boolean isAuthFailure(int status)
	{
		return status == 401 || status == 403;
	}

	/**
	 * The RuneScape fonts RuneLite uses have no letters beyond ASCII, so text from the website (which may be
	 * Lithuanian) is shown without accents: "s" with a caron becomes "s", anything else unknown becomes "?".
	 */
	static String ascii(String text)
	{
		if (text == null)
		{
			return "";
		}
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		StringBuilder out = new StringBuilder(plain.length());
		for (char c : plain.toCharArray())
		{
			if (c == '\u00A0' || Character.isWhitespace(c))
			{
				out.append(' ');
			}
			else if (c >= 0x20 && c < 0x7F)
			{
				out.append(c);
			}
			else if (c == '\u2013' || c == '\u2014')
			{
				out.append('-');
			}
			else if (c == '\u201C' || c == '\u201D' || c == '\u201E')
			{
				out.append('"');
			}
			else if (c == '\u2018' || c == '\u2019')
			{
				out.append('\'');
			}
			else if (!Character.isLowSurrogate(c))
			{
				out.append('?');
			}
		}
		return out.toString();
	}

	/**
	 * The website refused the personal code (HTTP 401/403): wrong, revoked, or the member left the clan.
	 * Retrying with the same code cannot succeed.
	 */
	static class RejectedException extends IOException
	{
		private final String reason;

		RejectedException(String message, String reason)
		{
			super(message);
			this.reason = reason;
		}

		/** The website's machine-readable reason, e.g. "rsn_mismatch", "invalid", "revoked", "inactive"; may be null. */
		String getReason()
		{
			return reason;
		}

		/** The code is valid but belongs to another character than the one the data is about. */
		boolean isNameMismatch()
		{
			return "rsn_mismatch".equals(reason);
		}
	}

	/** An accepted upload: the website's message, and whether it skipped the upload as not needed. */
	static class Reply
	{
		final String message;
		final boolean skipped;
		// The site stored (or knowingly ignored) the batch's skill events; older sites leave this false.
		final boolean skillEvents;
		/** The site stored (or knowingly ignored) the batch's kill count reports; older sites leave this false. */
		final boolean killCounts;

		Reply(String message, boolean skipped, boolean skillEvents, boolean killCounts)
		{
			this.message = message;
			this.skipped = skipped;
			this.skillEvents = skillEvents;
			this.killCounts = killCounts;
		}
	}

	private static boolean hasText(String value)
	{
		return value != null && !value.trim().isEmpty();
	}

	private ApiStatus parse(String body)
	{
		try
		{
			return gson.fromJson(body, ApiStatus.class);
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	private static class ApiStatus
	{
		private int status;
		private String message;
		// An object or, from some PHP responses, an empty array: read loosely.
		private JsonElement data;

		private JsonObject object()
		{
			return data != null && data.isJsonObject() ? data.getAsJsonObject() : null;
		}

		String reason()
		{
			JsonObject object = object();
			JsonElement value = object == null ? null : object.get("reason");
			return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
		}

		boolean flag(String name)
		{
			JsonObject object = object();
			JsonElement value = object == null ? null : object.get(name);
			return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
		}
	}

	static boolean isLocal(String host)
	{
		return host.equals("localhost") || host.equals("127.0.0.1") || host.endsWith(".test") || host.endsWith(".localhost");
	}
}
