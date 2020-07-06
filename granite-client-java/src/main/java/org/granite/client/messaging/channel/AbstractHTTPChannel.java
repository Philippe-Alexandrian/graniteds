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
package org.granite.client.messaging.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.granite.client.messaging.AllInOneResponseListener;
import org.granite.client.messaging.ResponseListener;
import org.granite.client.messaging.ResponseListenerDispatcher;
import org.granite.client.messaging.events.Event;
import org.granite.client.messaging.events.Event.Type;
import org.granite.client.messaging.messages.MessageChain;
import org.granite.client.messaging.messages.RequestMessage;
import org.granite.client.messaging.messages.ResponseMessage;
import org.granite.client.messaging.messages.requests.DisconnectMessage;
import org.granite.client.messaging.messages.requests.LoginMessage;
import org.granite.client.messaging.messages.requests.LogoutMessage;
import org.granite.client.messaging.messages.requests.PingMessage;
import org.granite.client.messaging.messages.responses.FaultMessage;
import org.granite.client.messaging.messages.responses.ResultMessage;
import org.granite.client.messaging.transport.Transport;
import org.granite.client.messaging.transport.TransportFuture;
import org.granite.client.messaging.transport.TransportMessage;
import org.granite.client.messaging.transport.TransportStopListener;
import org.granite.logging.Logger;

/**
 * @author Franck WOLFF
 */
public abstract class AbstractHTTPChannel extends AbstractChannel<Transport> implements TransportStopListener, Runnable {

    static final Logger log = Logger.getLogger(AbstractHTTPChannel.class);

    private final BlockingQueue<AsyncToken> tokensQueue = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, AsyncToken> tokensMap = new ConcurrentHashMap<>();
    private final AsyncToken stopToken = new AsyncToken(new DisconnectMessage());
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private AsyncToken disconnectToken = null;

    private Thread senderThread = null;
    private Semaphore connections;
    private Timer timer = null;

    private List<ChannelStatusListener> statusListeners = new ArrayList<>();
    volatile boolean pinged = false;
    volatile boolean authenticated = false;
    private ReentrantLock authenticationLock = new ReentrantLock();
    volatile boolean authenticating = false;
    private ReauthenticateCallback reauthenticateCallback = null;
    protected volatile int maxConcurrentRequests;
    protected volatile long defaultTimeToLive = DEFAULT_TIME_TO_LIVE; // 1 mn.

    public AbstractHTTPChannel(Transport transport, String id, URI uri, int maxConcurrentRequests) {
	super(transport, id, uri);

	if (maxConcurrentRequests < 1) {
	    throw new IllegalArgumentException("maxConcurrentRequests must be greater or equal to 1");
	}

	this.maxConcurrentRequests = maxConcurrentRequests;
    }

    protected abstract TransportMessage createTransportMessage(AsyncToken token) throws UnsupportedEncodingException;

    protected abstract ResponseMessage decodeResponse(InputStream is) throws IOException;

    protected boolean schedule(TimerTask timerTask, long delay) {
	if (this.timer != null) {
	    try {
		this.timer.schedule(timerTask, delay);
	    } catch (IllegalStateException e) {
		log.error(e, "Timer has been cancelled, starting new timer");
		this.timer = null;
		this.timer = new Timer(this.id + "_timer", true);
		this.timer.schedule(timerTask, delay);
	    }
	    return true;
	}
	return false;
    }

    @Override
    public long getDefaultTimeToLive() {
	return this.defaultTimeToLive;
    }

    @Override
    public void setDefaultTimeToLive(long defaultTimeToLive) {
	this.defaultTimeToLive = defaultTimeToLive;
    }

    @Override
    public boolean isAuthenticated() {
	return this.authenticated;
    }

    public void setReauthenticateCallback(ReauthenticateCallback callback) {
	this.reauthenticateCallback = callback;
    }

    public int getMaxConcurrentRequests() {
	return this.maxConcurrentRequests;
    }

    @Override
    public void onStop(Transport transportP) {
	stop();
    }

