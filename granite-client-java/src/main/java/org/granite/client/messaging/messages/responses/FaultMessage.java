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
package org.granite.client.messaging.messages.responses;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

/**
 * @author Franck WOLFF
 */
public final class FaultMessage extends AbstractResponseMessage {

    private static final long serialVersionUID = 1L;

    public static enum Code {
	UNKNOWN,

	CLIENT_CALL_FAILED, CLIENT_CALL_TIMED_OUT, CLIENT_CALL_CANCELLED,

	SERVER_CALL_FAILED,

	INVALID_CREDENTIALS, AUTHENTICATION_FAILED, NOT_LOGGED_IN, SESSION_EXPIRED, ACCESS_DENIED,

	VALIDATION_FAILED, OPTIMISTIC_LOCK
    }

    private Code code;
    private String description;
    private String details;
    private Object cause;
    private Map<String, Object> extended;

    private String unknownCode;

    public FaultMessage() {
    }

    public FaultMessage(String clientId, String correlationId, Code code, String description, String details, Object cause, Map<String, Object> extended) {

	super(clientId, correlationId);

	this.code = code;
	this.description = description;
	this.details = details;
	this.cause = cause;
	this.extended = extended;
    }

    public FaultMessage(String id, String clientId, long timestamp, long timeToLive, Map<String, Object> headers, String correlationId, Code code, String description,
	    String details, Object cause, Map<String, Object> extended) {

	super(id, clientId, timestamp, timeToLive, headers, correlationId);

	this.code = code;
	this.description = description;
	this.details = details;
	this.cause = cause;
	this.extended = extended;
    }

    @Override
    public Type getType() {
	return Type.FAULT;
    }

    public boolean isSecurityFault() {
	switch (this.code) {
	case UNKNOWN:
	    return (this.unknownCode != null) && this.unknownCode.startsWith("Server.Security.");

	case INVALID_CREDENTIALS:
	case AUTHENTICATION_FAILED:
	case NOT_LOGGED_IN:
	case SESSION_EXPIRED:
	case ACCESS_DENIED:
	    return true;

	// $CASES-OMITTED$
	default:
	    return false;
	}
    }

    @Override
    public Object getData() {
	return toString();
    }

    public Code getCode() {
	return this.code;
    }

    public void setCode(Code code) {
	this.code = code;
    }

    public String getDescription() {
	return this.description;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    public String getDetails() {
	return this.details;
    }

    public void setDetails(String details) {
	this.details = details;
    }

    public Object getCause() {
	return this.cause;
    }

    public void setCause(Object cause) {
	this.cause = cause;
    }

    public Map<String, Object> getExtended() {
	return this.extended;
    }

    public void setExtended(Map<String, Object> extended) {
	this.extended = extended;
    }

    public String getUnknownCode() {
	return this.unknownCode;
    }

    public void setUnknownCode(String unknownCode) {
	this.unknownCode = unknownCode;
    }

    @Override
    public FaultMessage copy() {
	FaultMessage message = new FaultMessage();

	super.copy(message);

	message.code = this.code;
	message.description = this.description;
	message.details = this.details;
	message.cause = this.cause;
	message.extended = this.extended;

	return message;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	super.readExternal(in);

	this.code = (Code) in.readObject();
	this.description = in.readUTF();
	this.details = in.readUTF();
	this.cause = in.readObject();
	this.extended = (Map<String, Object>) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
	super.writeExternal(out);

	out.writeObject(this.code);
	if (this.description != null) {
	    out.writeUTF(this.description);
	} else {
	    out.writeObject(null);
	}
	if (this.details != null) {
	    out.writeUTF(this.details);
	} else {
	    out.writeObject(null);
	}
	out.writeObject(this.cause);
	out.writeObject(this.extended);
    }

    @Override
    public StringBuilder toString(StringBuilder sb) {
	return super.toString(sb).append("\n    code=").append(this.code).append("\n    description=").append(this.description).append("\n    details=").append(this.details)
		.append("\n    cause=").append(this.cause).append("\n    extended=").append(this.extended);
    }
}
