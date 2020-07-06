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
package org.granite.client.messaging.channel.amf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.granite.client.messaging.Consumer;
import org.granite.client.messaging.ResponseListener;
import org.granite.client.messaging.channel.AsyncToken;
import org.granite.client.messaging.channel.Channel;
import org.granite.client.messaging.channel.ChannelException;
import org.granite.client.messaging.channel.MessagingChannel;
import org.granite.client.messaging.channel.MessagingChannel.ChannelResponseListener;
import org.granite.client.messaging.channel.ResponseMessageFuture;
import org.granite.client.messaging.codec.MessagingCodec;
import org.granite.client.messaging.messages.Message.Type;
import org.granite.client.messaging.messages.RequestMessage;
import org.granite.client.messaging.messages.ResponseMessage;
import org.granite.client.messaging.messages.requests.DisconnectMessage;
import org.granite.client.messaging.messages.requests.LoginMessage;
import org.granite.client.messaging.messages.responses.AbstractResponseMessage;
import org.granite.client.messaging.messages.responses.FaultMessage;
import org.granite.client.messaging.messages.responses.ResultMessage;
import org.granite.client.messaging.transport.DefaultTransportMessage;
import org.granite.client.messaging.transport.Transport;
import org.granite.client.messaging.transport.TransportMessage;
import org.granite.logging.Logger;
import org.granite.util.UUIDUtil;

import flex.messaging.messages.AcknowledgeMessage;
import flex.messaging.messages.AsyncMessage;
import flex.messaging.messages.CommandMessage;
import flex.messaging.messages.Message;

/**
 * @author Franck WOLFF
 */
public class BaseAMFMessagingChannel extends AbstractAMFChannel implements MessagingChannel {

    static final Logger log = Logger.getLogger(BaseAMFMessagingChannel.class);

    protected final MessagingCodec<Message[]> codec;

    protected volatile String sessionId = null;

    protected final ConcurrentMap<String, Consumer> consumersMap = new ConcurrentHashMap<>();
    protected final AtomicReference<String> connectMessageId = new AtomicReference<>(null);
    protected final AtomicReference<String> loginMessageId = new AtomicReference<>(null);
    protected final AtomicReference<ReconnectTimerTask> reconnectTimerTask = new AtomicReference<>();
    protected final List<ChannelResponseListener> responseListeners = new ArrayList<>();

    protected volatile long reconnectIntervalMillis = TimeUnit.SECONDS.toMillis(30L);
    protected boolean reconnectMaxAttemptsSet = false;
    protected volatile long reconnectMaxAttempts = 60L;
    protected volatile long reconnectAttempts = 0L;

    public BaseAMFMessagingChannel(MessagingCodec<Message[]> codec, Transport transport, String id, URI uri) {
	super(transport, id, uri, 1);

	this.codec = codec;
    }

    @Override
    public void setSessionId(String sessionId) {
	if (((sessionId == null) && (this.sessionId != null)) || ((sessionId != null) && !sessionId.equals(this.sessionId))) {
	    this.sessionId = sessionId;
	    log.info("Messaging channel %s set sessionId %s", this.clientId, sessionId);
	}
    }

    @Override
    public void setDefaultMaxReconnectAttempts(long reconnectMaxAttempts) {
	this.reconnectMaxAttempts = reconnectMaxAttempts;
	this.reconnectMaxAttemptsSet = true;
    }

    @Override
    public void preconnect() throws ChannelException {
	executeReauthenticateCallback();
    }

    protected boolean connect() {

	// Connecting: make sure we don't have an active reconnect timer task.
	cancelReconnectTimerTask();

	// No subscriptions...
	if (this.consumersMap.isEmpty()) {
	    return false;
	}

	// We are already waiting for a connection/answer.
	final String idL = UUIDUtil.randomUUID();
	if (!this.connectMessageId.compareAndSet(null, idL)) {
	    return false;
	}

	log.debug("Connecting channel with clientId %s", this.clientId);

	// Create and try to send the connect message.
	try {
	    preconnect();

	    this.transport.send(this, createConnectMessage(idL, false));

	    return true;
	} catch (Exception e) {
	    // Connect immediately failed, release the message id and schedule a reconnect.
	    this.connectMessageId.set(null);
	    this.loginMessageId.set(null);
	    scheduleReconnectTimerTask(false);

	    return false;
	}
    }

