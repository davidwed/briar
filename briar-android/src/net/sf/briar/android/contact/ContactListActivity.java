package net.sf.briar.android.contact;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarBinder;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.transport.ConnectionListener;
import net.sf.briar.api.transport.ConnectionRegistry;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;

import com.google.inject.Inject;

public class ContactListActivity extends BriarActivity
implements OnClickListener, DatabaseListener, ConnectionListener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;
	@Inject private ConnectionRegistry connectionRegistry;

	private ArrayAdapter<ContactListItem> adapter = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ContactListAdapter(this);
		ListView list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1f));
		list.setAdapter(adapter);
		layout.addView(list);

		Button addContactButton = new Button(this);
		addContactButton.setBackgroundResource(0);
		addContactButton.setLayoutParams(new LayoutParams(MATCH_PARENT,
				WRAP_CONTENT));
		addContactButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.social_add_person, 0, 0);
		addContactButton.setText(R.string.add_contact_button);
		addContactButton.setOnClickListener(this);
		layout.addView(addContactButton);

		setContentView(layout);

		// Listen for contacts being added or removed
		db.addListener(this);
		// Listen for contacts connecting or disconnecting
		connectionRegistry.addListener(this);
		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
		// Load the contact list from the DB
		reloadContactList();

		// Add some fake contacts to the database in a background thread
		// FIXME: Remove this
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					IBinder binder = serviceConnection.waitForBinder();
					((BriarBinder) binder).getService().waitForStartup();
					if(LOG.isLoggable(INFO)) LOG.info("Service started");
					Collection<Contact> contacts = db.getContacts();
					if(contacts.isEmpty()) {
						// Insert a couple of fake contacts
						db.addContact("Alice");
						db.addContact("Bob");
					}
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		db.removeListener(this);
		connectionRegistry.removeListener(this);
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactAddedEvent) reloadContactList();
		else if(e instanceof ContactRemovedEvent) reloadContactList();
	}

	private void reloadContactList() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					IBinder binder = serviceConnection.waitForBinder();
					((BriarBinder) binder).getService().waitForStartup();
					// Load the contacts from the database
					Collection<Contact> contacts = db.getContacts();
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + contacts.size() + " contacts");
					// Update the contact list
					updateContactList(contacts);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void updateContactList(final Collection<Contact> contacts) {
		runOnUiThread(new Runnable() {
			public void run() {
				adapter.clear();
				for(Contact c : contacts) {
					boolean conn = connectionRegistry.isConnected(c.getId());
					adapter.add(new ContactListItem(c, conn));
				}
				adapter.sort(ContactListItem.COMPARATOR);
			}
		});
	}

	public void contactConnected(final ContactId c) {
		setConnected(c, true);
	}

	public void contactDisconnected(ContactId c) {
		setConnected(c, false);
	}

	private void setConnected(final ContactId c, final boolean connected) {
		runOnUiThread(new Runnable() {
			public void run() {
				int count = adapter.getCount();
				for(int i = 0; i < count; i++) {
					ContactListItem item = adapter.getItem(i);
					if(item.getContactId().equals(c)) {
						item.setConnected(connected);
						return;
					}
				}
			}
		});
	}
}