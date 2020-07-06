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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import org.granite.client.persistence.LazyInitializationException;
import org.granite.client.persistence.Loader;
import org.granite.logging.Logger;
import org.granite.messaging.persistence.PersistentCollectionSnapshot;
import org.granite.util.TypeUtil;

/**
 * @author Franck WOLFF
 */
public abstract class AbstractPersistentCollection<C> implements PersistentCollection<C> {

    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger.getLogger(AbstractPersistentCollection.class);

    private volatile C collection = null;
    private volatile boolean dirty = false;
    private volatile String detachedState = null;
    private Loader<C> loader = new DefaultCollectionLoader<>();
    private List<ChangeListener<C>> changeListeners = new ArrayList<>();
    private List<InitializationListener<C>> initializationListeners = new ArrayList<>();

    protected AbstractPersistentCollection() {
    }

    protected void init(C collectionP, String detachedStateP, boolean dirtyP) {
	this.collection = collectionP;
	if (detachedStateP != null) {
	    this.detachedState = detachedStateP;
	}
	this.dirty = dirtyP;
    }

    @Override
    public Loader<C> getLoader() {
	return this.loader;
    }

    @Override
    public void setLoader(Loader<C> loader) {
	this.loader = loader;
    }

    protected boolean checkInitializedRead() {
	if (wasInitialized()) {
	    return true;
	}
	this.loader.load(this, null);
	return false;
    }