    @Override
    protected void postSetAuthenticated(boolean authenticated, ResponseMessage response) {
	// Force disconnection for streaming transports to ensure next calls are in a new session/authentication context
	if (!authenticated && (response instanceof FaultMessage) && this.transport.isDisconnectAfterAuthenticationFailure()) {
	    log.debug("Channel clientId %s force disconnection after unauthentication (new sessionId %s)", this.clientId, this.sessionId);
	    disconnect();
	}
    }

    @Override
    public ResponseMessageFuture logout(boolean sendLogout, ResponseListener... listeners) {
	ResponseMessageFuture future = super.logout(sendLogout, listeners);

	// Force disconnection for streaming transports to ensure next calls are in a new session/authentication context
	if (sendLogout) {
	    disconnect();
	}

	return future;
    }

    @Override
    public void addConsumer(Consumer consumer) {
	this.consumersMap.putIfAbsent(consumer.getSubscriptionId(), consumer);

	connect();
    }

    @Override
    public boolean removeConsumer(Consumer consumer) {
	String subscriptionId = consumer.getSubscriptionId();
	if (subscriptionId == null) {
	    for (String sid : this.consumersMap.keySet()) {
		if (this.consumersMap.get(sid) == consumer) {
		    subscriptionId = sid;
		    break;
		}
	    }
	}
	if (subscriptionId == null) {
	    log.warn("Channel %s trying to remove unexisting consumer for destination %s", this.id, consumer.getDestination());
	    return false;
	}
	return this.consumersMap.remove(subscriptionId) != null;
    }

    @Override
    public void addListener(ChannelResponseListener listener) {
	this.responseListeners.add(listener);
    }

    @Override
    public void removeListener(ChannelResponseListener listener) {
	this.responseListeners.remove(listener);
    }

    @Override
    public synchronized ResponseMessageFuture disconnect(ResponseListener... listeners) {
	cancelReconnectTimerTask();

	for (Consumer consumer : this.consumersMap.values()) {
	    consumer.onDisconnect();
	}

	this.consumersMap.clear();

	this.connectMessageId.set(null);
	this.loginMessageId.set(null);
	this.reconnectAttempts = 0L;

	if (isStarted()) {
	    return send(new DisconnectMessage(this.clientId), listeners);
	}
	return null;
    }

    @Override
    protected TransportMessage createTransportMessage(AsyncToken token) throws UnsupportedEncodingException {
	Message[] messages = convertToAmf(token.getRequest());
	return new DefaultTransportMessage<>(token.getId(), false, token.isDisconnectRequest(), this.clientId, this.sessionId, messages, this.codec);
    }

