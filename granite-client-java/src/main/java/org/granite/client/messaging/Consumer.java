/**
 *   GRANITE DATA SERVICES
 *   Copyright (C) 2006-2015 GRANITE DATA SERVICES S.A.S.
 *
 *   This file is part of the Granite Data Services Platform.
 *
 *   Granite Data Services is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   Granite Data Services is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 *   General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 *   USA, or see <http://www.gnu.org/licenses/>.
 */
package org.granite.client.messaging;

import java.util.concurrent.ConcurrentHashMap;

import org.granite.client.messaging.channel.MessagingChannel;
import org.granite.client.messaging.channel.ResponseMessageFuture;
import org.granite.client.messaging.events.FailureEvent;
import org.granite.client.messaging.events.IssueEvent;
import org.granite.client.messaging.events.ResultEvent;
import org.granite.client.messaging.events.TopicMessageEvent;
import org.granite.client.messaging.messages.push.TopicMessage;
import org.granite.client.messaging.messages.requests.ReplyMessage;
import org.granite.client.messaging.messages.requests.SubscribeMessage;
import org.granite.client.messaging.messages.requests.UnsubscribeMessage;
import org.granite.logging.Logger;

/**
 * Consumer class that allows to receive messages from a remote pub/sub destination
 *
 * <pre>
 * {
 *     &#64;code
 *     Consumer consumer = new Consumer(messagingChannel, "myDestination", "myTopic");
 *     consumer.subscribe().get();
 *     consumer.addMessageListener(new TopicMessageListener() {
 * 	public void onMessage(TopicMessageEvent event) {
 * 	    System.out.println("Received: " + event.getData());
 * 	}
 *     });
 * }
 * </pre>
 *
 * @author Franck WOLFF
 */
public class Consumer extends AbstractTopicAgent {

    static final Logger log = Logger.getLogger(Consumer.class);

    private final ConcurrentHashMap<TopicMessageListener, Boolean> listeners = new ConcurrentHashMap<>();
    final ConcurrentHashMap<TopicSubscriptionListener, Boolean> subscriptionListeners = new ConcurrentHashMap<>();

    String subscriptionId = null;
    private String selector = null;

    /**
     * Create a consumer for the specified channel and destination
     *
     * @param channel messaging channel
     * @param destination remote destination
     * @param topic subtopic to which the producer sends its messages
     */
    public Consumer(MessagingChannel channel, String destination, String topic) {
	super(channel, destination, topic);
    }

    /**
     * Message selector for this consumer
     *
     * @return selector
     */
    public String getSelector() {
	return this.selector;
    }

    /**
     * Set the message selector for this consumer This must be set before subscribing to the destination It is necessary to resubscribe to the destination after changing its value
     *
     * @param selector selector
     */
    public void setSelector(String selector) {
	this.selector = selector;
    }

    /**
     * Is this consumer subscribed ?
     *
     * @return true if subscribed
     */
    public boolean isSubscribed() {
	return this.subscriptionId != null;
    }

    /**
     * Current subscription id received from the server
     *
     * @return subscription id
     */
    public String getSubscriptionId() {
	return this.subscriptionId;
    }

    /**
     * Subscribe to the remote destination
     *
     * @param listenersP array of listeners notified when the subscription is processed
     * @return future triggered when subscription is processed
     */
    public ResponseMessageFuture subscribe(ResponseListener... listenersP) {
	return subscribe(null, listenersP);
    }

    public ResponseMessageFuture resubscribe(ResponseListener... listenersP) {
	log.debug("Resubscribing consumer on channel %s with subcriptionId %s", this.channel.getClientId(), this.subscriptionId);
	return subscribe(this.subscriptionId, listenersP);
    }

    public ResponseMessageFuture subscribe(final String subscriptionIdP, ResponseListener... listenersP) {
	ResponseListener[] listenersL = listenersP;
	log.debug("Subscribing consumer on channel %s with subcriptionId %s", this.channel.getClientId(), subscriptionIdP);
	SubscribeMessage subscribeMessage = new SubscribeMessage(this.destination, this.topic, this.selector);
	subscribeMessage.getHeaders().putAll(this.defaultHeaders);
	if (subscriptionIdP != null) {
	    subscribeMessage.setSubscriptionId(subscriptionIdP);
	}

	final Consumer consumer = this;
	ResponseListener listener = new ResultIssuesResponseListener() {

	    @Override
	    public void onResult(ResultEvent event) {
		Consumer.this.subscriptionId = (String) event.getResult();

		if (Consumer.this.subscriptionId != null) {
		    log.debug("Subscription successful %s: %s", consumer, event);
		    Consumer.this.channel.addConsumer(consumer);

		    for (TopicSubscriptionListener subscriptionListener : Consumer.this.subscriptionListeners.keySet()) {
			subscriptionListener.onSubscriptionSuccess(Consumer.this, event, subscriptionIdP);
		    }
		} else {
		    log.error("Subscription failed %s: %s", consumer, event);

		    IssueEvent failureEvent = new FailureEvent(event.getRequest(), new IllegalStateException("Received null subscriptionId"));
		    for (TopicSubscriptionListener subscriptionListener : Consumer.this.subscriptionListeners.keySet()) {
			subscriptionListener.onSubscriptionFault(Consumer.this, failureEvent);
		    }
		}
	    }

	    @Override
	    public void onIssue(IssueEvent event) {
		log.error("Subscription failed %s: %s", consumer, event);

		for (TopicSubscriptionListener subscriptionListener : Consumer.this.subscriptionListeners.keySet()) {
		    subscriptionListener.onSubscriptionFault(Consumer.this, event);
		}
	    }
	};

	if ((listenersL == null) || (listenersL.length == 0)) {
	    listenersL = new ResponseListener[] { listener };
	} else {
	    ResponseListener[] tmp = new ResponseListener[listenersL.length + 1];
	    System.arraycopy(listenersL, 0, tmp, 1, listenersL.length);
	    tmp[0] = listener;
	    listenersL = tmp;
	}

	for (TopicSubscriptionListener subscriptionListener : this.subscriptionListeners.keySet()) {
	    subscriptionListener.onSubscribing(this);
	}

	return this.channel.send(subscribeMessage, listenersL);
    }

