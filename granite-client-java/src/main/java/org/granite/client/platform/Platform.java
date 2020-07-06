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
package org.granite.client.platform;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.granite.client.configuration.ClassScanner;
import org.granite.client.configuration.Configuration;
import org.granite.client.messaging.channel.ChannelType;
import org.granite.client.messaging.transport.Transport;
import org.granite.client.persistence.Persistence;
import org.granite.logging.Logger;
import org.granite.messaging.reflect.Reflection;
import org.granite.util.ServiceLoader;
import org.granite.util.TypeUtil;

/**
 * Platform abstracts everything specific to a target framework or platform
 *
 * Implementations are looked for in the {@link java.util.ServiceLoader} registry. The platform implementation must be unique (otherwise a PlatformConfigurationError is throws). To
 * resolve the ambiguity, the platform class name can also be defined by the system property org.granite.client.platform.Platform.
 *
 * @author Franck WOLFF
 */
public class Platform {

    private static final Logger log = Logger.getLogger(Platform.class);

    public static final String SYSTEM_PROPERTY_KEY = Platform.class.getName();

    protected static Platform instance = null;

    protected final Reflection reflection;
    protected final Persistence persistence;

    protected Object context;

    public static synchronized Platform getInstance() {

	if (instance == null) {
	    String platformClassName = System.getProperty(SYSTEM_PROPERTY_KEY);

	    if (platformClassName == null) {
		initInstance((ClassLoader) null);
	    }

	    if (instance == null) {
		initInstance(platformClassName, null);
	    }
	}

	return instance;
    }

    public static synchronized Platform initInstance(ClassLoader platformClassLoaderP) {
	ClassLoader platformClassLoader = platformClassLoaderP;

	if (instance != null) {
	    throw new IllegalStateException("Platform already loaded");
	}

	if (platformClassLoader == null) {
	    platformClassLoader = Thread.currentThread().getContextClassLoader();
	}

	ServiceLoader<Platform> platformLoader = ServiceLoader.load(Platform.class, platformClassLoader);

	Iterator<Platform> platforms = null;
	try {
	    platforms = platformLoader.iterator();

	    if (platforms.hasNext()) {
		instance = platforms.next();
	    }

	    if (platforms.hasNext()) {
		throw new PlatformConfigurationError("Multiple Platform services: " + instance + " / " + platforms.next());
	    }
	} catch (PlatformConfigurationError e) {
	    throw e;
	} catch (Throwable t) {
	    throw new PlatformConfigurationError("Could not load Platform service", t);
	}

	return instance;
    }

    public static synchronized Platform initInstance(String platformClassName) {
	return initInstance(platformClassName, null);
    }

    public static synchronized Platform initInstance(String platformClassName, ClassLoader platformClassLoader) {
	return initInstance(platformClassName, platformClassLoader, null);
    }

    public static synchronized Platform initInstance(String platformClassNameP, ClassLoader platformClassLoaderP, ClassLoader reflectionClassLoader) {
	String platformClassName = platformClassNameP;
	ClassLoader platformClassLoader = platformClassLoaderP;

	if (instance != null) {
	    throw new IllegalStateException("Platform already loaded");
	}

	if (platformClassLoader == null) {
	    platformClassLoader = Thread.currentThread().getContextClassLoader();
	}

	if (platformClassName == null) {
	    platformClassName = Platform.class.getName();
	}

	try {
	    @SuppressWarnings("unchecked")
	    Class<? extends Platform> platformClass = (Class<? extends Platform>) platformClassLoader.loadClass(platformClassName);
	    instance = platformClass.getConstructor(ClassLoader.class).newInstance(reflectionClassLoader);
	} catch (Throwable t) {
	    throw new PlatformConfigurationError("Could not create new Platform of type: " + platformClassName, t);
	}

	return instance;
    }

    public Platform() {
	this(new Reflection(null));
    }