    @Override
    protected ResponseMessage decodeResponse(InputStream is) throws IOException {

	boolean reconnect = false;
	AbstractResponseMessage responseChain = null;
	AbstractResponseMessage currentResponse = null;

	try {
	    if (is.available() > 0) {
		final Message[] messages = this.codec.decode(is);

		log.debug("Channel %s: received %d messages", this.clientId, messages.length);

		for (Message message : messages) {

		    if (message instanceof AcknowledgeMessage) {
			AbstractResponseMessage response = convertFromAmf((AcknowledgeMessage) message);

			if (response instanceof ResultMessage) {
			    log.debug("Channel %s received result %s of type %s correlationId %s", this.clientId, response.getId(), response.getType().name(),
				    response.getCorrelationId());

			    Type requestType = null;
			    RequestMessage request = getRequest(response.getCorrelationId());
			    if (request != null) {
				requestType = request.getType();
			    } else if (response.getCorrelationId().equals(this.connectMessageId.get())) { // Reconnect
				requestType = Type.PING;
				response.setProcessed();
			    } else if (response.getCorrelationId().equals(this.loginMessageId.get())) {
				requestType = Type.LOGIN;
			    }

			    if (requestType != null) {
				ResultMessage result = (ResultMessage) response;
				switch (requestType) {

				case PING:
				    if (messages[0].getBody() instanceof Map) {
					Map<?, ?> advices = (Map<?, ?>) messages[0].getBody();
					Object reconnectIntervalMillisL = advices.get(Channel.RECONNECT_INTERVAL_MS_KEY);
					if (reconnectIntervalMillisL instanceof Number) {
					    this.reconnectIntervalMillis = ((Number) reconnectIntervalMillisL).longValue();
					}
					Object reconnectMaxAttemptsL = advices.get(Channel.RECONNECT_MAX_ATTEMPTS_KEY);
					if ((reconnectMaxAttemptsL instanceof Number) && !this.reconnectMaxAttemptsSet) {
					    this.reconnectMaxAttempts = ((Number) reconnectMaxAttemptsL).longValue();
					}
				    }

				    // Successful ping, reinitialize reconnect counter
				    this.reconnectAttempts = 0L;

				    if (messages[0].getHeaders().containsKey("JSESSIONID")) {
					setSessionId((String) messages[0].getHeader("JSESSIONID"));
				    }

				    boolean resubscribe = false;
				    if ((this.clientId != null) && !this.clientId.equals(result.getClientId())) {
					log.warn("Channel %s ping successful new clientId %s current %s requested %s", this.id, result.getClientId(), this.clientId,
						request != null ? result.getClientId() : "(no request)");
					resubscribe = true;
				    } else {
					log.debug("Channel %s ping successful clientId %s current %s requested %s", this.id, result.getClientId(), this.clientId,
						request != null ? result.getClientId() : "(no request)");
				    }

				    this.clientId = result.getClientId();
				    setPinged(true);

				    LoginMessage loginMessage = authenticate(null);
				    if (loginMessage != null) {
					this.loginMessageId.set(loginMessage.getId());
				    } else if (resubscribe) {
					for (Consumer consumer : this.consumersMap.values()) {
					    consumer.resubscribe();
					}
				    }

				    break;

				case LOGIN:
				    log.debug("Channel %s authentication successful clientId %s", this.id, this.clientId);

				    setAuthenticated(true, response);

				    for (Consumer consumer : this.consumersMap.values()) {
					consumer.resubscribe();
				    }

				    break;

				case SUBSCRIBE:
				    String subscriptionId = (String) messages[0].getHeader(AsyncMessage.DESTINATION_CLIENT_ID_HEADER);

				    log.debug("Channel %s subscription successful clientId %s subscriptionId %s", this.id, this.clientId, subscriptionId);

				    result.setResult(subscriptionId);
				    break;

				// $CASES-OMITTED$
				default:
				    break;
				}
			    }
			} else if (response instanceof FaultMessage) {
			    log.debug("Channel %s received fault %s of type %s correlationId %s", this.clientId, response.getId(), response.getType().name(),
				    response.getCorrelationId());

			    Type requestType = null;
			    RequestMessage request = getRequest(response.getCorrelationId());
			    if (request != null) {
				requestType = request.getType();
			    } else if (response.getCorrelationId().equals(this.connectMessageId.get())) { // Reconnect
				requestType = Type.PING;
				response.setProcessed();
			    } else if (response.getCorrelationId().equals(this.loginMessageId.get())) {
				requestType = Type.LOGIN;
			    }

			    if (requestType != null) {
				switch (requestType) {

				case PING:
				    log.warn("Channel %s ping failed current clientId %s requested %s", this.id, this.clientId,
					    request != null ? request.getClientId() : "(no request)");

				    this.clientId = null;
				    setPinged(false);

				case LOGIN:
				    log.warn("Channel %s authentication failed current clientId %s requested %s", this.id, this.clientId,
					    request != null ? request.getClientId() : "(no request)");

				    setAuthenticated(false, response);

				    if (this.transport.isDisconnectAfterAuthenticationFailure()) {
					log.debug("Channel clientId %s force disconnection after authentication failure", this.clientId);
					disconnect();
				    }

				    break;

				// $CASES-OMITTED$
				default:
				    break;
				}
			    }

			    dispatchFault((FaultMessage) response);
			}

			if (responseChain == null) {
			    responseChain = currentResponse = response;
			} else {
			    currentResponse.setNext(response);
			    currentResponse = response;
			}
		    }
		}

		if (responseChain != null) {
		    for (ChannelResponseListener listener : this.responseListeners) {
			listener.onResponse(responseChain);
		    }
		}

		for (Message message : messages) {
		    if (!(message instanceof AcknowledgeMessage)) {
			reconnect = this.transport.isReconnectAfterReceive();

			if (!(message instanceof AsyncMessage)) {
			    throw new RuntimeException("Message should be an AsyncMessage: " + message);
			}

			String subscriptionId = (String) message.getHeader(AsyncMessage.DESTINATION_CLIENT_ID_HEADER);
			Consumer consumer = this.consumersMap.get(subscriptionId);
			if (consumer != null) {
			    consumer.onMessage(convertFromAmf((AsyncMessage) message));
			} else {
			    log.warn("Channel %s: no consumer for subscriptionId: %s", this.clientId, subscriptionId);
			}
		    }
		}
	    } else {
		reconnect = this.transport.isReconnectAfterReceive();
	    }
	} finally {
	    if (reconnect) {
		this.connectMessageId.set(null);
		this.loginMessageId.set(null);
		connect();
	    }
	}

	return responseChain;
    }

