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
package org.granite.client.messaging.codec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.granite.messaging.amf.io.util.DefaultActionScriptClassDescriptor;
import org.granite.util.TypeUtil;

/**
 * @author Franck WOLFF
 */
public class ClientJavaClassDescriptor extends DefaultActionScriptClassDescriptor {

    private static final SunConstructorFactory factory = new SunConstructorFactory();
    private static final ConcurrentHashMap<String, Constructor<?>> constructors = new ConcurrentHashMap<>();

    public ClientJavaClassDescriptor(String type, byte encoding) {
	super(type, encoding);
    }

    @Override
    public Object newJavaInstance() {
	try {
	    return super.newJavaInstance();
	} catch (RuntimeException e) {
	    if (e.getCause() instanceof InstantiationException) {
		return findDefaultConstructor();
	    }
	    throw e;
	}
    }

    private Object findDefaultConstructor() {
	try {
	    Constructor<?> defaultContructor = constructors.get(this.type);
	    if (defaultContructor == null) {
		defaultContructor = factory.findDefaultConstructor(TypeUtil.forName(this.type));
		Constructor<?> previousConstructor = constructors.putIfAbsent(this.type, defaultContructor);
		if (previousConstructor != null) {
		    defaultContructor = previousConstructor; // Should be the same instance, anyway...
		}
	    }
	    return defaultContructor.newInstance();
	} catch (Exception e) {
	    throw new RuntimeException("Could not create Proxy for: " + this.type);
	}
    }
}

class SunConstructorFactory {

    private final Object reflectionFactory;
    private final Method newConstructorForSerialization;

    public SunConstructorFactory() {
	try {
	    Class<?> factoryClass = TypeUtil.forName("sun.reflect.ReflectionFactory");
	    Method getReflectionFactory = factoryClass.getDeclaredMethod("getReflectionFactory");
	    this.reflectionFactory = getReflectionFactory.invoke(null);
	    this.newConstructorForSerialization = factoryClass.getDeclaredMethod("newConstructorForSerialization", new Class[] { Class.class, Constructor.class });
	} catch (Exception e) {
	    throw new RuntimeException("Could not create Sun Factory", e);
	}
    }

    @SuppressWarnings("unchecked")
    public <T> Constructor<T> findDefaultConstructor(Class<T> clazz) {
	try {
	    Constructor<?> constructor = Object.class.getDeclaredConstructor();
	    constructor = (Constructor<?>) this.newConstructorForSerialization.invoke(this.reflectionFactory, new Object[] { clazz, constructor });
	    constructor.setAccessible(true);
	    return (Constructor<T>) constructor;
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
    }
}