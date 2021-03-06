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
import java.lang.reflect.Field;
import java.lang.reflect.Type;

import org.granite.logging.Logger;
import org.granite.messaging.amf.io.convert.Converters;
import org.granite.messaging.amf.types.AMFSpecialValueFactory.SpecialValueFactory;

/**
 * @author Franck WOLFF
 */
public class FieldProperty extends Property {

    private final Field field;
    private final SpecialValueFactory<?> factory;

    public FieldProperty(Converters converters, Field field) {
	super(converters, field.getName());

	field.setAccessible(true);
	this.field = field;

	this.factory = specialValueFactory.getValueFactory(this);
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass, boolean recursive) {
	return this.field.isAnnotationPresent(annotationClass);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass, boolean recursive) {
	return this.field.getAnnotation(annotationClass);
    }

    @Override
    public Type getType() {
	return this.field.getGenericType();
    }

    @Override
    public Class<?> getDeclaringClass() {
	return this.field.getDeclaringClass();
    }

    @Override
    public void setValue(Object instance, Object value, boolean convert) {
	try {
	    this.field.set(instance, convert ? convert(value) : value);
	} catch (Exception e) {
	    Logger.getLogger(FieldProperty.class).error("Exception: Class=" + this.field.getDeclaringClass().getName() + " Property=" + getName() + " Expected="
		    + value.getClass().getSimpleName() + " Actual=" + getType().getTypeName() + " value=" + value);
	}
    }

    @Override
    public Object getValue(Object instance) {
	try {
	    Object o = this.field.get(instance);
	    if (this.factory != null) {
		o = this.factory.create(o);
	    }
	    return o;
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
    }
}