    @Override
    protected void internalStop() {
	super.internalStop();

	cancelReconnectTimerTask();
    }

    @Override
    public void onError(TransportMessage message, Exception e) {
	if (!isStarted()) {
	    return;
	}

	super.onError(message, e);

	// Mark consumers as unsubscribed
	// Should maybe not do it once consumers auto resubscribe after disconnect
	// for (Consumer consumer : consumersMap.values())
	// consumer.onDisconnect();

	// Don't reconnect here, there should be a following onClose
	if ((message != null) && this.connectMessageId.compareAndSet(message.getId(), null)) {
	    scheduleReconnectTimerTask(false);
	}
    }

    protected void cancelReconnectTimerTask() {
	ReconnectTimerTask task = this.reconnectTimerTask.getAndSet(null);
	if ((task != null) && task.cancel()) {
	    this.reconnectAttempts = 0L;
	}
    }

    @Override
    public TransportMessage createConnectMessage(String idP, boolean reconnect) {
	CommandMessage connectMessage = new CommandMessage();
	connectMessage.setOperation(CommandMessage.CONNECT_OPERATION);
	connectMessage.setMessageId(idP);
	connectMessage.setTimestamp(System.currentTimeMillis());
	connectMessage.setClientId(this.clientId);

	return new DefaultTransportMessage<>(idP, !reconnect, false, this.clientId, this.sessionId, new Message[] { connectMessage }, this.codec);
    }

    protected void scheduleReconnectTimerTask(boolean immediate) {
	setPinged(false);
	setAuthenticated(false, null);

	ReconnectTimerTask task = new ReconnectTimerTask();

	ReconnectTimerTask previousTask = this.reconnectTimerTask.getAndSet(task);
	if (previousTask != null) {
	    previousTask.cancel();
	}

	if ((this.reconnectMaxAttempts <= 0) || (this.reconnectAttempts < this.reconnectMaxAttempts)) {
	    this.reconnectAttempts++;

	    log.info("Channel %s schedule reconnect (retry #%d / %d)", getId(), this.reconnectAttempts, this.reconnectMaxAttempts);

	    schedule(task, immediate && (this.reconnectAttempts == 1) ? 0L : this.reconnectIntervalMillis);
	} else {
	    log.error("Channel %s max number of reconnects (%d) reached", getId(), this.reconnectMaxAttempts);
	}
    }

    class ReconnectTimerTask extends TimerTask {

	@Override
	public void run() {
	    log.info("Channel %s reconnecting (retry #%d / %d)", getId(), BaseAMFMessagingChannel.this.reconnectAttempts, BaseAMFMessagingChannel.this.reconnectMaxAttempts);
	    connect();
	}
    }

}
