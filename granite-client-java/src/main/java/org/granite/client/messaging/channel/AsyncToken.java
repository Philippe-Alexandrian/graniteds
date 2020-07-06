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

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.granite.client.messaging.ResponseListener;
import org.granite.client.messaging.ResponseListenerDispatcher;
import org.granite.client.messaging.events.CancelledEvent;
import org.granite.client.messaging.events.Event;
import org.granite.client.messaging.events.FailureEvent;
import org.granite.client.messaging.events.FaultEvent;
import org.granite.client.messaging.events.ResultEvent;
import org.granite.client.messaging.events.TimeoutEvent;
import org.granite.client.messaging.messages.RequestMessage;
import org.granite.client.messaging.messages.ResponseMessage;
import org.granite.client.messaging.messages.requests.DisconnectMessage;
import org.granite.client.messaging.messages.responses.FaultMessage;
import org.granite.client.messaging.messages.responses.ResultMessage;
import org.granite.logging.Logger;

/**
 * @author Franck WOLFF
 */
public class AsyncToken extends TimerTask implements ResponseMessageFuture {

    private static final Logger log = Logger.getLogger(AsyncToken.class);

    private final RequestMessage request;
    private final List<ResponseListener> listeners = new ArrayList<>();

    private Event event = null;

    private ResponseListener channelListener = null;

    public AsyncToken(RequestMessage request) {
	this(request, (ResponseListener[]) null);
    }

    public AsyncToken(RequestMessage request, ResponseListener listener) {
	this(request, (listener == null ? null : new ResponseListener[] { listener }));
    }

    public AsyncToken(RequestMessage request, ResponseListener[] listeners) {
	if (request == null) {
	    throw new NullPointerException("request cannot be null");
	}
	this.request = request;

	if (listeners != null) {
	    for (ResponseListener listener : listeners) {
		if (listener == null) {
		    throw new NullPointerException("listeners cannot contain null values");
		}
		this.listeners.add(listener);
	    }
	}
    }

    public String getId() {
	return this.request.getId();
    }

    public RequestMessage getRequest() {
	return this.request;
    }

    public boolean isDisconnectRequest() {
	return this.request instanceof DisconnectMessage;
    }

    public synchronized Event setChannelListener(ResponseListener channelListener) {
	if (this.event == null) {
	    this.channelListener = channelListener;
	}
	return this.event;
    }

    @Override
    public void run() {
	try {
	    // Try to dispatch a TimeoutEvent.
	    dispatchTimeout(System.currentTimeMillis());
	} catch (Throwable e) {
	    log.error(e, "Error while executing token task for request " + this.request);
	}
    }

    @Override
    public boolean cancel() {
	// Try to dispatch a CancelledEvent.
	return dispatchCancelled();
    }

    @Override
    public ResponseMessage get() throws InterruptedException, ExecutionException, TimeoutException {
	return get(0);
    }

    @Override
    public ResponseMessage get(long timeout) throws InterruptedException, ExecutionException, TimeoutException {
	synchronized (this) {
	    if (this.event == null) {
		try {
		    wait(timeout);
		} catch (InterruptedException e) {
		    if (dispatchCancelled()) {
			throw e;
		    }
		}
	    }
	}

	return ResponseListenerDispatcher.getResponseMessage(this.event);
    }

    @Override
    public synchronized boolean isCancelled() {
	return this.event instanceof CancelledEvent;
    }

    @Override
    public synchronized boolean isDone() {
	return this.event != null;
    }

    public boolean dispatchResult(ResultMessage result) {
	return dispatch(new ResultEvent(this.request, result));
    }

    public boolean dispatchFault(FaultMessage fault) {
	return dispatch(new FaultEvent(this.request, fault));
    }

    public boolean dispatchFailure(Exception e) {
	return dispatch(new FailureEvent(this.request, e));
    }

    public boolean dispatchTimeout(long millis) {
	return dispatch(new TimeoutEvent(this.request, millis));
    }

    public boolean dispatchCancelled() {
	return dispatch(new CancelledEvent(this.request));
    }

    private boolean dispatch(Event eventP) {

	// Cancel this TimerTask.
	super.cancel();

	synchronized (this) {

	    // Make sure we didn't dispatch a previous event.
	    if (this.event != null) {
		return false;
	    }

	    // Create the corresponding event.
	    this.event = eventP;

	    if (this.channelListener != null) {
		ResponseListenerDispatcher.dispatch(this.channelListener, eventP);
	    }

	    // Wake up all threads waiting on the get() method.
	    notifyAll();
	}

	// Call all listeners.
	for (ResponseListener listener : this.listeners) {
	    ResponseListenerDispatcher.dispatch(listener, eventP);
	}

	// Release references on listeners to help gc
	this.channelListener = null;
	this.listeners.clear();

	return true;
    }

    @Override
    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	}
	return (obj instanceof AsyncToken) && this.request.getId().equals(((AsyncToken) obj).request.getId());
    }

    @Override
    public int hashCode() {
	return this.request.getId().hashCode();
    }

    @Override
    public String toString() {
	return getClass().getName() + " {request=" + this.request + "}";
    }
}