    protected void checkInitializedWrite() {
	if (!wasInitialized()) {
	    throw new LazyInitializationException(getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(this)));
	}
    }

    protected C getCollection() {
	return this.collection;
    }

    public String getDetachedState() {
	return this.detachedState;
    }

    protected ClassLoader getClassLoader() {
	return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public boolean wasInitialized() {
	return this.collection != null;
    }

    @Override
    public boolean isDirty() {
	return this.dirty;
    }

    @Override
    public void dirty() {
	this.dirty = true;
	for (ChangeListener<C> listener : this.changeListeners) {
	    listener.changed(this);
	}
    }

    @Override
    public void clearDirty() {
	this.dirty = false;
    }

    @Override
    public PersistentCollection<C> clone(boolean uninitialize) {
	try {
	    AbstractPersistentCollection<C> collectionL = TypeUtil.newInstance(getClass(), AbstractPersistentCollection.class);
	    if (wasInitialized() && !uninitialize) {
		collectionL.init(getCollection(), getDetachedState(), isDirty());
	    }
	    return collectionL;
	} catch (Exception e) {
	    throw new RuntimeException("Could not clone collection " + this.getClass().getName(), e);
	}
    }

    protected abstract PersistentCollectionSnapshot createSnapshot(Object io, boolean forReading);

    protected abstract void updateFromSnapshot(ObjectInput in, PersistentCollectionSnapshot snapshot);

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
	PersistentCollectionSnapshot snapshot = createSnapshot(out, false);
	snapshot.writeExternal(out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	PersistentCollectionSnapshot snapshot = createSnapshot(in, true);
	snapshot.readExternal(in);
	updateFromSnapshot(in, snapshot);
    }

    @Override
    public String toString() {
	return getClass().getSimpleName() + " {initialized=" + wasInitialized() + ", dirty=" + isDirty() + "}" + (this.collection != null ? ": " + this.collection.toString() : "");
    }

    class IteratorProxy<E> implements Iterator<E> {

	private final Iterator<E> iterator;

	public IteratorProxy(Iterator<E> iterator) {
	    this.iterator = iterator;
	}

	@Override
	public boolean hasNext() {
	    return this.iterator.hasNext();
	}

	@Override
	public E next() {
	    if (!checkInitializedRead()) {
		return null;
	    }
	    return this.iterator.next();
	}

	@Override
	public void remove() {
	    checkInitializedWrite();
	    this.iterator.remove();
	    dirty();
	}

	@Override
	public int hashCode() {
	    return this.iterator.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
	    return this.iterator.equals(obj);
	}
    }

    class ListIteratorProxy<E> implements ListIterator<E> {

	private final ListIterator<E> iterator;

	private E lastNextOrPrevious = null;

	public ListIteratorProxy(ListIterator<E> iterator) {
	    this.iterator = iterator;
	}

	@Override
	public boolean hasNext() {
	    return this.iterator.hasNext();
	}

	@Override
	public E next() {
	    if (!checkInitializedRead()) {
		return null;
	    }
	    return (this.lastNextOrPrevious = this.iterator.next());
	}

	@Override
	public boolean hasPrevious() {
	    return this.iterator.hasPrevious();
	}

	@Override
	public E previous() {
	    if (!checkInitializedRead()) {
		return null;
	    }
	    return (this.lastNextOrPrevious = this.iterator.previous());
	}

	@Override
	public int nextIndex() {
	    return this.iterator.nextIndex();
	}

	@Override
	public int previousIndex() {
	    return this.iterator.previousIndex();
	}

	@Override
	public void remove() {
	    checkInitializedWrite();
	    this.iterator.remove();
	    this.lastNextOrPrevious = null;
	    dirty();
	}

	@Override
	public void set(E e) {
	    checkInitializedWrite();
	    this.iterator.set(e);
	    if (e == null ? this.lastNextOrPrevious != null : !e.equals(this.lastNextOrPrevious)) {
		dirty();
	    }
	}

	@Override
	public void add(E e) {
	    checkInitializedWrite();
	    this.iterator.add(e);
	    this.lastNextOrPrevious = null;
	    dirty();
	}

	@Override
	public int hashCode() {
	    return this.iterator.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
	    return this.iterator.equals(obj);
	}
    }

    class CollectionProxy<E> implements Collection<E> {

	protected final Collection<E> collection;

	public CollectionProxy(Collection<E> collection) {
	    this.collection = collection;
	}

	@Override
	public int size() {
	    return this.collection.size();
	}

	@Override
	public boolean isEmpty() {
	    return this.collection.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
	    return this.collection.contains(o);
	}

	@Override
	public Iterator<E> iterator() {
	    return new IteratorProxy<>(this.collection.iterator());
	}

	@Override
	public Object[] toArray() {
	    return this.collection.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
	    return this.collection.toArray(a);
	}

	@Override
	public boolean add(E e) {
	    if (this.collection.add(e)) {
		dirty();
		return true;
	    }
	    return false;
	}

	@Override
	public boolean remove(Object o) {
	    if (this.collection.remove(o)) {
		dirty();
		return true;
	    }
	    return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
	    return this.collection.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
	    if (this.collection.addAll(c)) {
		dirty();
		return true;
	    }
	    return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
	    if (this.collection.removeAll(c)) {
		dirty();
		return true;
	    }
	    return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
	    if (this.collection.retainAll(c)) {
		dirty();
		return true;
	    }
	    return false;
	}

	@Override
	public void clear() {
	    if (!this.collection.isEmpty()) {
		this.collection.clear();
		dirty();
	    }
	}

	@Override
	public int hashCode() {
	    return this.collection.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
	    return this.collection.equals(obj);
	}
    }

    class SetProxy<E> extends CollectionProxy<E> implements Set<E> {

	public SetProxy(Set<E> collection) {
	    super(collection);
	}
    }

    class ListProxy<E> extends CollectionProxy<E> implements List<E> {

	public ListProxy(List<E> collection) {
	    super(collection);
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
	    if (((List<E>) this.collection).addAll(index, c)) {
		dirty();
		return true;
	    }
	    return false;
	}

	@Override
	public E get(int index) {
	    return ((List<E>) this.collection).get(index);
	}

	@Override
	public E set(int index, E element) {
	    E previousElement = ((List<E>) this.collection).set(index, element);
	    if (previousElement == null ? element != null : !previousElement.equals(element)) {
		dirty();
	    }
	    return previousElement;
	}

	@Override
	public void add(int index, E element) {
	    ((List<E>) this.collection).add(index, element);
	    dirty();
	}

	@Override
	public E remove(int index) {
	    E removedElement = ((List<E>) this.collection).remove(index);
	    dirty();
	    return removedElement;
	}

	@Override
	public int indexOf(Object o) {
	    return ((List<E>) this.collection).indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
	    return ((List<E>) this.collection).lastIndexOf(o);
	}

	@Override
	public ListIterator<E> listIterator() {
	    return listIterator(0);
	}

	@Override
	public ListIterator<E> listIterator(int index) {
	    return new ListIteratorProxy<>(((List<E>) this.collection).listIterator(index));
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
	    return new ListProxy<>(((List<E>) this.collection).subList(fromIndex, toIndex));
	}
    }

    class SortedSetProxy<E> extends SetProxy<E> implements SortedSet<E> {

	public SortedSetProxy(SortedSet<E> collection) {
	    super(collection);
	}

	@Override
	public Comparator<? super E> comparator() {
	    return ((SortedSet<E>) this.collection).comparator();
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
	    return new SortedSetProxy<>(((SortedSet<E>) this.collection).subSet(fromElement, toElement));
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
	    return new SortedSetProxy<>(((SortedSet<E>) this.collection).headSet(toElement));
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
	    return new SortedSetProxy<>(((SortedSet<E>) this.collection).tailSet(fromElement));
	}

	@Override
	public E first() {
	    return ((SortedSet<E>) this.collection).first();
	}

	@Override
	public E last() {
	    return ((SortedSet<E>) this.collection).last();
	}
    }

    class SortedMapProxy<K, V> implements SortedMap<K, V> {

	protected final SortedMap<K, V> sortedMap;

	public SortedMapProxy(SortedMap<K, V> sortedMap) {
	    this.sortedMap = sortedMap;
	}

	@Override
	public int size() {
	    return this.sortedMap.size();
	}

	@Override
	public boolean isEmpty() {
	    return this.sortedMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
	    return this.sortedMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
	    return this.sortedMap.containsValue(value);
	}

	@Override
	public V get(Object key) {
	    return this.sortedMap.get(key);
	}

	@Override
	public V put(K key, V value) {
	    boolean containsKey = this.sortedMap.containsKey(key);
	    V previousValue = this.sortedMap.put(key, value);
	    if (!containsKey || (previousValue == null ? value != null : !previousValue.equals(value))) {
		dirty();
	    }
	    return previousValue;
	}

	@Override
	public V remove(Object key) {
	    boolean containsKey = this.sortedMap.containsKey(key);
	    V removedValue = this.sortedMap.remove(key);
	    if (containsKey) {
		dirty();
	    }
	    return removedValue;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
	    for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
		put(entry.getKey(), entry.getValue());
	    }
	}

	@Override
	public void clear() {
	    if (!this.sortedMap.isEmpty()) {
		this.sortedMap.clear();
		dirty();
	    }
	}

	@Override
	public Comparator<? super K> comparator() {
	    return this.sortedMap.comparator();
	}

	@Override
	public SortedMap<K, V> subMap(K fromKey, K toKey) {
	    return new SortedMapProxy<>(this.sortedMap.subMap(fromKey, toKey));
	}

	@Override
	public SortedMap<K, V> headMap(K toKey) {
	    return new SortedMapProxy<>(this.sortedMap.headMap(toKey));
	}

	@Override
	public SortedMap<K, V> tailMap(K fromKey) {
	    return new SortedMapProxy<>(this.sortedMap.tailMap(fromKey));
	}

	@Override
	public K firstKey() {
	    return this.sortedMap.firstKey();
	}

	@Override
	public K lastKey() {
	    return this.sortedMap.lastKey();
	}

	@Override
	public Set<K> keySet() {
	    return new SetProxy<>(this.sortedMap.keySet());
	}

	@Override
	public Collection<V> values() {
	    return new CollectionProxy<>(this.sortedMap.values());
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
	    return new SetProxy<>(this.sortedMap.entrySet());
	}

	@Override
	public int hashCode() {
	    return this.sortedMap.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
	    return this.sortedMap.equals(obj);
	}
    }

    @Override
    public void addListener(ChangeListener<C> listener) {
	if (!this.changeListeners.contains(listener)) {
	    this.changeListeners.add(listener);
	}
    }

    @Override
    public void removeListener(ChangeListener<C> listener) {
	this.changeListeners.remove(listener);
    }

    static class DefaultCollectionLoader<C> implements Loader<C> {

	@Override
	public void load(PersistentCollection<C> collection, InitializationCallback<C> callback) {
	    throw new LazyInitializationException(collection.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(collection)));
	}

	@Override
	public void onInitializing() {
	    // Empty.
	}

	@Override
	public void onInitialize() {
	    // Empty.
	}

	@Override
	public void onUninitialize() {
	    // Empty.
	}
    }

    @Override
    public void addListener(InitializationListener<C> listener) {
	if (!this.initializationListeners.contains(listener)) {
	    this.initializationListeners.add(listener);
	}
    }

    @Override
    public void removeListener(InitializationListener<C> listener) {
	this.initializationListeners.remove(listener);
    }

    @Override
    public void initializing() {
	this.loader.onInitializing();
    }

    @Override
    public void initialize(C content, Initializer<C> initializer) {
	this.loader.onInitialize();

	doInitialize(content, initializer != null);
	if (initializer != null) {
	    initializer.initialize(content);
	}

	for (InitializationListener<C> listener : this.initializationListeners) {
	    listener.initialized(this);
	}

	log.debug("initialized");
    }

    protected abstract void doInitialize(C content, boolean empty);

    @Override
    public void uninitialize() {
	this.loader.onUninitialize();

	for (InitializationListener<C> listener : this.initializationListeners) {
	    listener.uninitialized(this);
	}

	this.collection = null;

	log.debug("uninitialized");
    }

    @Override
    public void withInitialized(InitializationCallback<C> callback) {
	if (wasInitialized()) {
	    callback.call(this);
	} else {
	    this.loader.load(this, callback);
	}
    }
}