    @Override
    public synchronized boolean start() {
	if (this.senderThread != null) {
	    return true;
	}

	this.tokensQueue.clear();
	this.tokensMap.clear();
	this.disconnectToken = null;
	this.stopped.set(false);

	log.info("Starting channel %s...", this.id);
	this.senderThread = new Thread(this, this.id + "_sender");
	try {
	    this.timer = new Timer(this.id + "_timer", true);
	    this.connections = new Semaphore(this.maxConcurrentRequests);
	    this.senderThread.start();

	    this.transport.addStopListener(this);

	    log.info("Channel %s started.", this.id);
	} catch (Exception e) {
	    if (this.timer != null) {
		this.timer.cancel();
		this.timer = null;
	    }
	    this.connections = null;
	    this.senderThread = null;
	    log.error(e, "Channel %s failed to start.", this.id);
	    return false;
	}
	return true;
    }

    @Override
    public synchronized boolean isStarted() {
	return this.senderThread != null;
    }

    @Override
    public synchronized boolean stop() {
	if (this.senderThread == null) {
	    return false;
	}

	log.info("Stopping channel %s...", this.id);

	if (this.timer != null) {
	    try {
		this.timer.cancel();
	    } catch (Exception e) {
		log.error(e, "Channel %s timer failed to stop.", this.id);
	    } finally {
		this.timer = null;
	    }
	}

	internalStop();

	log.debug("Interrupting thread %s", this.id);

	this.connections = null;
	this.tokensMap.clear();
	this.tokensQueue.clear();
	this.disconnectToken = null;

	this.stopped.set(true);
	this.tokensQueue.add(this.stopToken);

	Thread thread = this.senderThread;
	this.senderThread = null;
	thread.interrupt();

	this.pinged = false;
	this.clientId = null;
	setAuthenticated(false, null);

	return true;
    }

    protected void internalStop() {
	// Empty.
    }

    public void reauthenticate() throws ChannelException {
	Credentials credentialsL = this.credentials;
	if (credentialsL == null) {
	    return;
	}

	log.debug("Channel %s client %s reauthenticating", getId(), this.clientId);
	ChannelException channelException = null;
	try {
	    this.authenticationLock.lock(); // Avoid multiple simultaneous blocking login calls
	    if (this.authenticating || this.authenticated) {
		return;
	    }

	    try {
		this.authenticating = true;
		LoginMessage loginMessage = new LoginMessage(this.clientId, credentialsL);
		ResponseMessage response = sendSimpleBlockingToken(loginMessage);
		if (response instanceof ResultMessage) {
		    setAuthenticated(true, response);
		} else if (response instanceof FaultMessage) {
		    FaultMessage fault = (FaultMessage) response;
		    channelException = new ChannelException(this.clientId,
			    "Could not reauthenticate channel " + this.clientId + " , fault " + fault.getCode() + " " + fault.getDescription() + " " + fault.getDetails());
		} else {
		    channelException = new ChannelException(this.clientId, "Could not reauthenticate channel " + this.clientId);
		}
	    } finally {
		this.authenticating = false;
	    }
	} catch (Exception e) {
	    channelException = new ChannelException(this.clientId, "Could not reauthenticate channel " + this.clientId, e);
	} finally {
	    this.authenticationLock.unlock();
	}
	if (channelException != null) {
	    throw channelException;
	}
    }

    protected LoginMessage authenticate(AsyncToken dependentToken) {
	Credentials credentialsL = this.credentials;
	if (credentialsL == null) {
	    return null;
	}

	LoginMessage loginMessage = new LoginMessage(this.clientId, credentialsL);
	if (dependentToken != null) {
	    log.debug("Channel %s blocking authentication %s clientId %s", this.id, loginMessage.getId(), this.clientId);
	    try {
		this.authenticationLock.lock(); // Avoid multiple simultaneous blocking login calls
		if (this.authenticating || this.authenticated) {
		    return null;
		}

		try {
		    this.authenticating = true;
		    ResultMessage result = sendBlockingToken(loginMessage, dependentToken);
		    if (result == null) {
			return loginMessage;
		    }
		    setAuthenticated(true, result);
		} finally {
		    this.authenticating = false;
		}
	    } finally {
		this.authenticationLock.unlock();
	    }
	} else {
	    if (this.authenticating || this.authenticated) {
		return null;
	    }

	    log.debug("Channel %s non blocking authentication %s clientId %s", this.id, loginMessage.getId(), this.clientId);
	    send(loginMessage);
	}
	return loginMessage;
    }

