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
package org.granite.client.messaging.messages.requests;

import java.util.Map;

import org.granite.client.messaging.messages.Message;

/**
 * @author Franck WOLFF
 */
public final class UnsubscribeMessage extends AbstractTopicRequestMessage {

    private static final long serialVersionUID = 1L;

    private String subscriptionId = null;

    public UnsubscribeMessage() {
    }

    public UnsubscribeMessage(String destination, String topic, String subscriptionId) {
	this(null, destination, topic, subscriptionId);
    }

    public UnsubscribeMessage(String clientId, String destination, String topic, String subscriptionId) {
	super(clientId, destination, topic);

	this.subscriptionId = subscriptionId;
    }

    public UnsubscribeMessage(String id, String clientId, long timestamp, long timeToLive, Map<String, Object> headers, String destination, String topic, String subscriptionId) {

	super(id, clientId, timestamp, timeToLive, headers, destination, topic);

	this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionId() {
	return this.subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
	this.subscriptionId = subscriptionId;
    }

    @Override
    public Type getType() {
	return Type.UNSUBSCRIBE;
    }

    @Override
    public Message copy() {
	UnsubscribeMessage message = new UnsubscribeMessage();

	copy(message);

	message.subscriptionId = this.subscriptionId;

	return message;
    }
}
