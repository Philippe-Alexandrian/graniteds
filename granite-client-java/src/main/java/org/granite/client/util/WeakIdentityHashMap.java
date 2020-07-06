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
package org.granite.client.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author William DRAI
 */
public class WeakIdentityHashMap<K, V> implements Map<K, V> {

    final ReferenceQueue<K> queue = new ReferenceQueue<>();
    private Map<IdentityWeakReference, V> map;

    public WeakIdentityHashMap() {
	this.map = new HashMap<>();
    }

    public WeakIdentityHashMap(int size) {
	this.map = new HashMap<>(size);
    }

    @Override
    public void clear() {
	this.map.clear();
	reap();
    }

    @Override
    public boolean containsKey(Object key) {
	reap();
	return this.map.containsKey(new IdentityWeakReference(key));
    }

    @Override
    public boolean containsValue(Object value) {
	reap();
	return this.map.containsValue(value);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
	reap();
	Set<Map.Entry<K, V>> ret = new HashSet<>();
	for (Map.Entry<IdentityWeakReference, V> ref : this.map.entrySet()) {
	    final K key = ref.getKey().get();
	    final V value = ref.getValue();
	    Map.Entry<K, V> entry = new Map.Entry<K, V>() {
		@Override
		public K getKey() {
		    return key;
		}

		@Override
		public V getValue() {
		    return value;
		}

		@Override
		public V setValue(V value) {
		    throw new UnsupportedOperationException();
		}
	    };
	    ret.add(entry);
	}
	return Collections.unmodifiableSet(ret);
    }

    @Override
    public Set<K> keySet() {
	reap();
	Set<K> ret = new HashSet<>();
	for (IdentityWeakReference ref : this.map.keySet()) {
	    ret.add(ref.get());
	}

	return Collections.unmodifiableSet(ret);
    }

    @Override
    public boolean equals(Object o) {
	return this.map.equals(((WeakIdentityHashMap<?, ?>) o).map);
    }

    @Override
    public V get(Object key) {
	reap();
	return this.map.get(new IdentityWeakReference(key));
    }

    @Override
    public V put(K key, V value) {
	reap();
	return this.map.put(new IdentityWeakReference(key), value);
    }

    @Override
    public int hashCode() {
	reap();
	return this.map.hashCode();
    }

    @Override
    public boolean isEmpty() {
	reap();
	return this.map.isEmpty();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
	throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
	reap();
	return this.map.remove(new IdentityWeakReference(key));
    }

    @Override
    public int size() {
	reap();
	return this.map.size();
    }

    @Override
    public Collection<V> values() {
	reap();
	return this.map.values();
    }

    private synchronized void reap() {
	Object zombie = this.queue.poll();

	while (zombie != null) {
	    @SuppressWarnings("unchecked")
	    IdentityWeakReference victim = (IdentityWeakReference) zombie;
	    this.map.remove(victim);
	    zombie = this.queue.poll();
	}
    }

    class IdentityWeakReference extends WeakReference<K> {

	private final int hash;

	@SuppressWarnings("unchecked")
	IdentityWeakReference(Object obj) {
	    super((K) obj, WeakIdentityHashMap.this.queue);
	    this.hash = System.identityHashCode(obj);
	}

	@Override
	public int hashCode() {
	    return this.hash;
	}

	@Override
	public boolean equals(Object o) {
	    if (this == o) {
		return true;
	    }

	    @SuppressWarnings("unchecked")
	    IdentityWeakReference ref = (IdentityWeakReference) o;
	    if (this.get() == ref.get()) {
		return true;
	    }

	    return false;
	}
    }
}