    protected void executeReauthenticateCallback() throws ChannelException {
	// Force reauthentication from the remoting channel before connecting if this channel is not able to authenticate itself (i.e. websockets)
	if (this.transport.isAuthenticationAfterReconnectWithRemoting() && (this.reauthenticateCallback != null)) {
	    log.debug("Channel clientId %s force reauthentication with remoting channel", this.clientId);
	    this.reauthenticateCallback.reauthenticate();
	}
    }

    @Override
    public void run() {

	while (!Thread.interrupted()) {
	    try {
		if (this.stopped.get()) {
		    break;
		}

		AsyncToken token = this.tokensQueue.take();
		if (token == this.stopToken) {
		    break;
		}

		if (token.isDone()) {
		    continue;
		}

		if (token.isDisconnectRequest()) {
		    sendToken(token);
		    continue;
		}

		executeReauthenticateCallback();

		if (!this.pinged) {
		    PingMessage pingMessage = new PingMessage(this.clientId);
		    log.debug("Channel %s send ping %s with clientId %s", this.id, pingMessage.getId(), this.clientId);
		    ResultMessage result = sendBlockingToken(pingMessage, token);
		    if (result == null) {
			continue;
		    }
		    this.clientId = result.getClientId();
		    log.debug("Channel %s pinged clientId %s", this.id, this.clientId);
		    this.pinged = true;
		}

		authenticate(token);

		if (!(token.getRequest() instanceof PingMessage)) {
		    sendToken(token);
		}
	    } catch (InterruptedException e) {
		log.info("Channel %s stopped.", this.id);
		break;
	    } catch (Exception e) {
		log.error(e, "Channel %s got an unexpected exception.", this.id);
	    }
	}
    }

    private ResultMessage sendBlockingToken(RequestMessage request, AsyncToken dependentToken) {

	// Make this blocking request share the timeout/timeToLive values of the dependent token.
	request.setTimestamp(dependentToken.getRequest().getTimestamp());
	request.setTimeToLive(dependentToken.getRequest().getTimeToLive());

	// Create the blocking token and schedule it with the dependent token timeout.
	AsyncToken blockingToken = new AsyncToken(request);
	try {
	    schedule(blockingToken, blockingToken.getRequest().getRemainingTimeToLive());
	} catch (IllegalArgumentException e) {
	    dependentToken.dispatchTimeout(System.currentTimeMillis());
	    return null;
	} catch (Exception e) {
	    dependentToken.dispatchFailure(e);
	    return null;
	}

	// Try to send the blocking token (can block if the connections semaphore can't be acquired
	// immediately).
	try {
	    if (!sendToken(blockingToken)) {
		return null;
	    }
	} catch (Exception e) {
	    dependentToken.dispatchFailure(e);
	    return null;
	}

	// Block until we get a server response (result or fault), a cancellation (unlikely), a timeout
	// or any other execution exception.
	try {
	    ResponseMessage response = blockingToken.get();

	    // Request was successful, return a non-null result.
	    if (response instanceof ResultMessage) {
		return (ResultMessage) response;
	    }

	    if (response instanceof FaultMessage) {
		FaultMessage faultMessage = (FaultMessage) response.copy(dependentToken.getRequest().getId());
		if (dependentToken.getRequest() instanceof MessageChain) {
		    ResponseMessage nextResponse = faultMessage;
		    for (MessageChain<?> nextRequest = ((MessageChain<?>) dependentToken.getRequest()).getNext(); nextRequest != null; nextRequest = nextRequest.getNext()) {
			nextResponse.setNext(response.copy(nextRequest.getId()));
			nextResponse = nextResponse.getNext();
		    }
		}
		dependentToken.dispatchFault(faultMessage);
	    } else {
		throw new RuntimeException("Unknown response message type: " + response);
	    }

	} catch (InterruptedException e) {
	    dependentToken.dispatchFailure(e);
	} catch (TimeoutException e) {
	    dependentToken.dispatchTimeout(System.currentTimeMillis());
	} catch (ExecutionException e) {
	    if (e.getCause() instanceof Exception) {
		dependentToken.dispatchFailure((Exception) e.getCause());
	    } else {
		dependentToken.dispatchFailure(e);
	    }
	} catch (Exception e) {
	    dependentToken.dispatchFailure(e);
	}

	return null;
    }

