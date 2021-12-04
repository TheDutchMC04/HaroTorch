package dev.array21.harotorch.update;

import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import dev.array21.harotorch.HaroTorch;
import dev.array21.httplib.Http;
import dev.array21.httplib.Http.RequestMethod;
import dev.array21.httplib.Http.ResponseObject;

public class UpdateChecker {
	
	private final HaroTorch plugin;
	
	public UpdateChecker(HaroTorch plugin) {
		this.plugin = plugin;
	}

	public void checkUpdate() {
		String[] currentVersion = this.plugin.getDescription().getVersion().split(Pattern.quote("."));
		int currentMajorVersion = Integer.parseInt(currentVersion[0]);
		int currentMinorVersion = Integer.parseInt(currentVersion[1]);
		int currentBuild = Integer.parseInt((currentVersion.length > 2) ? currentVersion[2] : "0");
		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("User-Agent", "HaroTorch Plugin v" + this.plugin.getDescription().getVersion());
		
		ResponseObject response;
		try {
			response = new Http().makeRequest(RequestMethod.GET, "https://api.github.com/repos/thedutchmc/harotorch/releases/latest", null, null, null, headers);
		} catch(IOException e) {
			HaroTorch.logWarn(String.format("An issue occurred while checking what the latest version of HaroTorch is: IOException (%s)", e.getMessage()));
			return;
		}
		
		if(response.getResponseCode() != 200) {
			HaroTorch.logWarn(String.format("Got a non-200 status code while checking what the latest version of HaroTorch is: HTTP-%d (%s)", response.getResponseCode(), response.getConnectionMessage()));
			return;
		}
		
		final Gson gson = new Gson();
		GithubResponse responseDeserialized = gson.fromJson(response.getMessage(), GithubResponse.class);
	
		String[] latestVersion = responseDeserialized.getTagName().split(Pattern.quote("."));
		int latestMajorVersion = Integer.parseInt(latestVersion[0]);
		int latestMinorVersion = Integer.parseInt(latestVersion[1]);
		int latestBuild = Integer.parseInt((latestVersion.length > 2) ? latestVersion[2] : "0");
		
		if(latestMajorVersion > currentMajorVersion) {
			updateAvailable(responseDeserialized.getUrl(), responseDeserialized.getTagName());
			return;
		}
		
		if(latestMinorVersion > currentMinorVersion) {
			updateAvailable(responseDeserialized.getUrl(), responseDeserialized.getTagName());
			return;
		}
		
		if(latestBuild > currentBuild) {
			updateAvailable(responseDeserialized.getUrl(), responseDeserialized.getTagName());
			return;
		}
		
		HaroTorch.logInfo("You are running the latest version of HaroTorch. Nice work! :D");
	}
	
	private void updateAvailable(String url, String latestVersion) {
		HaroTorch.logWarn(String.format("An update is available. You are running version %s, the latest version is %s. You can download it here: %s", this.plugin.getDescription().getVersion(), latestVersion, url));
	}
}
