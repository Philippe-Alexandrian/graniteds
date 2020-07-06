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
package org.granite.client.test.model;

import java.util.UUID;

import org.granite.client.persistence.Id;
import org.granite.client.persistence.Uid;

public abstract class AbstractEntity extends Versioned {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    private boolean __initialized__ = true;
    @SuppressWarnings("unused")
    private String __detachedState__ = null;

    @Id
    private Integer id;
    @Uid
    private String uid;
    private boolean restricted;

    public Integer getId() {
	return this.id;
    }

    public boolean isRestricted() {
	return this.restricted;
    }

    @Override
    public boolean equals(Object o) {
	return ((o == this) || ((o instanceof AbstractEntity) && uid().equals(((AbstractEntity) o).uid())));
    }

    @Override
    public int hashCode() {
	return uid().hashCode();
    }

    private String uid() {
	if (this.uid == null) {
	    this.uid = UUID.randomUUID().toString();
	}
	return this.uid;
    }
}