    private ResponseMessage sendSimpleBlockingToken(RequestMessage request) throws Exception {

	request.setTimestamp(System.currentTimeMillis());
	if (request.getTimeToLive() <= 0L) {
	    request.setTimeToLive(this.defaultTimeToLive);
	}

	// Create the blocking token and schedule it with the dependent token timeout.
	AsyncToken blockingToken = new AsyncToken(request);
	try {
	    schedule(blockingToken, blockingToken.getRequest().getRemainingTimeToLive());
	} catch (IllegalArgumentException e) {
	    return null;
	} catch (Exception e) {
	    return null;
	}

	// Try to send the blocking token (can block if the connections semaphore can't be acquired
	// immediately).
	try {
	    if (!sendToken(blockingToken)) {
		return null;
	    }
	} catch (Exception e) {
	    throw e;
	}

	// Block until we get a server response (result or fault), a cancellation (unlikely), a timeout
	// or any other execution exception.
	try {
	    return blockingToken.get();
	} catch (Exception e) {
	    throw e;
	}
    }

    private boolean sendToken(final AsyncToken token) {

	boolean releaseConnections = false;
	try {
	    // Block until a connection is available.
	    if (!this.connections.tryAcquire(token.getRequest().getRemainingTimeToLive(), TimeUnit.MILLISECONDS)) {
		token.dispatchTimeout(System.currentTimeMillis());
		return false;
	    }

	    // Semaphore was successfully acquired, we must release it in the finally block unless we succeed in
	    // sending the data (see below).
	    releaseConnections = true;

	    // Check if the token has already received an event (likely a timeout or a cancellation).
	    if (token.isDone()) {
		return false;
	    }

	    // Make sure we have set a clientId (can be null for ping message).
	    token.getRequest().setClientId(this.clientId);

	    if (token.getRequest() instanceof DisconnectMessage) {
		// Store disconnect token
		this.disconnectToken = token;
	    } else {
		// Add the token to active tokens map.
		if (this.tokensMap.putIfAbsent(token.getId(), token) != null) {
		    throw new RuntimeException("MessageId isn't unique: " + token.getId());
		}
	    }

	    log.debug("Channel %s send %s message id %s", this.clientId, token.getRequest().getType().name(), token.getRequest().getId());

	    // Actually send the message content.
	    TransportFuture transportFuture = this.transport.send(this, createTransportMessage(token));

	    // Create and try to set a channel listener: if no event has been dispatched for this token (tokenEvent == null),
	    // the listener will be called on the next event. Otherwise, we just call the listener immediately.
	    ResponseListener channelListener = new ChannelResponseListener(token.getId(), this.tokensMap, transportFuture, this.connections);
	    Event tokenEvent = token.setChannelListener(channelListener);
	    if (tokenEvent != null) {
		ResponseListenerDispatcher.dispatch(channelListener, tokenEvent);
	    }

	    // Message was sent and we were able to handle everything ourself.
	    releaseConnections = false;

	    return true;
	} catch (Exception e) {
	    this.tokensMap.remove(token.getId());
	    token.dispatchFailure(e);
	    if (this.timer != null) {
		this.timer.purge(); // Must purge to cleanup timer references to AsyncToken
	    }
	    return false;
	} finally {
	    if (releaseConnections && (this.connections != null)) {
		this.connections.release();
	    }
	}
    }

    protected RequestMessage getRequest(String idP) {
	AsyncToken token = this.tokensMap.get(idP);
	return (token != null ? token.getRequest() : null);
    }

