package org.briarproject.keyagreement;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.keyagreement.KeyAgreementConnection;
import org.briarproject.api.keyagreement.KeyAgreementListener;
import org.briarproject.api.keyagreement.Payload;
import org.briarproject.api.keyagreement.TransportDescriptor;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.Clock;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.keyagreement.KeyAgreementConstants.CONNECTION_TIMEOUT;

class KeyAgreementConnector {

	interface Callbacks {
		void connectionWaiting();
	}

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementConnector.class.getName());

	private final Callbacks callbacks;
	private final Clock clock;
	private final CryptoComponent crypto;
	private final PluginManager pluginManager;
	private final CompletionService<KeyAgreementConnection> connect;

	private final List<KeyAgreementListener> listeners =
			new ArrayList<KeyAgreementListener>();
	private final List<Future<KeyAgreementConnection>> pending =
			new ArrayList<Future<KeyAgreementConnection>>();
	private final CountDownLatch aliceLatch = new CountDownLatch(1);

	private volatile boolean alice = false;

	public KeyAgreementConnector(Callbacks callbacks, Clock clock,
			CryptoComponent crypto, PluginManager pluginManager,
			Executor ioExecutor) {
		this.callbacks = callbacks;
		this.clock = clock;
		this.crypto = crypto;
		this.pluginManager = pluginManager;
		connect = new ExecutorCompletionService<KeyAgreementConnection>(
				ioExecutor);
	}

	public Payload listen(KeyPair localKeyPair) {
		LOG.info("Starting BQP listeners");
		// Derive commitment
		byte[] publicKey = localKeyPair.getPublic().getEncoded();
		byte[] commitment = crypto.deriveKeyCommitment(publicKey);
		// Start all listeners and collect their descriptors
		List<TransportDescriptor> descriptors =
				new ArrayList<TransportDescriptor>();
		for (DuplexPlugin plugin : pluginManager.getKeyAgreementPlugins()) {
			KeyAgreementListener l = plugin.createKeyAgreementListener(
					commitment);
			if (l != null) {
				TransportDescriptor d = l.getDescriptor();
				descriptors.add(d);
				pending.add(connect.submit(new ReadableTask(l.listen())));
				listeners.add(l);
			}
		}
		return new Payload(commitment, descriptors);
	}

	public void stopListening() {
		LOG.info("Stopping BQP listeners");
		for (KeyAgreementListener l : listeners) l.close();
	}

	public KeyAgreementTransport connect(Payload remotePayload, boolean alice) {
		// Let the readable tasks know if we are Alice
		this.alice = alice;
		aliceLatch.countDown();
		long end = clock.currentTimeMillis() + CONNECTION_TIMEOUT;

		// Start connecting over supported transports
		LOG.info("Starting outgoing BQP connections");
		for (TransportDescriptor d : remotePayload.getTransportDescriptors()) {
			Plugin p = pluginManager.getPlugin(d.getId());
			if (p != null && p instanceof DuplexPlugin) {
				DuplexPlugin plugin = (DuplexPlugin) p;
				pending.add(connect.submit(new ReadableTask(new ConnectorTask(
						plugin, remotePayload.getCommitment(), d, end))));
			}
		}

		// Get chosen connection
		KeyAgreementConnection chosen = null;
		try {
			LOG.info("Waiting for connection");
			long now = clock.currentTimeMillis();
			Future<KeyAgreementConnection> f =
					connect.poll(end - now, MILLISECONDS);
			if (f == null) {
				LOG.info("No connection within timeout");
				return null;
			}
			chosen = f.get();
			if (chosen == null) throw new IllegalStateException();
			return new KeyAgreementTransport(chosen);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while waiting for connection");
			Thread.currentThread().interrupt();
			return null;
		} catch (ExecutionException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} finally {
			stopListening();
			// Close all other connections
			closePending(chosen);
		}
	}

	private void closePending(KeyAgreementConnection chosen) {
		for (Future<KeyAgreementConnection> f : pending) {
			if (f.cancel(true)) {
				LOG.info("Cancelled redundant task");
			} else {
				try {
					KeyAgreementConnection c = f.get();
					if (c == null) throw new IllegalStateException();
					if (c != chosen) {
						LOG.info("Closing redundant connection");
						tryToClose(c.getConnection());
					}
				} catch (InterruptedException e) {
					LOG.info("Interrupted while closing connections");
					Thread.currentThread().interrupt();
					return;
				} catch (ExecutionException e) {
					if (LOG.isLoggable(INFO)) LOG.info(e.toString());
				}
			}
		}
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			conn.getReader().dispose(false, true);
			conn.getWriter().dispose(false);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) LOG.info(e.toString());
		}
	}

	private class ConnectorTask implements Callable<KeyAgreementConnection> {

		private final byte[] commitment;
		private final TransportDescriptor descriptor;
		private final long end;
		private final DuplexPlugin plugin;

		private ConnectorTask(DuplexPlugin plugin, byte[] commitment,
				TransportDescriptor descriptor, long end) {
			this.plugin = plugin;
			this.commitment = commitment;
			this.descriptor = descriptor;
			this.end = end;
		}

		@Override
		public KeyAgreementConnection call() throws Exception {
			// Repeat attempts until we connect or get interrupted
			long now = clock.currentTimeMillis();
			while (now < end) {
				DuplexTransportConnection conn =
						plugin.createKeyAgreementConnection(commitment,
								descriptor, end - now);
				if (conn != null) {
					LOG.info("Outgoing connection");
					return new KeyAgreementConnection(conn, plugin.getId());
				}
				// Wait 2s before retry (to circumvent transient failures)
				Thread.sleep(2000);
				now = clock.currentTimeMillis();
			}
			throw new IOException("Timed out");
		}
	}

	private class ReadableTask implements Callable<KeyAgreementConnection> {

		private final Callable<KeyAgreementConnection> connectionTask;

		private ReadableTask(Callable<KeyAgreementConnection> connectionTask) {
			this.connectionTask = connectionTask;
		}

		@Override
		public KeyAgreementConnection call() throws Exception {
			KeyAgreementConnection c = connectionTask.call();
			InputStream in = c.getConnection().getReader().getInputStream();
			aliceLatch.await();
			if (alice) return c;
			boolean waitingSent = false;
			while (in.available() == 0) {
				if (!waitingSent) {
					// Bob waits here until Alice obtains his payload.
					callbacks.connectionWaiting();
					waitingSent = true;
				}
				LOG.info("Waiting for data");
				Thread.sleep(1000);
			}
			LOG.info("Data available");
			return c;
		}
	}
}
