package org.briarproject.android.sharing;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.sharing.ShareActivity.CONTACTS;
import static org.briarproject.android.sharing.ShareActivity.getContactsFromIds;
import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;

public class ContactSelectorFragment extends BaseFragment implements
		BaseContactListAdapter.OnItemClickListener {

	public final static String TAG = "ContactSelectorFragment";

	private static final Logger LOG =
			Logger.getLogger(ContactSelectorFragment.class.getName());

	private ShareActivity shareActivity;
	private Menu menu;
	private BriarRecyclerView list;
	private ContactSelectorAdapter adapter;
	private Collection<ContactId> selectedContacts;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ContactManager contactManager;
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	protected volatile ForumSharingManager forumSharingManager;

	private volatile GroupId groupId;

	public static ContactSelectorFragment newInstance(GroupId groupId) {

		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		ContactSelectorFragment fragment = new ContactSelectorFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			shareActivity = (ShareActivity) context;
		} catch (ClassCastException e) {
			throw new InstantiationError(
					"This fragment is only meant to be attached to a subclass of ShareActivity");
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
		Bundle args = getArguments();
		groupId = new GroupId(args.getByteArray(GROUP_ID));
		if (groupId == null) throw new IllegalStateException("No GroupId");
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View contentView = inflater.inflate(
				R.layout.introduction_contact_chooser, container, false);

		if (Build.VERSION.SDK_INT >= 21) {
			setExitTransition(new Fade());
		}

		adapter = new ContactSelectorAdapter(getActivity(), this);

		list = (BriarRecyclerView) contentView.findViewById(R.id.contactList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts_selector));

		// restore selected contacts if available
		if (savedInstanceState != null) {
			ArrayList<Integer> intContacts =
					savedInstanceState.getIntegerArrayList(CONTACTS);
			selectedContacts = ShareActivity.getContactsFromIntegers(
					intContacts);
		}

		return contentView;
	}

	@Override
	public void onResume() {
		super.onResume();

		if (selectedContacts != null)
			loadContacts(Collections.unmodifiableCollection(selectedContacts));
		else loadContacts(null);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (adapter != null) {
			selectedContacts = adapter.getSelectedContactIds();
			outState.putIntegerArrayList(CONTACTS,
					getContactsFromIds(selectedContacts));
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.forum_share_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
		this.menu = menu;
		// hide sharing action initially, if no contact is selected
		updateMenuItem();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				shareActivity.onBackPressed();
				return true;
			case R.id.action_share_forum:
				selectedContacts = adapter.getSelectedContactIds();
				shareActivity.showMessageScreen(groupId, selectedContacts);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onItemClick(View view, ContactListItem item) {
		((SelectableContactListItem) item).toggleSelected();
		adapter.notifyItemChanged(adapter.findItemPosition(item), item);

		updateMenuItem();
	}

	private void loadContacts(final Collection<ContactId> selection) {
		shareActivity.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					List<ContactListItem> contacts = new ArrayList<>();

					for (Contact c : contactManager.getActiveContacts()) {
						LocalAuthor localAuthor = identityManager
								.getLocalAuthor(c.getLocalAuthorId());
						// was this contact already selected?
						boolean selected = selection != null &&
								selection.contains(c.getId());
						// do we have already some sharing with that contact?
						boolean disabled = shareActivity.isDisabled(groupId, c);
						contacts.add(new SelectableContactListItem(c,
								localAuthor, groupId, selected, disabled));
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayContacts(Collections.unmodifiableList(contacts));
				} catch (DbException e) {
					displayContacts(Collections.<ContactListItem>emptyList());
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts(final List<ContactListItem> contacts) {
		shareActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!contacts.isEmpty()) adapter.addAll(contacts);
				else list.showData();
				updateMenuItem();
			}
		});
	}

	private void updateMenuItem() {
		if (menu == null) return;
		MenuItem item = menu.findItem(R.id.action_share_forum);
		if (item == null) return;

		selectedContacts = adapter.getSelectedContactIds();
		if (selectedContacts.size() > 0) {
			item.setVisible(true);
		} else {
			item.setVisible(false);
		}
	}
}