    public Platform(ClassLoader reflectionClassLoader) {
	this(new Reflection(reflectionClassLoader));
    }

    public Platform(Reflection reflection) {
	if (reflection == null) {
	    throw new NullPointerException("reflection cannot be null");
	}

	this.reflection = reflection;
	this.persistence = new Persistence(reflection);
    }

    public Object getContext() {
	return this.context;
    }

    public void setContext(Object context) {
	this.context = context;
    }

    public ClassScanner newClassScanner() {
	String scannerClassName = System.getProperty("scanner.className");
	if (scannerClassName != null) {
	    try {
		return TypeUtil.newInstance(scannerClassName, ClassScanner.class);
	    } catch (Throwable t) {
		throw new RuntimeException("Specified scanner class " + scannerClassName + " not available", t);
	    }
	}

	try {
	    return TypeUtil.newInstance("org.granite.client.scan.ExtCosClassScanner", ClassScanner.class);
	} catch (Throwable t) {
	    log.debug(t, "Extcos scanner not available, using classpath scanner");
	}

	try {
	    return TypeUtil.newInstance("org.granite.client.scan.StandardClassScanner", ClassScanner.class);
	} catch (Throwable t) {
	    throw new RuntimeException("Could not create class scanner", t);
	}
    }

    public Reflection getReflection() {
	return this.reflection;
    }

    public Configuration newConfiguration() {
	try {
	    return TypeUtil.newInstance("org.granite.client.configuration.SimpleConfiguration", Configuration.class);
	} catch (Throwable t) {
	    throw new RuntimeException("Could not create simple configuration", t);
	}
    }

    public String defaultChannelType() {
	return ChannelType.LONG_POLLING;
    }

    public Transport newRemotingTransport() {
	try {
	    return TypeUtil.newInstance("org.granite.client.messaging.transport.apache.ApacheAsyncTransport", Transport.class);
	} catch (Exception e) {
	    throw new RuntimeException("Could not create Apache transport");
	}
    }

    public Transport newMessagingTransport() {
	return null;
    }

    public Map<String, Transport> getMessagingTransports() {
	Map<String, Transport> transportMap = new HashMap<>();
	try {
	    TypeUtil.forName("org.eclipse.jetty.websocket.jsr356.JettyClientContainerProvider");
	    transportMap.put(ChannelType.WEBSOCKET, TypeUtil.newInstance("org.granite.client.messaging.transport.jetty9.JettyStdWebSocketTransport", Transport.class));
	} catch (Throwable e1) {
	    try {
		TypeUtil.forName("org.eclipse.jetty.websocket.client.WebSocketClient");
		transportMap.put(ChannelType.WEBSOCKET, TypeUtil.newInstance("org.granite.client.messaging.transport.jetty9.JettyStdWebSocketTransport", Transport.class));
	    } catch (Throwable e2) {
		// Jetty 9 websocket client not found
		try {
		    TypeUtil.forName("org.eclipse.jetty.websocket.WebSocketClientFactory");
		    transportMap.put(ChannelType.WEBSOCKET, TypeUtil.newInstance("org.granite.client.messaging.transport.jetty.JettyWebSocketTransport", Transport.class));
		} catch (Throwable e3) {
		    // Jetty 8 websocket client not found
		    try {
			TypeUtil.forName("org.glassfish.tyrus.client.ClientManager");
			transportMap.put(ChannelType.WEBSOCKET, TypeUtil.newInstance("org.granite.client.messaging.transport.tyrus.TyrusWebSocketTransport", Transport.class));
		    } catch (Throwable e4) {
			// Tyrus websocket client not found
		    }
		}
	    }
	}
	return transportMap;
    }

    public Persistence getPersistence() {
	return this.persistence;
    }

    public static Reflection reflection() {
	return getInstance().reflection;
    }

    public static Persistence persistence() {
	return getInstance().persistence;
    }
}
