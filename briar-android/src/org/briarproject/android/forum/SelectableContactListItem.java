package org.briarproject.android.forum;

import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.contact.ConversationItem;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import java.util.Collections;

// This class is not thread-safe
public class SelectableContactListItem extends ContactListItem {

	private boolean selected;

	public SelectableContactListItem(Contact contact, LocalAuthor localAuthor,
			GroupId groupId, boolean selected) {

		super(contact, localAuthor, false, groupId, Collections.<ConversationItem>emptyList());

		this.selected = selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public boolean isSelected() {
		return selected;
	}

	public void toggleSelected() {
		selected = !selected;
	}

}
