package com.hxd.push;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BroadcastNotificationProducer implements Runnable {
	private final String tokenFilePath;
	private final PushController pushController;
	private final String payload;
	private final NotificationProducerDelegate delegate;
	private final Logger logger = LoggerFactory.getLogger(BroadcastNotificationProducer.class);
	
	private long notificationsProducted = 0;

	public BroadcastNotificationProducer(final PushController pushController, final String tokenFilePath, final String payload, final NotificationProducerDelegate delegate) {
		this.pushController = pushController;
		this.tokenFilePath = tokenFilePath;
		this.payload = payload;
		this.delegate = delegate;
	}

	@Override
	public void run() {
		BufferedReader bufferedReader = null;
		Exception caughtException = null;
		try {
			bufferedReader = new BufferedReader(new FileReader(new File(this.tokenFilePath)));
			String line = null;
			while( (line = bufferedReader.readLine()) != null ) {
				String token = line.trim().replace(" ", "");
				if (token.length() == 64) {
					this.pushController.doPush(token, this.payload);
					this.notificationsProducted++;
				} else {
					this.logger.warn("Illegal token: " + token);
				}
			}
			bufferedReader.close();
			this.logger.debug("All tokens have been sent!");
		} catch (InterruptedException e) {
			this.logger.debug("NotificationProducer aborted due to InterruptedException!");
			Thread.currentThread().interrupt();
			try {
				if (bufferedReader != null) {
					bufferedReader.close();
				}
			} catch (IOException innerException) {
				caughtException = innerException;
				innerException.printStackTrace();
			}
			
		} catch (FileNotFoundException e) {
			caughtException = e;
			e.printStackTrace();
		} catch (IOException e) {
			caughtException = e;
			e.printStackTrace();
		} finally {
			this.logger.info(this.notificationsProducted + " notifications enqueued!");
			System.out.println("\n" + this.notificationsProducted + " notifications enqueued!");
			this.delegate.producerDidComplete(this, caughtException);
		}
		
	}
	
}