    /**
     * Unubscribe from the remote destination
     *
     * @param listenersP array of listeners notified when the unsubscription is processed
     * @return future triggered when unsubscription is processed
     */
    public ResponseMessageFuture unsubscribe(ResponseListener... listenersP) {
	ResponseListener[] listenersL = listenersP;

	if (!isSubscribed()) {
	    return null;
	}

	UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(this.destination, this.topic, this.subscriptionId);
	unsubscribeMessage.getHeaders().putAll(this.defaultHeaders);

	final Consumer consumer = this;
	ResponseListener listener = new ResultIssuesResponseListener() {

	    @Override
	    public void onResult(ResultEvent event) {
		Consumer.this.channel.removeConsumer(consumer);

		for (TopicSubscriptionListener subscriptionListener : Consumer.this.subscriptionListeners.keySet()) {
		    subscriptionListener.onUnsubscriptionSuccess(Consumer.this, event, Consumer.this.subscriptionId);
		}

		Consumer.this.subscriptionId = null;
	    }

	    @Override
	    public void onIssue(IssueEvent event) {
		log.error("Unsubscription failed %s: %s", consumer, event);
		Consumer.this.channel.removeConsumer(consumer);

		for (TopicSubscriptionListener subscriptionListener : Consumer.this.subscriptionListeners.keySet()) {
		    subscriptionListener.onUnsubscriptionFault(Consumer.this, event, Consumer.this.subscriptionId);
		}

		Consumer.this.subscriptionId = null;
	    }
	};

	if ((listenersL == null) || (listenersL.length == 0)) {
	    listenersL = new ResponseListener[] { listener };
	} else {
	    ResponseListener[] tmp = new ResponseListener[listenersL.length + 1];
	    System.arraycopy(listenersL, 0, tmp, 0, listenersL.length);
	    tmp[listenersL.length] = listener;
	    listenersL = tmp;
	}

	for (TopicSubscriptionListener subscriptionListener : this.subscriptionListeners.keySet()) {
	    subscriptionListener.onUnsubscribing(this);
	}

	return this.channel.send(unsubscribeMessage, listenersL);
    }

    /**
     * Register a message listener for this consumer
     *
     * @param listener message listener
     */
    public void addMessageListener(TopicMessageListener listener) {
	this.listeners.putIfAbsent(listener, Boolean.TRUE);
    }

    /**
     * Unregister a message listener for this consumer
     *
     * @param listener message listener
     * @return true if the listener was actually removed (if it was registered before)
     */
    public boolean removeMessageListener(TopicMessageListener listener) {
	return this.listeners.remove(listener) != null;
    }

    /**
     * Register a subscription listener for this consumer
     *
     * @param listener subscription listener
     */
    public void addSubscriptionListener(TopicSubscriptionListener listener) {
	this.subscriptionListeners.putIfAbsent(listener, Boolean.TRUE);
    }

    /**
     * Unregister a subscription listener for this consumer
     *
     * @param listener subscription listener
     * @return true if the listener was actually removed (if it was registered before)
     */
    public boolean removeSubscriptionListener(TopicSubscriptionListener listener) {
	return this.subscriptionListeners.remove(listener) != null;
    }

    /**
     * Called when the channel is disconnected
     */
    public void onDisconnect() {
	this.subscriptionId = null;
    }

    /**
     * Called when a message is received on the channel
     *
     * @param message message received
     */
    public void onMessage(TopicMessage message) {
	for (TopicMessageListener listener : this.listeners.keySet()) {
	    try {
		listener.onMessage(new TopicMessageEvent(this, message));
	    } catch (Exception e) {
		log.error(e, "Consumer listener threw an exception: ", listener);
	    }
	}
    }

    /**
     * Reply to a server-to-client request
     *
     * @param message incoming request message
     * @param reply response to send
     */
    public void reply(TopicMessage message, Object reply) {
	ReplyMessage replyMessage = new ReplyMessage(this.destination, this.topic, message.getId(), reply);
	replyMessage.getHeaders().putAll(message.getHeaders());
	this.channel.send(replyMessage);
    }

    @Override
    public String toString() {
	return getClass().getName() + " {subscriptionId=" + this.subscriptionId + ", destination=" + this.destination + ", topic=" + this.topic + ", selector=" + this.selector
		+ "}";
    }
}
