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
package org.granite.client.persistence.collection;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Franck WOLFF
 */
public abstract class AbstractPersistentMapCollection<K, V, C extends Map<K, V>> extends AbstractPersistentCollection<C> implements Map<K, V> {

    private static final long serialVersionUID = 1L;

    public AbstractPersistentMapCollection() {
    }

    @Override
    public int size() {
	if (!checkInitializedRead()) {
	    return 0;
	}
	return getCollection().size();
    }

    @Override
    public boolean isEmpty() {
	if (!checkInitializedRead()) {
	    return true;
	}
	return getCollection().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
	if (!checkInitializedRead()) {
	    return false;
	}
	return getCollection().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
	if (!checkInitializedRead()) {
	    return false;
	}
	return getCollection().containsValue(value);
    }

    @Override
    public V get(Object key) {
	if (!checkInitializedRead()) {
	    return null;
	}
	return getCollection().get(key);
    }

    @Override
    public V put(K key, V value) {
	return put(key, value, true);
    }

    protected V put(K key, V value, boolean checkInitialized) {
	if (checkInitialized) {
	    checkInitializedWrite();
	}

	boolean containsKey = getCollection().containsKey(key);
	V previousValue = getCollection().put(key, value);
	if (!containsKey || (previousValue == null ? value != null : !previousValue.equals(value))) {
	    dirty();
	}
	return previousValue;
    }

    @Override
    public V remove(Object key) {
	checkInitializedWrite();

	boolean containsKey = getCollection().containsKey(key);
	V removedValue = getCollection().remove(key);
	if (containsKey) {
	    dirty();
	}
	return removedValue;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
	checkInitializedWrite();

	for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
	    put(entry.getKey(), entry.getValue(), false);
	}
    }

    @Override
    public void clear() {
	checkInitializedWrite();
	if (!getCollection().isEmpty()) {
	    getCollection().clear();
	    dirty();
	}
    }

    @Override
    public Set<K> keySet() {
	if (!checkInitializedRead()) {
	    return Collections.emptySet();
	}
	return new SetProxy<>(getCollection().keySet());
    }

    @Override
    public Collection<V> values() {
	if (!checkInitializedRead()) {
	    return Collections.emptySet();
	}
	return new CollectionProxy<>(getCollection().values());
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
	if (!checkInitializedRead()) {
	    return Collections.emptySet();
	}
	return new SetProxy<>(getCollection().entrySet());
    }
}
