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
package org.granite.messaging.amf.io.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import org.granite.logging.Logger;
import org.granite.util.Introspector;
import org.granite.util.PropertyDescriptor;
import org.granite.util.TypeUtil;

/**
 * @author Franck WOLFF
 */
public class DefaultActionScriptClassDescriptor extends ActionScriptClassDescriptor {

    private final Class<?> clazz;

    public DefaultActionScriptClassDescriptor(String type, byte encoding) {
	super(type, encoding);

	this.clazz = forName(type, this.instantiator);
    }

    private static Class<?> forName(String name, String instantiator) {
	if (name.length() == 0) {
	    return HashMap.class;
	}

	String className = (instantiator != null ? instantiator : name);
	try {
	    return TypeUtil.forName(className);
	} catch (Throwable t) {
	    throw new RuntimeException("Class not found: " + className);
	}
    }

    @Override
    public void defineProperty(String name) {

	if ((this.type.length() == 0) || (this.instantiator != null)) {
	    this.properties.add(new MapProperty(this.converters, name));
	} else {
	    try {
		// Try to find public getter/setter.
		PropertyDescriptor[] props = Introspector.getPropertyDescriptors(this.clazz);
		for (PropertyDescriptor prop : props) {
		    if (name.equals(prop.getName()) && (prop.getWriteMethod() != null) && (prop.getReadMethod() != null)) {
			this.properties.add(new MethodProperty(this.converters, name, prop.getWriteMethod(), prop.getReadMethod()));
			return;
		    }
		}

		// Try to find public field.
		Field field = this.clazz.getField(name);
		if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
		    this.properties.add(new FieldProperty(this.converters, field));
		}

	    } catch (NoSuchFieldException e) {
		if ("uid".equals(name)) {
		    this.properties.add(new UIDProperty(this.converters));
		} else {
		    Logger.getLogger(DefaultActionScriptClassDescriptor.class).error("Exception: Class=" + this.clazz.getName() + " Property=" + name);
		}
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    }
	}
    }

    @Override
    public Object newJavaInstance() {
	if (this.clazz == HashMap.class) {
	    return new HashMap<String, Object>();
	}

	try {
	    return this.clazz.newInstance();
	} catch (Exception e) {
	    throw new RuntimeException("Could not create instance of: " + this.clazz.getName(), e);
	}
    }
}
