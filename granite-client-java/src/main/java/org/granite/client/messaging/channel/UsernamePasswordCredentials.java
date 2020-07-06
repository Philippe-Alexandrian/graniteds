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
package org.granite.client.messaging.channel;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.granite.util.Base64;

/**
 * Simple username/password credentials
 *
 * @author Franck WOLFF
 */
public final class UsernamePasswordCredentials implements Credentials {

    private final String username;
    private final String password;
    private final Charset charset;

    /**
     * Create credentials with the specified username and password
     * 
     * @param username username
     * @param password password
     */
    public UsernamePasswordCredentials(String username, String password) {
	this(username, password, null);
    }

    /**
     * Create credentials with the specified username, password and charset (for localized usernames)
     * 
     * @param username username
     * @param password password
     * @param charset charset
     */
    public UsernamePasswordCredentials(String username, String password, Charset charset) {
	this.username = username;
	this.password = password;
	this.charset = (charset != null ? charset : Charset.defaultCharset());
    }

    /**
     * Current username
     * 
     * @return username
     */
    public String getUsername() {
	return this.username;
    }

    /**
     * Current password
     * 
     * @return password
     */
    public String getPassword() {
	return this.password;
    }

    /**
     * Current charset
     * 
     * @return charset
     */
    public Charset getCharset() {
	return this.charset;
    }

    public String encodeBase64() throws UnsupportedEncodingException {
	StringBuilder sb = new StringBuilder();
	if (this.username != null) {
	    if (this.username.indexOf(':') != -1) {
		throw new UnsupportedEncodingException("Username cannot contain ':' characters: " + this.username);
	    }
	    sb.append(this.username);
	}
	sb.append(':');
	if (this.username != null) {
	    sb.append(this.password);
	}
	return Base64.encodeToString(sb.toString().getBytes(this.charset.name()), false);
    }

    @Override
    public String toString() {
	return getClass().getName() + " {username=***, password=***, charset=" + this.charset + "}";
    }
}
