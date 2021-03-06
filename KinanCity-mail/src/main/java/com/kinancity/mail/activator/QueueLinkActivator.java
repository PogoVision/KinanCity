package com.kinancity.mail.activator;

import java.io.IOException;
import java.util.ArrayDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kinancity.mail.MailConstants;

import lombok.Setter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Class that will take care of following the activation link
 * 
 * @author drallieiv
 *
 */
public class QueueLinkActivator implements LinkActivator, Runnable {

	private static final int THROTTLE_PAUSE = 60000;
	private Logger fileLogger = LoggerFactory.getLogger("LINKS");
	private Logger logger = LoggerFactory.getLogger(getClass());

	private static final String SUCCESS_MSG = "Thank you for signing up! Your account is now active.";
	private static final String ALREADY_DONE_MSG = "Your account has already been activated.";
	private static final String INVALID_TOKEN_MSG = "We cannot find an account matching the confirmation email.";
	private static final String THROTTLE_MSG = "403 Forbidden";

	private okhttp3.OkHttpClient client;

	private ArrayDeque<String> linkQueue;

	@Setter
	private boolean stop = false;

	public QueueLinkActivator() {
		client = new OkHttpClient.Builder().build();
		linkQueue = new ArrayDeque<>();

		Thread process = new Thread(this);
		process.start();
	}

	public boolean activateLink(String link) {
		linkQueue.add(link);
		return true;
	}

	public boolean realActivateLink(String link) {
		try {

			logger.info("Start actvation of link : {}", link);

			Request request = new Request.Builder()
					.header(MailConstants.HEADER_USER_AGENT, MailConstants.CHROME_USER_AGENT)
					.url(link)
					.build();

			boolean isFinal = false;
			boolean success = true;

			while (!isFinal) {
				Response response = client.newCall(request).execute();
				String strResponse = response.body().string();

				// By default, stop
				isFinal = true;

				if (response.isSuccessful()) {
					if (strResponse.contains(SUCCESS_MSG)) {
						logger.info("Activation success : Your account is now active");
						fileLogger.info("{};OK", link);
					} else if (strResponse.contains(ALREADY_DONE_MSG)) {
						logger.info("Activation success : Activation already done");
						fileLogger.info("{};OK", link);
					} else if (strResponse.contains(INVALID_TOKEN_MSG)) {
						logger.error("Invalid Activation token");
						fileLogger.info("{};BAD", link);
						success = false;
					} else {
						logger.warn("OK response but missing confirmation.");
						logger.debug("Body : \n {}", strResponse);
					}
				} else {
					if (response.code() == 503 && strResponse.contains(THROTTLE_MSG)) {
						logger.warn("HTTP 503. Your validation request was throttled, wait 60s");
						isFinal = false;
						throttlePause();
					} else {
						logger.error("Unexpected Error {} : {}", response.code(), strResponse);
						fileLogger.info("{};ERROR", link);
						success = false;
					}

				}
			}

			return success;

		} catch (IOException e) {
			return false;
		}
	}

	public void throttlePause() {
		int split = 20;

		for (int i = 0; i < split; i++) {
			try {
				if (split > 0) {
					logger.info("...");
				}
				Thread.sleep(THROTTLE_PAUSE / split);
			} catch (InterruptedException e) {
				// Interrupted
				logger.warn("stoppped");
			}
		}
	}

	@Override
	public void run() {
		while (!stop || !linkQueue.isEmpty()) {
			if (linkQueue.isEmpty()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// Interrupted
					logger.warn("stoppped");
				}
			} else {
				String firstLink = linkQueue.pop();
				if (firstLink != null) {
					realActivateLink(firstLink);
				}
				logger.info("{} link to activate remaining", linkQueue.size());
			}
		}
	}
}
