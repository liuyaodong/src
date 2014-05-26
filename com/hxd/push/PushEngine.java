package com.hxd.push;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.security.KeyStoreException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.hxd.push.APNLogEnvironment.APNLogLevel;

public class PushEngine implements NotificationProducerDelegate {
	private final Logger logger = LoggerFactory.getLogger(PushEngine.class);
	private final APNConfiguration configuration;
	private final APNManager apnManager;
	private final String payload;
	
	private PushEngine(final APNConfiguration configuration, final APNLogEnvironment logEnvironment) {
		this.logger.debug("configuration: " + configuration);
		this.configuration = configuration;
		APNPayloadBuilder payloadBuilder = new APNPayloadBuilder().alertBody(this.configuration.getAlertBody())
			    .badge(configuration.getBadge())
			    .sound("Default");
		if (this.configuration.getCustomField() != null && !this.configuration.getCustomField().isEmpty()) {
			payloadBuilder.customFields(this.configuration.getCustomField());
		}
		this.payload = payloadBuilder.build();
		this.logger.debug("payload: " + payload);
		
		APNManager manager = null;
		try {
			manager = new APNManager(this.configuration.isDebug() ? APNSEnviroment.getSandboxEnvironment() : APNSEnviroment.getProductionEnvironment(),
									this.configuration.getPkcs12(),
									this.configuration.getPassword(),
									logEnvironment);
		} catch (KeyStoreException e) {
			e.printStackTrace();
		}
		this.apnManager = manager;
	}
	
	private void doPush() {
		this.apnManager.start();
		NotificationProducer producer = new NotificationProducer(apnManager.getNotificationEnqueue(), this.configuration.getTokenFile(), this.payload, this);
		ExecutorService producerService = Executors.newSingleThreadExecutor();
		producerService.submit(producer);
		producerService.shutdown();
	}

	@Override
	public void producerDidComplete(NotificationProducer producer, Exception e) {
		String messageString = "Tokens have been processed";
		if (e != null) {
			messageString += " with exception: " + e.getMessage();
		}
		System.out.println(messageString);
		System.out.println("All connection will be closed in 3 minutes.");
		ScheduledExecutorService scheduleToTerminate = Executors.newSingleThreadScheduledExecutor();
		scheduleToTerminate.schedule(new Runnable() {
			@Override
			public void run() {
				System.out.println("Try to terminate");
				apnManager.stop();
			}
		}, 3 * 60, TimeUnit.SECONDS);
		scheduleToTerminate.shutdown();
	}
	
	public static void main(String args[]) {
		if (args.length != 1) {
			System.err.println("Wrong args list!");
			return;
		}
		
		String jsonFilePath = args[0];
		APNConfiguration configuration = null;
		try {
			FileReader jsonFileReader = new FileReader(new File(jsonFilePath));
			configuration = new Gson().fromJson(jsonFileReader, APNConfiguration.class);
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
		}
		
		if (configuration == null) {
			System.err.println("Illegal json file!");
			return;
		}

		APNLogEnvironment logEnvironment = new APNLogEnvironment(configuration.getLogPath());
		if (!logEnvironment.configure(APNLogLevel.LOG_LEVEL_DEBUG)) {
			System.err.println(String.format("Log environment configuration failed! Apn abort!"));
			return;
		}
		
		new PushEngine(configuration, logEnvironment).doPush();
	}
}
