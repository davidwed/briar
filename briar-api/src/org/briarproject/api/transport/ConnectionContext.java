package org.briarproject.api.transport;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;

public class ConnectionContext {

	private final ContactId contactId;
	private final TransportId transportId;
	private final byte[] secret;
	private final long connection;
	private final boolean alice;

	public ConnectionContext(ContactId contactId, TransportId transportId,
			byte[] secret, long connection, boolean alice) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.secret = secret;
		this.connection = connection;
		this.alice = alice;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public byte[] getSecret() {
		return secret;
	}

	public long getConnectionNumber() {
		return connection;
	}

	public boolean getAlice() {
		return alice;
	}
}