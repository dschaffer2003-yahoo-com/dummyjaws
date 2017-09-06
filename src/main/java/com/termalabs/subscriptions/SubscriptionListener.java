package com.termalabs.subscriptions;

import static com.termalabs.subscriptions.SubscriptionConstants.*;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import com.termalabs.subscriptions.Subscription.SubscriptionType;

import rabbitmq.Prediction;

public class SubscriptionListener {


	private final Map<SubscriptionType, Set<URL>> subscriptions;
	private final ObjectMapper om;

	private Connection connection;

	public SubscriptionListener() throws IOException, TimeoutException {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		this.connection = factory.newConnection();
		this.subscriptions = new HashMap<>();
		Arrays.asList(SubscriptionType.values()).forEach(e -> subscriptions.put(e, new HashSet<>()));		
		this.om = new ObjectMapper();
	}

	private void listen() throws Exception {
		listenForAdds();
		listenForDeletes();
		listenForPredictions();	

	}

	private void listenForPredictions() throws Exception {
		Channel subscriptionChannel = connection.createChannel();

		subscriptionChannel.queueDeclare(PREDICTION_SUBSCRIPTION_QUEUE, true, false, false, null);

		Consumer subscriptionConsumer = new DefaultConsumer(subscriptionChannel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws IOException {
				Prediction prediction = om.readValue(body, Prediction.class);
				subscriptions.get(Subscription.SubscriptionType.PREDICTION)
				    .forEach(url->System.out.println("Sending prediction to URL " + prediction +  " " + url));
			}
		};
		subscriptionChannel.basicConsume(PREDICTION_SUBSCRIPTION_QUEUE, true, subscriptionConsumer);
		
	}

	private void listenForAdds() throws Exception {
		Channel subscriptionChannel = connection.createChannel();

		subscriptionChannel.queueDeclare(ADD_SUBSCRIPTION_QUEUE, true, false, false, null);

		Consumer subscriptionConsumer = new DefaultConsumer(subscriptionChannel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws IOException {
				Subscription subscription = om.readValue(body, Subscription.class);
				subscriptions.get(subscription.getType()).add(new URL(subscription.getUrl()));
				System.out.println(" Adding subscription " + subscription);
			}
		};
		subscriptionChannel.basicConsume(ADD_SUBSCRIPTION_QUEUE, true, subscriptionConsumer);

	}

	private void listenForDeletes() throws Exception {
		Channel subscriptionChannel = connection.createChannel();

		subscriptionChannel.queueDeclare(DELETE_SUBSCRIPTION_QUEUE, true, false, false, null);

		Consumer subscriptionConsumer = new DefaultConsumer(subscriptionChannel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws IOException {
				Subscription subscription = om.readValue(body, Subscription.class);
				subscriptions.get(subscription.getType()).remove(new URL(subscription.getUrl()));
				System.out.println(" Deleting subscription " + subscription);
			}
		};
		subscriptionChannel.basicConsume(DELETE_SUBSCRIPTION_QUEUE, true, subscriptionConsumer);

	}

	public static void main(String[] argv) throws Exception {
		SubscriptionListener subscriptionListener = new SubscriptionListener();
		subscriptionListener.listen();

	}
}