    @Override
    public ResponseMessageFuture send(RequestMessage request, ResponseListener... listeners) {
	if (request == null) {
	    throw new NullPointerException("request cannot be null");
	}

	if (!start()) {
	    throw new RuntimeException("Channel not started");
	}

	AsyncToken token = new AsyncToken(request, listeners);

	request.setTimestamp(System.currentTimeMillis());
	if (request.getTimeToLive() <= 0L) {
	    request.setTimeToLive(this.defaultTimeToLive);
	}

	try {
	    log.debug("Client %s schedule request %s", this.clientId, request.getId());
	    schedule(token, request.getRemainingTimeToLive());
	    this.tokensQueue.add(token);
	} catch (Exception e) {
	    log.error(e, "Could not add token to queue: %s", token);
	    token.dispatchFailure(e);
	    return new ImmediateFailureResponseMessageFuture(e);
	}

	return token;
    }

    @Override
    public ResponseMessageFuture logout(ResponseListener... listeners) {
	return logout(true, listeners);
    }

    @Override
    public ResponseMessageFuture logout(boolean sendLogout, ResponseListener... listeners) {
	log.info("Logging out channel %s", this.clientId);

	clearCredentials();
	setAuthenticated(false, null);
	if (sendLogout) {
	    return send(new LogoutMessage(), listeners);
	}
	return null;
    }

    @Override
    public void onMessage(TransportMessage message, InputStream is) {
	try {
	    ResponseMessage response = decodeResponse(is);
	    if (response == null) {
		return;
	    }

	    if ((response.getCorrelationId() == null) || response.isProcessed()) {
		return;
	    }

	    AsyncToken token = this.tokensMap.remove(response.getCorrelationId());
	    if (token == null) {
		log.warn("Unknown correlation id: %s", response.getCorrelationId());
		return;
	    }

	    switch (response.getType()) {
	    case RESULT:
		token.dispatchResult((ResultMessage) response);
		break;
	    case FAULT:
		token.dispatchFault((FaultMessage) response);
		break;
	    // $CASES-OMITTED$
	    default:
		token.dispatchFailure(new RuntimeException("Unknown message type: " + response));
		break;
	    }
	} catch (Exception e) {
	    log.error(e, "Could not deserialize or dispatch incoming messages");

	    AsyncToken token = message != null ? this.tokensMap.remove(message.getId()) : null;
	    if (token != null) {
		token.dispatchFailure(e);
	    }
	} finally {
	    if (this.timer != null) {
		this.timer.purge(); // Must purge to cleanup timer references to AsyncToken
	    }
	}
    }

    @Override
    public void onDisconnect() {
	log.info("Disconnecting channel %s", this.clientId);

	this.tokensMap.clear();
	this.tokensQueue.clear();

	if (this.timer != null) {
	    this.timer.purge(); // Must purge to cleanup timer references to AsyncToken
	}

	if (this.disconnectToken != null) {
	    // Handle "hard" disconnect
	    ResultMessage resultMessage = new ResultMessage(this.clientId, this.disconnectToken.getRequest().getId(), true);
	    this.disconnectToken.dispatchResult(resultMessage);
	    this.disconnectToken = null;
	}

	this.clientId = null;
	this.pinged = false;
	this.authenticating = false;
	this.authenticated = false;
    }

    @Override
    public void onError(TransportMessage message, Exception e) {
	if (message == null) {
	    return;
	}

	AsyncToken token = this.tokensMap.remove(message.getId());
	if (token == null) {
	    return;
	}

	token.dispatchFailure(e);

	if (this.timer != null) {
	    this.timer.purge(); // Must purge to cleanup timer references to AsyncToken
	}
    }

    @Override
    public void onCancelled(TransportMessage message) {
	AsyncToken token = this.tokensMap.remove(message.getId());
	if (token == null) {
	    return;
	}

	token.dispatchCancelled();

	if (this.timer != null) {
	    this.timer.purge(); // Must purge to cleanup timer references to AsyncToken
	}
    }

    private static class ChannelResponseListener extends AllInOneResponseListener {

	private final String tokenId;
	private final ConcurrentMap<String, AsyncToken> tokensMap;
	private final TransportFuture transportFuture;
	private final Semaphore connections;

