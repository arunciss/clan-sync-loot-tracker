package com.arc1s.clansync;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Optional "clan tasks" support. A website that has it answers GET {api}/pvm/current with feature "clan_tasks";
 * any other answer (404, an HTML page) means the website does not support clan tasks and the panel section stays hidden.
 * Only the user's personal code is sent.
 */
class ClanTasksClient
{
	static final String ACTION_DONE = "done";
	/** Skip the task for one re-roll, like a normal skip. */
	static final String ACTION_REROLL = "reroll";
	static final String ACTION_TAKE = "take";
	/** Saves the task preferences (wilderness, slayer boss tasks); they apply from the next task. */
	static final String ACTION_PREFS = "prefs";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final Gson gson;

	ClanTasksClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/** What the website answered. */
	static class Result
	{
		/** HTTP-like status from the website's JSON (or the HTTP code when there is no JSON). */
		final int status;
		final String message;
		final String reason;
		/** False when the website does not offer clan tasks at all. */
		final boolean supported;
		final ClanTaskState state;
		/** The task just completed with Done, if any. */
		final ClanTask completed;

		Result(int status, String message, String reason, boolean supported, ClanTaskState state, ClanTask completed)
		{
			this.status = status;
			this.message = message;
			this.reason = reason;
			this.supported = supported;
			this.state = state;
			this.completed = completed;
		}

		boolean ok()
		{
			return status >= 200 && status < 300;
		}

		/** The personal code itself was refused (wrong, revoked, member left the clan). */
		boolean codeRefused()
		{
			return (status == 401 || status == 403) && !"disabled".equals(reason);
		}
	}

	Result current(String base, String code) throws IOException
	{
		Result result = call(url(base, "current"), code, null);
		if (result.ok() && (result.state == null || !ClanTaskState.FEATURE.equals(result.state.getFeature())))
		{
			return new Result(404, "This website does not offer clan tasks", null, false, null, null);
		}
		return result;
	}

	Result act(String base, String code, String action, Integer assignmentId, String requestId) throws IOException
	{
		Map<String, Object> body = new LinkedHashMap<>();
		if (assignmentId != null)
		{
			body.put("assignmentId", assignmentId);
		}
		body.put("requestId", requestId);
		return call(url(base, action), code, body);
	}

	Result prefs(String base, String code, boolean wilderness, boolean slayerTasks) throws IOException
	{
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("wilderness", wilderness);
		body.put("slayerTasks", slayerTasks);
		return call(url(base, ACTION_PREFS), code, body);
	}

	/** "{api}/pvm/{name}" under the user's API URL (which may also end in /clan-sync or /loot). */
	static HttpUrl url(String base, String name)
	{
		String root = SiteUploader.resolve(base, "pvm");
		HttpUrl url = root == null ? null : HttpUrl.parse(root);
		return url == null ? null : url.newBuilder().addPathSegment(name).build();
	}

	private Result call(HttpUrl url, String code, Object body) throws IOException
	{
		if (url == null)
		{
			throw new IOException("Invalid website URL");
		}
		if (!url.isHttps() && !SiteUploader.isLocal(url.host()))
		{
			throw new IOException("Website URL must start with https://");
		}
		if (code == null || code.trim().isEmpty())
		{
			throw new IOException("Enter your personal code in the plugin settings");
		}
		Request.Builder request = new Request.Builder()
			.url(url)
			.header("User-Agent", "RuneLite-ClanSyncLootTracker/" + SiteUploader.VERSION)
			.header(SiteUploader.MEMBER_HEADER, code.trim());
		if (body != null)
		{
			request.post(RequestBody.create(JSON, gson.toJson(body)));
		}
		try (Response response = httpClient.newCall(request.build()).execute())
		{
			ResponseBody responseBody = response.body();
			return parse(response.code(), responseBody == null ? "" : responseBody.string());
		}
	}

	Result parse(int httpCode, String text)
	{
		JsonObject json;
		try
		{
			JsonElement element = gson.fromJson(text, JsonElement.class);
			json = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			json = null;
		}
		if (json == null || !json.has("status"))
		{
			// Not this API (a 404 page, a firewall page): treat as "no clan tasks here" unless it is a server hiccup.
			boolean temporary = httpCode == 429 || httpCode >= 500;
			return new Result(httpCode, temporary ? "Website is busy (HTTP " + httpCode + ")" : "This website does not offer clan tasks",
				null, temporary, null, null);
		}
		int status = json.get("status").getAsInt();
		if (status >= 200 && status < 300 && (httpCode < 200 || httpCode >= 300))
		{
			status = httpCode;
		}
		String message = json.has("message") && json.get("message").isJsonPrimitive() ? SiteUploader.ascii(json.get("message").getAsString()).trim() : "";
		JsonObject data = json.has("data") && json.get("data").isJsonObject() ? json.getAsJsonObject("data") : null;
		String reason = data != null && data.has("reason") && data.get("reason").isJsonPrimitive() ? data.get("reason").getAsString() : null;
		ClanTaskState state = null;
		ClanTask completed = null;
		if (data != null)
		{
			if (data.has("feature"))
			{
				state = gson.fromJson(data, ClanTaskState.class);
			}
			else if (data.has("state") && data.get("state").isJsonObject())
			{
				state = gson.fromJson(data.get("state"), ClanTaskState.class);
			}
			if (data.has("completed") && data.get("completed").isJsonObject())
			{
				completed = gson.fromJson(data.get("completed"), ClanTask.class);
			}
		}
		return new Result(status, message, reason, true, state, completed);
	}
}
