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

import org.granite.client.messaging.RemoteAlias;
import org.granite.client.persistence.Entity;

@RemoteAlias("org.granite.example.addressbook.entity.Address")
@Entity
public class Address extends AbstractEntity {

    private static final long serialVersionUID = 1L;

    private String address1;
    private String address2;
    private String zipcode;
    private String city;
    private Country country;

    public String getAddress1() {
	return this.address1;
    }

    public void setAddress1(String address1) {
	this.address1 = address1;
    }

    public String getAddress2() {
	return this.address2;
    }

    public void setAddress2(String address2) {
	this.address2 = address2;
    }

    public String getCity() {
	return this.city;
    }

    public void setCity(String city) {
	this.city = city;
    }

    public String getZipcode() {
	return this.zipcode;
    }

    public void setZipcode(String zipcode) {
	this.zipcode = zipcode;
    }

    public Country getCountry() {
	return this.country;
    }

    public void setCountry(Country country) {
	this.country = country;
    }
}