	public ChannelResponseListener(String tokenId, ConcurrentMap<String, AsyncToken> tokensMap, TransportFuture transportFuture, Semaphore connections) {

	    this.tokenId = tokenId;
	    this.tokensMap = tokensMap;
	    this.transportFuture = transportFuture;
	    this.connections = connections;
	}

	@Override
	public void onEvent(Event event) {
	    try {
		this.tokensMap.remove(this.tokenId);
		if ((event.getType() == Type.TIMEOUT) || (event.getType() == Type.CANCELLED)) {
		    if (this.transportFuture != null) {
			try {
			    this.transportFuture.cancel();
			} catch (UnsupportedOperationException e) {
			    // In case transport does not support cancel
			}
		    }
		}
	    } finally {
		if (this.connections != null) {
		    this.connections.release();
		}
	    }
	}
    }

    @Override
    public void addListener(ChannelStatusListener listener) {
	this.statusListeners.add(listener);
    }

    @Override
    public void removeListener(ChannelStatusListener listener) {
	this.statusListeners.remove(listener);
    }

    protected void setPinged(boolean pinged) {
	if (this.pinged == pinged) {
	    return;
	}
	this.pinged = pinged;
	for (ChannelStatusListener listener : this.statusListeners) {
	    listener.pingedChanged(this, pinged);
	}
    }

    protected void setAuthenticated(boolean authenticated, ResponseMessage response) {
	if (!this.authenticating && (this.authenticated == authenticated)) {
	    return;
	}

	log.debug("Channel %s authentication changed clientId %s (%s)", this.id, this.clientId, String.valueOf(authenticated));
	this.authenticating = false;
	this.authenticated = authenticated;
	for (ChannelStatusListener listener : this.statusListeners) {
	    listener.authenticatedChanged(this, authenticated, response);
	}

	postSetAuthenticated(authenticated, response);
    }

    protected void postSetAuthenticated(boolean authenticatedP, ResponseMessage response) {
	// Empty.
    }

    public void clearCredentials() {
	this.credentials = null;
	for (ChannelStatusListener listener : this.statusListeners) {
	    listener.credentialsCleared(this);
	}
    }

    protected void dispatchFault(FaultMessage faultMessage) {
	for (ChannelStatusListener listener : this.statusListeners) {
	    listener.fault(this, faultMessage);
	}
    }

    @Override
    public void bindStatus(ChannelStatusNotifier notifier) {
	notifier.addListener(this.statusListener);
    }

    @Override
    public void unbindStatus(ChannelStatusNotifier notifier) {
	notifier.removeListener(this.statusListener);
    }

    private ChannelStatusListener statusListener = new ChannelStatusListener() {

	@Override
	public void fault(Channel channel, FaultMessage faultMessage) {
	    // Empty.
	}

	@Override
	public void pingedChanged(Channel channel, boolean pingedP) {
	    if (channel == AbstractHTTPChannel.this) {
		return;
	    }
	    log.debug("Channel %s pinged changed %s", channel.getClientId(), pingedP);
	    AbstractHTTPChannel.this.pinged = pingedP;
	}

	@Override
	public void authenticatedChanged(Channel channel, boolean authenticatedP, ResponseMessage response) {
	    if (channel == AbstractHTTPChannel.this) {
		return;
	    }

	    log.debug("Channel %s authenticated changed %s", channel.getClientId(), authenticatedP);
	    boolean wasAuthenticated = AbstractHTTPChannel.this.authenticated;
	    AbstractHTTPChannel.this.authenticating = false;
	    AbstractHTTPChannel.this.authenticated = authenticatedP;

	    if (authenticatedP != wasAuthenticated) {
		AbstractHTTPChannel.this.postSetAuthenticated(authenticatedP, response);
	    }
	}

	@Override
	public void credentialsCleared(Channel channel) {
	    if (channel == AbstractHTTPChannel.this) {
		return;
	    }
	    log.debug("Channel %s credentials cleared", channel.getClientId());
	    AbstractHTTPChannel.this.credentials = null;
	}
    };
}
