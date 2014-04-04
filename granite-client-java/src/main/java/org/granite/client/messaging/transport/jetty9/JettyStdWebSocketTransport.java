/**
 *   GRANITE DATA SERVICES
 *   Copyright (C) 2006-2013 GRANITE DATA SERVICES S.A.S.
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
package org.granite.client.messaging.transport.jetty9;

import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.websocket.jsr356.JettyClientContainerProvider;
import org.granite.client.messaging.transport.websocket.WebSocketTransport;


/**
 * @author William DRAI
 */
public class JettyStdWebSocketTransport extends WebSocketTransport {

    @Override
    protected WebSocketContainer createContainer() {
        return JettyClientContainerProvider.getWebSocketContainer();
    }

    @Override
    protected void closeContainer(WebSocketContainer container) {
    }
}