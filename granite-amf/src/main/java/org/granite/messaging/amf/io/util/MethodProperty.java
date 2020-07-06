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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.granite.logging.Logger;
import org.granite.messaging.amf.io.convert.Converters;
import org.granite.messaging.amf.types.AMFSpecialValueFactory.SpecialValueFactory;
import org.granite.util.TypeUtil;
import org.granite.util.TypeUtil.DeclaredAnnotation;

/**
 * @author Franck WOLFF
 */
public class MethodProperty extends Property {

    private final Method setter;
    private final Method getter;
    private final Type type;
    private final SpecialValueFactory<?> factory;

    public MethodProperty(Converters converters, String name, Method setter, Method getter) {
	super(converters, name);
	this.setter = setter;
	this.getter = getter;
	this.type = getter != null ? getter.getGenericReturnType() : setter.getParameterTypes()[0];

	this.factory = specialValueFactory.getValueFactory(this);
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass, boolean recursive) {
	if (this.getter != null) {
	    if (this.getter.isAnnotationPresent(annotationClass)) {
		return true;
	    }
	    if (recursive && TypeUtil.isAnnotationPresent(this.getter, annotationClass)) {
		return true;
	    }
	}
	if (this.setter != null) {
	    if (this.setter.isAnnotationPresent(annotationClass)) {
		return true;
	    }
	    if (recursive && TypeUtil.isAnnotationPresent(this.setter, annotationClass)) {
		return true;
	    }
	}
	return false;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass, boolean recursive) {
	T annotation = null;
	if (this.getter != null) {
	    annotation = this.getter.getAnnotation(annotationClass);
	    if ((annotation == null) && recursive) {
		DeclaredAnnotation<T> declaredAnnotation = TypeUtil.getAnnotation(this.getter, annotationClass);
		if (declaredAnnotation != null) {
		    annotation = declaredAnnotation.annotation;
		}
	    }
	}
	if ((annotation == null) && (this.setter != null)) {
	    annotation = this.setter.getAnnotation(annotationClass);
	    if ((annotation == null) && recursive) {
		DeclaredAnnotation<T> declaredAnnotation = TypeUtil.getAnnotation(this.setter, annotationClass);
		if (declaredAnnotation != null) {
		    annotation = declaredAnnotation.annotation;
		}
	    }
	}
	return annotation;
    }

    @Override
    public Type getType() {
	return this.type;
    }

    @Override
    public Class<?> getDeclaringClass() {
	return this.getter != null ? this.getter.getDeclaringClass() : this.setter.getDeclaringClass();
    }

    @Override
    public void setValue(Object instance, Object value, boolean convert) {
	try {
	    this.setter.invoke(instance, convert ? convert(value) : value);
	} catch (Throwable t) {
	    Logger.getLogger(MethodProperty.class).error("Exception: Class=" + this.setter.getDeclaringClass().getName() + " Property=" + getName() + " Expected="
		    + value.getClass().getSimpleName() + " Actual=" + getType().getTypeName() + " value=" + value);
	}
    }

    @Override
    public Object getValue(Object instance) {
	try {
	    Object o = this.getter.invoke(instance);
	    if (this.factory != null) {
		o = this.factory.create(o);
	    }
	    return o;
	} catch (Throwable t) {
	    throw new RuntimeException(t);
	}
    }
}
