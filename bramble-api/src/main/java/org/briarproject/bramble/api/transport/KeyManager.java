package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.plugin.TransportId;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Responsible for managing transport keys and recognising the pseudo-random
 * tags of incoming streams.
 */
public interface KeyManager {

	/**
	 * Informs the key manager that a new contact has been added. Derives and
	 * stores a set of transport keys for communicating with the contact over
	 * each transport.
	 * <p/>
	 * {@link StreamContext StreamContexts} for the contact can be created
	 * after this method has returned.
	 *
	 * @param alice true if the local party is Alice
	 */
	void addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice) throws DbException;

	/**
	 * Derives and stores a set of unbound transport keys for each transport
	 * and returns the key set IDs.
	 * <p/>
	 * The keys must be bound before they can be used for incoming streams,
	 * and also activated before they can be used for outgoing streams.
	 *
	 * @param alice true if the local party is Alice
	 */
	Map<TransportId, KeySetId> addUnboundKeys(Transaction txn, SecretKey master,
			long timestamp, boolean alice) throws DbException;

	/**
	 * Binds the given transport keys to the given contact.
	 */
	void bindKeys(Transaction txn, ContactId c, Map<TransportId, KeySetId> keys)
			throws DbException;

	/**
	 * Marks the given transport keys as usable for outgoing streams. Keys must
	 * be bound before they are activated.
	 */
	void activateKeys(Transaction txn, Map<TransportId, KeySetId> keys)
			throws DbException;

	/**
	 * Removes the given transport keys, which must not have been bound, from
	 * the manager and the database.
	 */
	void removeKeys(Transaction txn, Map<TransportId, KeySetId> keys)
			throws DbException;

	/**
	 * Returns true if we have keys that can be used for outgoing streams to
	 * the given contact over the given transport.
	 */
	boolean canSendOutgoingStreams(ContactId c, TransportId t);

	/**
	 * Returns a {@link StreamContext} for sending a stream to the given
	 * contact over the given transport, or null if an error occurs or the
	 * contact does not support the transport.
	 */
	@Nullable
	StreamContext getStreamContext(ContactId c, TransportId t)
			throws DbException;

	/**
	 * Looks up the given tag and returns a {@link StreamContext} for reading
	 * from the corresponding stream, or null if an error occurs or the tag was
	 * unexpected.
	 */
	@Nullable
	StreamContext getStreamContext(TransportId t, byte[] tag)
			throws DbException;
}
