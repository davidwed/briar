package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import javax.inject.Inject;

import static org.briarproject.android.BriarActivity.GROUP_ID;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class FeedPostFragment extends BasePostFragment {

	private static final String TAG = FeedPostFragment.class.getName();

	private MessageId postId;
	private GroupId blogId;

	@Inject
	FeedController feedController;

	static FeedPostFragment newInstance(GroupId blogId, MessageId postId) {
		FeedPostFragment f = new FeedPostFragment();

		Bundle bundle = new Bundle();
		bundle.putByteArray(GROUP_ID, blogId.getBytes());
		bundle.putByteArray(POST_ID, postId.getBytes());

		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		Bundle args = getArguments();
		byte[] b = args.getByteArray(GROUP_ID);
		if (b == null) throw new IllegalStateException("No group ID in args");
		blogId = new GroupId(b);

		byte[] p = args.getByteArray(POST_ID);
		if (p == null) throw new IllegalStateException("No post ID in args");
		postId = new MessageId(p);

		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		feedController.loadBlogPost(blogId, postId,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						this) {
					@Override
					public void onResultUi(BlogPostItem post) {
						onBlogPostLoaded(post);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO: Decide how to handle errors in the UI
					}
				});
	}

}
