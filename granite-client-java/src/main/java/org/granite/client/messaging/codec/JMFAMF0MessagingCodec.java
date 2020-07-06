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
package org.granite.client.messaging.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.granite.client.messaging.jmf.ClientSharedContext;
import org.granite.messaging.amf.AMF0Message;
import org.granite.messaging.jmf.JMFDeserializer;
import org.granite.messaging.jmf.JMFSerializer;
import org.granite.util.ContentType;

/**
 * @author Franck WOLFF
 */
public class JMFAMF0MessagingCodec implements MessagingCodec<AMF0Message> {

    private final ClientSharedContext sharedContext;

    public JMFAMF0MessagingCodec(ClientSharedContext sharedContext) {
	this.sharedContext = sharedContext;
    }

    @Override
    public ClientType getClientType() {
	return ClientType.JAVA;
    }

    @Override
    public String getContentType() {
	return ContentType.JMF_AMF.mimeType();
    }

    @Override
    public void encode(AMF0Message message, OutputStream output) throws IOException {
	@SuppressWarnings("all")
	JMFSerializer serializer = new JMFSerializer(output, this.sharedContext);
	serializer.writeObject(message);
    }

    @Override
    public AMF0Message decode(InputStream input) throws IOException {
	@SuppressWarnings("all")
	JMFDeserializer deserializer = new JMFDeserializer(input, this.sharedContext);
	try {
	    return (AMF0Message) deserializer.readObject();
	} catch (ClassNotFoundException e) {
	    throw new IOException(e);
	}
    }
}
