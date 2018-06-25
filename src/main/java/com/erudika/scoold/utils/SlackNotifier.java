package com.erudika.scoold.utils;

import com.erudika.para.utils.Config;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

public class SlackNotifier {

	private static final Logger logger = LoggerFactory.getLogger(SlackNotifier.class);
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Config.EXECUTOR_THREADS);

	public SlackNotifier() {
	}

	public boolean sendNotification(final String email, final String message) {
		if (email == null) {
			return false;
		}
		asyncExecute(new Runnable() {
			public void run() {
				try {
					Client client = Client.create();
					ObjectMapper mapper = new ObjectMapper();
        			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

					WebResource webResource = client
						.resource("https://slack.com/api/users.lookupByEmail");
					webResource.header("Authorization", "Bearer " + System.getenv("SLACK_APP_DEVOPS"));
					webResource = webResource.queryParam("token", System.getenv("SLACK_APP_DEVOPS"));
					webResource = webResource.queryParam("email", email);

					ClientResponse response = webResource.type("application/x-www-form-urlencoded")
						.get(ClientResponse.class);

					String responseStr = response.getEntity(String.class);					
					SlackUser user = mapper.readValue(responseStr, SlackUser.class);

					webResource = client.resource("https://slack.com/api/conversations.open");
					webResource.header("Authorization", "Bearer " + System.getenv("BOT_TOKEN_ASKBOT"));
					webResource = webResource.queryParam("token", System.getenv("BOT_TOKEN_ASKBOT"));
					webResource = webResource.queryParam("users", user.user.id);
					response = webResource.type("application/x-www-form-urlencoded")
						.post(ClientResponse.class);

					responseStr = response.getEntity(String.class);
					SlackConversation conv = mapper.readValue(responseStr, SlackConversation.class);
					
					webResource = client.resource("https://slack.com/api/chat.postMessage");
					webResource.header("Authorization", "Bearer " + System.getenv("BOT_TOKEN_ASKBOT"));
					webResource = webResource.queryParam("token", System.getenv("BOT_TOKEN_ASKBOT"));
					webResource = webResource.queryParam("channel", conv.channel.id);
					webResource = webResource.queryParam("text",message);
					webResource = webResource.queryParam("as_user", "true");
					response = webResource.type("application/x-www-form-urlencoded")
						.post(ClientResponse.class);
				} catch (Exception ex) {
					logger.error("Slack Notification failed. {}", ex.getMessage());
				}
			}
		});
		return true;
	}

	private void asyncExecute(Runnable runnable) {
		if (runnable != null) {
			try {
				EXECUTOR.execute(runnable);
			} catch (RejectedExecutionException ex) {
				logger.warn(ex.getMessage());
				try {
					runnable.run();
				} catch (Exception e) {
					logger.error(null, e);
				}
			}
		}
	}

	static class SlackUser{
		public SlackUserUser user;
	}

	static class SlackUserUser{
		public String id;
	}

	static class SlackConversation{
		public SlackChannel channel;
	}

	static class SlackChannel{
		public String id;
	}
}
