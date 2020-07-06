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
package org.granite.client.messaging.transport.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.xml.ws.Endpoint;

import org.granite.client.messaging.channel.Channel;
import org.granite.client.messaging.transport.TransportException;
import org.granite.client.messaging.transport.TransportMessage;
import org.granite.logging.Logger;

/**
 * @author William DRAI
 */
public abstract class WebSocketTransport extends AbstractWebSocketTransport<Session> {

    private static final Logger log = Logger.getLogger(WebSocketTransport.class);

    private WebSocketContainer webSocketContainer;
    private Map<String, Object> userProperties = null;

    public void setUserProperties(Map<String, Object> userProperties) {
	this.userProperties = userProperties;
    }

    @Override
    public synchronized boolean start() {
	if (isStarted()) {
	    return true;
	}

	log.info("Starting WebSocket transport...");

	try {
	    this.webSocketContainer = createContainer();
	    this.webSocketContainer.setDefaultMaxSessionIdleTimeout(getMaxIdleTime());
	    this.webSocketContainer.setDefaultMaxBinaryMessageBufferSize(getMaxMessageSize());

	    log.info("WebSocket transport started.");
	    return true;
	} catch (Exception e) {
	    this.webSocketContainer = null;
	    getStatusHandler().handleException(new TransportException("Could not start WebSocket Endpoint", e));

	    log.error(e, "WebSocket transport failed to start.");
	    return false;
	}
    }

    protected abstract WebSocketContainer createContainer();

    protected abstract void closeContainer(WebSocketContainer webSocketContainer);

    @Override
    public synchronized boolean isStarted() {
	return this.webSocketContainer != null;
    }

    public class GravityWebSocketEndpointConfig implements ClientEndpointConfig {

	private final List<String> protocols;
	private final Channel channel;
	private final TransportMessage transportMessage;
	private final Map<String, Object> userProperties = new ConcurrentHashMap<>();

	public GravityWebSocketEndpointConfig(Channel channel, TransportMessage transportMessage) {

	    String protocol = "org.granite.gravity." + transportMessage.getContentType().substring("application/x-".length());
	    this.protocols = Collections.singletonList(protocol);

	    this.channel = channel;
	    this.transportMessage = transportMessage;

	    if (WebSocketTransport.this.userProperties != null) {
		this.userProperties.putAll(WebSocketTransport.this.userProperties);
	    }
	}

	@Override
	public ClientEndpointConfig.Configurator getConfigurator() {
	    return new GravityWebSocketConfigurator();
	}

	@Override
	public List<String> getPreferredSubprotocols() {
	    return this.protocols;
	}

	@Override
	public List<Extension> getExtensions() {
	    return Collections.emptyList();
	}

	@Override
	public List<Class<? extends Encoder>> getEncoders() {
	    return Collections.emptyList();
	}

	@Override
	public List<Class<? extends Decoder>> getDecoders() {
	    return Collections.emptyList();
	}

	@Override
	public Map<String, Object> getUserProperties() {
	    return this.userProperties;
	}

	private class GravityWebSocketConfigurator extends Configurator {

	    @Override
	    public void beforeRequest(Map<String, List<String>> headers) {
		if (GravityWebSocketEndpointConfig.this.transportMessage.getSessionId() != null) {
		    headers.put("Cookie", Collections.singletonList("JSESSIONID=" + GravityWebSocketEndpointConfig.this.transportMessage.getSessionId()));
		}

		headers.put("connectId", Collections.singletonList(GravityWebSocketEndpointConfig.this.transportMessage.getId()));
		headers.put("GDSClientType", Collections.singletonList(GravityWebSocketEndpointConfig.this.transportMessage.getClientType().toString()));
		String clientId = GravityWebSocketEndpointConfig.this.transportMessage.getClientId() != null ? GravityWebSocketEndpointConfig.this.transportMessage.getClientId()
			: GravityWebSocketEndpointConfig.this.channel.getClientId();
		if (clientId != null) {
		    headers.put("GDSClientId", Collections.singletonList(clientId));
		}
	    }

	    @Override
	    public void afterResponse(HandshakeResponse hr) {
	    }
	}
    }

    public class GravityWebSocketEndpoint extends Endpoint implements MessageHandler.Whole<byte[]> {

	private Channel channel;

	public GravityWebSocketEndpoint(Channel channel) {
	    this.channel = channel;
	}

	@Override
	public void onOpen(Session session, EndpointConfig endpointConfig) {
	    session.addMessageHandler(this);
	    onConnect(this.channel, session);
	}

	@Override
	public void onMessage(byte[] data) {
	    onBinaryMessage(this.channel, data, 0, data.length);
	}

	@Override
	public void onClose(Session session, CloseReason closeReason) {
	    WebSocketTransport.this.onClose(this.channel, closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
	}

	@Override
	public void onError(Session session, Throwable error) {
	    WebSocketTransport.this.onError(this.channel, error);
	}
    }

    @Override
    public void connect(final Channel channel, final TransportMessage transportMessage) {
	try {
	    log.info("Connecting to websocket %s sessionId %s", channel.getUri(), transportMessage.getSessionId());

	    this.webSocketContainer.connectToServer(new GravityWebSocketEndpoint(channel), new GravityWebSocketEndpointConfig(channel, transportMessage), channel.getUri());
	} catch (Exception e) {
	    log.error(e, "Could not connect to uri %s", channel.getUri());
	    getStatusHandler().handleException(new TransportException("Could not connect to uri " + channel.getUri(), e));
	}
    }

    @Override
    public synchronized void stop() {
	if (this.webSocketContainer == null) {
	    return;
	}

	log.info("Stopping WebSocket transport...");

	setStopping(true);

	super.stop();

	try {
	    closeContainer(this.webSocketContainer);
	} catch (Exception e) {
	    getStatusHandler().handleException(new TransportException("Could not stop WebSocket", e));

	    log.error(e, "WebSocket failed to stop properly.");
	} finally {
	    this.webSocketContainer = null;

	    setStopping(false);
	}

	log.info("WebSocket transport stopped.");
    }

    @Override
    protected TransportData<Session> newTransportData() {
	return new WebSocketTransportData();
    }

    private class WebSocketTransportData extends TransportData<Session> {

	private Session session = null;

	@Override
	public void connect(Session session) {
	    this.session = session;
	}

	@Override
	public boolean isConnected() {
	    return this.session != null;
	}

	@Override
	public void disconnect() {
	    this.session = null;
	}

	@Override
	public void sendBytes(byte[] data) throws IOException {
	    this.session.getBasicRemote().sendBinary(ByteBuffer.wrap(data));
	}
    }
}
