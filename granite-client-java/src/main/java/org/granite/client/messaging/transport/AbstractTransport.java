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
package org.granite.client.messaging.transport;

import java.util.ArrayList;
import java.util.List;

import org.granite.client.messaging.transport.TransportStatusHandler.LogEngineStatusHandler;
import org.granite.client.messaging.transport.TransportStatusHandler.NoopEngineStatusHandler;

/**
 * @author Franck WOLFF
 */
public abstract class AbstractTransport<C> implements Transport {

    private volatile C context;
    private volatile TransportStatusHandler statusHandler = new LogEngineStatusHandler();

    protected final List<TransportStopListener> stopListeners = new ArrayList<>();

    public AbstractTransport() {
    }

    public AbstractTransport(C context) {
	this.context = context;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setContext(Object context) {
	this.context = (C) context;
    }

    @Override
    public C getContext() {
	return this.context;
    }

    @Override
    public boolean isDisconnectAfterAuthenticationFailure() {
	return false;
    }

    @Override
    public boolean isAuthenticationAfterReconnectWithRemoting() {
	return false;
    }

    @Override
    public void setStatusHandler(TransportStatusHandler statusHandlerP) {
	TransportStatusHandler statusHandlerL = statusHandlerP;
	if (statusHandlerL == null) {
	    statusHandlerL = new NoopEngineStatusHandler();
	}
	this.statusHandler = statusHandlerL;
    }

    @Override
    public TransportStatusHandler getStatusHandler() {
	return this.statusHandler;
    }

    @Override
    public void addStopListener(TransportStopListener listener) {
	synchronized (this.stopListeners) {
	    if (!this.stopListeners.contains(listener)) {
		this.stopListeners.add(listener);
	    }
	}
    }

    @Override
    public boolean removeStopListener(TransportStopListener listener) {
	synchronized (this.stopListeners) {
	    return this.stopListeners.remove(listener);
	}
    }

    @Override
    public void stop() {
	synchronized (this.stopListeners) {
	    for (TransportStopListener listener : this.stopListeners) {
		try {
		    listener.onStop(this);
		} catch (Exception e) {
		    // Empty.
		}
	    }
	    this.stopListeners.clear();
	}
    }
}
