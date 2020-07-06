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

import java.io.IOException;
import java.io.OutputStream;

import org.granite.client.messaging.codec.MessagingCodec;
import org.granite.client.messaging.codec.MessagingCodec.ClientType;

/**
 * @author Franck WOLFF
 */
public class DefaultTransportMessage<M> implements TransportMessage {

    private final String id;
    private final boolean connect;
    private final boolean disconnect;
    private final String clientId;
    private final String sessionId;
    private final M message;
    private final MessagingCodec<M> codec;

    public DefaultTransportMessage(String id, boolean connect, boolean disconnect, String clientId, String sessionId, M message, MessagingCodec<M> codec) {
	this.id = id;
	this.connect = connect;
	this.disconnect = disconnect;
	this.clientId = clientId;
	this.sessionId = sessionId;
	this.message = message;
	this.codec = codec;
    }

    @Override
    public ClientType getClientType() {
	return this.codec.getClientType();
    }

    @Override
    public String getId() {
	return this.id;
    }

    @Override
    public boolean isConnect() {
	return this.connect;
    }

    @Override
    public boolean isDisconnect() {
	return this.disconnect;
    }

    @Override
    public String getClientId() {
	return this.clientId;
    }

    @Override
    public String getSessionId() {
	return this.sessionId;
    }

    @Override
    public String getContentType() {
	return this.codec.getContentType();
    }

    @Override
    public void encode(OutputStream os) throws IOException {
	this.codec.encode(this.message, os);
    }
}
