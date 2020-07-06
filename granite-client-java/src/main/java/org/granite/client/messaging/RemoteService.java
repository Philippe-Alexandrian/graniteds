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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.granite.client.messaging.channel.Channel;
import org.granite.client.messaging.channel.ResponseMessageFuture;
import org.granite.client.messaging.messages.requests.InvocationMessage;

/**
 * RemoteService allows to call a remote service using RPC style Calling a service is asynchronous and won't block the thread initiating the call.
 *
 * <pre>
 * {
 *     &#64;code
 *     RemoteService remoteService = new RemoteService(remotingChannel, "myService");
 *     remoteService.newInvocation("myMethod", arg1, arg2).invoke();
 * }
 * </pre>
 *
 * It is also possible to chain multiple invocations in one single call:
 *
 * <pre>
 * {
 *     &#64;code
 *     RemoteService remoteService = new RemoteService(remotingChannel, "myService");
 *     remoteService.newInvocation("myMethod", arg1, arg2).appendInvocation("myMethod2", arg3).invoke();
 * }
 * </pre>
 *
 * @author Franck WOLFF
 */
public class RemoteService {

    final Channel channel;
    final String id;

    /**
     * Create a remote service for the specified channel and destination
     *
     * @param channel messaging channel
     * @param id remote destination name
     */
    public RemoteService(Channel channel, String id) {
	if ((channel == null) || (id == null)) {
	    throw new NullPointerException("channel and id cannot be null");
	}
	this.channel = channel;
	this.id = id;
    }

    /**
     * Remoting channel
     *
     * @return channel
     */
    public Channel getChannel() {
	return this.channel;
    }

    /**
     * Destination id
     *
     * @return destination id
     */
    public String getId() {
	return this.id;
    }

    /**
     * Create an invocation for this service
     *
     * @param method remote method name
     * @param parameters arguments to send
     * @return fluent invocation object
     */
    public RemoteServiceInvocation newInvocation(String method, Object... parameters) {
	return new RemoteServiceInvocation(this, method, parameters);
    }

    public static interface RemoteServiceInvocationChain {

	/**
	 * Append a new invocation to the current chain of invocations
	 *
	 * @param method remote method name
	 * @param parameters arguments to send
	 * @return fluent invocation object
	 */
	RemoteServiceInvocationChain appendInvocation(String method, Object... parameters);

	/**
	 * Execute the chain of invocations
	 *
	 * @return future that will be triggered when the chain of invocations has been processed
	 */
	ResponseMessageFuture invoke();
    }

    public static class RemoteServiceInvocation implements RemoteServiceInvocationChain {

	private final RemoteService remoteService;
	private final InvocationMessage request;

	private long timeToLive = 0L;
	private List<ResponseListener> listeners = new ArrayList<>();

	public RemoteServiceInvocation(RemoteService remoteService, String method, Object... parameters) {
	    if (remoteService == null) {
		throw new NullPointerException("remoteService cannot be null");
	    }
	    this.remoteService = remoteService;
	    this.request = new InvocationMessage(null, remoteService.id, method, parameters);
	}

	/**
	 * Register a listener for the current invocation
	 *
	 * @param listener listener
	 * @return fluent invocation object
	 */
	public RemoteServiceInvocation addListener(ResponseListener listener) {
	    if (listener != null) {
		this.listeners.add(listener);
	    }
	    return this;
	}

	/**
	 * Registers many listeners for the current invocation
	 *
	 * @param listenersP array of listener
	 * @return fluent invocation object
	 */
	public RemoteServiceInvocation addListeners(ResponseListener... listenersP) {
	    if ((listenersP != null) && (listenersP.length > 0)) {
		this.listeners.addAll(Arrays.asList(listenersP));
	    }
	    return this;
	}

	/**
	 * Set the time to live for the current invocation
	 *
	 * @param timeToLiveMillis time to live in milliseconds
	 * @return fluent invocation object
	 */
	public RemoteServiceInvocation setTimeToLive(long timeToLiveMillis) {
	    return setTimeToLive(timeToLiveMillis, TimeUnit.MILLISECONDS);
	}

	/**
	 * Set the time to live in any unit for the current invocation
	 *
	 * @param timeToLive time to live value
	 * @param unit time to live unit
	 * @return fluent invocation object
	 */
	public RemoteServiceInvocation setTimeToLive(long timeToLive, TimeUnit unit) {
	    if (timeToLive < 0) {
		throw new IllegalArgumentException("timeToLive cannot be negative");
	    }
	    if (unit == null) {
		throw new NullPointerException("unit cannot be null");
	    }
	    this.timeToLive = unit.toMillis(timeToLive);
	    return this;
	}

	@Override
	public RemoteServiceInvocationChain appendInvocation(String method, Object... parameters) {
	    InvocationMessage message = this.request;
	    while (message.getNext() != null) {
		message = message.getNext();
	    }
	    message.setNext(new InvocationMessage(null, this.remoteService.id, method, parameters));
	    return this;
	}

	@Override
	public ResponseMessageFuture invoke() {
	    this.request.setTimeToLive(this.timeToLive);
	    return this.remoteService.channel.send(this.request, this.listeners.toArray(new ResponseListener[this.listeners.size()]));
	}
    }
}
