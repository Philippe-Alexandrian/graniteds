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

import java.io.ObjectInput;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import org.granite.messaging.persistence.PersistentCollectionSnapshot;

/**
 * @author Franck WOLFF
 */
public class PersistentSet<E> extends AbstractPersistentSimpleCollection<E, Set<E>> implements Set<E> {

    private static final long serialVersionUID = 1L;

    public PersistentSet() {
    }

    public PersistentSet(boolean initialized) {
	this(initialized ? new HashSet<E>() : null, false);
    }

    public PersistentSet(Set<E> collection) {
	this(collection, true);
    }

    public PersistentSet(Set<E> collection, boolean clone) {
	if (collection instanceof SortedSet) {
	    throw new IllegalArgumentException("Should not be a SortedSet: " + collection);
	}

	if (collection != null) {
	    init(clone ? new HashSet<>(collection) : collection, null, false);
	}
    }

    @Override
    public void doInitialize(Set<E> set, boolean empty) {
	init(empty ? new HashSet<E>() : set, null, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void updateFromSnapshot(ObjectInput in, PersistentCollectionSnapshot snapshot) {
	if (snapshot.isInitialized()) {
	    init(new HashSet<E>((Collection<? extends E>) snapshot.getElementsAsCollection()), snapshot.getDetachedState(), snapshot.isDirty());
	} else {
	    init(null, snapshot.getDetachedState(), false);
	}
    }

    @Override
    public PersistentSet<E> clone(boolean uninitialize) {
	PersistentSet<E> set = new PersistentSet<>();
	if (wasInitialized() && !uninitialize) {
	    set.init(getCollection(), null, isDirty());
	}
	return set;
    }
}
