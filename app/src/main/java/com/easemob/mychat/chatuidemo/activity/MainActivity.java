/**
 * Copyright (C) 2013-2014 EaseMob Technologies. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easemob.mychat.chatuidemo.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.easemob.EMCallBack;
import com.easemob.EMConnectionListener;
import com.easemob.EMError;
import com.easemob.EMEventListener;
import com.easemob.EMGroupChangeListener;
import com.easemob.EMNotifierEvent;
import com.easemob.EMValueCallBack;
import com.easemob.mychat.applib.controller.HXSDKHelper;
import com.easemob.chat.EMChatManager;
import com.easemob.chat.EMContactListener;
import com.easemob.chat.EMContactManager;
import com.easemob.chat.EMConversation;
import com.easemob.chat.EMConversation.EMConversationType;
import com.easemob.chat.EMGroup;
import com.easemob.chat.EMGroupManager;
import com.easemob.chat.EMMessage;
import com.easemob.chat.EMMessage.ChatType;
import com.easemob.chat.EMMessage.Type;
import com.easemob.chat.TextMessageBody;
import com.easemob.mychat.chatuidemo.Constant;
import com.easemob.mychat.chatuidemo.DemoHXSDKHelper;
import com.easemob.mychat.chatuidemo.R;
import com.easemob.mychat.chatuidemo.db.InviteMessgeDao;
import com.easemob.mychat.chatuidemo.db.UserDao;
import com.easemob.mychat.chatuidemo.domain.InviteMessage;
import com.easemob.mychat.chatuidemo.domain.InviteMessage.InviteMesageStatus;
import com.easemob.mychat.chatuidemo.domain.User;
import com.easemob.mychat.chatuidemo.utils.CommonUtils;
import com.easemob.util.EMLog;
import com.easemob.util.HanziToPinyin;
import com.easemob.util.NetUtils;
import com.umeng.analytics.MobclickAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**     环信所有demo 中，核心是 MainActivity。
 *   MainActivity 前面由于一个SplashActivity 。
 *
 * MainActivity   布局     中包含三个碎片，     包含聊天List ，联系人List ， 设置 List
 * MainActivity   逻辑     和三个碎片相关的监听和刷新UI， 以及调用其它 Dialog
 * */




public class MainActivity extends BaseActivity implements EMEventListener {       /**
 其中BaseActivity extends FragmentActivity
 */

	protected static final String TAG = "MainActivity";

	// 未读消息textview
	private TextView unreadLabel;

	// 未读通讯录textview
	private TextView unreadAddressLable;

	private Button[] mTabs;

	private ChatAllHistoryFragment chatHistoryFragment;       /**  Fragment 1  历史聊天碎片    */
	private ContactlistFragment contactListFragment;          /**  Fragment 2   联系人碎片  */
	private SettingsFragment settingFragment;                 /**  Fragment 3   设置碎片    */

	private Fragment[] fragments;     /**  碎片数组    */
	private int index;

	// 当前fragment的index
	private int currentTabIndex;       /**   当前碎片 ID   */

	// 账号在别处登录
	public boolean isConflict = false;       /**    账号在别处登录   */
	// 账号被移除
	private boolean isCurrentAccountRemoved = false;         /**    账号被移除  */
	
	private MyConnectionListener connectionListener = null;
	private MyGroupChangeListener groupChangeListener = null;

	/**
	 * 检查当前用户是否被删除
	 */
	public boolean getCurrentAccountRemoved() {
		return isCurrentAccountRemoved;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null && savedInstanceState.getBoolean(Constant.ACCOUNT_REMOVED, false)) {
			// 防止被移除后，没点确定按钮然后按了home键，长期在后台又进app导致的crash
			// 三个fragment里加的判断同理
		    DemoHXSDKHelper.getInstance().logout(true,null);       /**   如果账户被移除，退出MainActivity,
			 进入登录界面 	 */
			finish();
			startActivity(new Intent(this, LoginActivity.class));         /**  进入登录界面    */
			return;
		} else if (savedInstanceState != null && savedInstanceState.getBoolean("isConflict", false)) {
			// 防止被T后，没点确定按钮然后按了home键，长期在后台又进app导致的crash
			// 三个fragment里加的判断同理
			finish();
			startActivity(new Intent(this, LoginActivity.class));        /**   如果被挤掉,
			 退出MainActivity，进入登录界面 	 */
			return;
		}
		setContentView(R.layout.activity_main);       /**    如果不被挤掉，也不被，则进入  */

		initView();       /**   通过findViewByID  初始化控件 */

		// MobclickAgent.setDebugMode( true );
		// --?--
		MobclickAgent.updateOnlineConfig(this);   /**  应该是 SDK 中，用于通知用户数据的一个方法  */

		if (getIntent().getBooleanExtra("conflict", false) && !isConflictDialogShow) {
			showConflictDialog();     /**   提示账户被别人挤掉  */
		} else if (getIntent().getBooleanExtra(Constant.ACCOUNT_REMOVED, false) && !isAccountRemovedDialogShow) {
			showAccountRemovedDialog();   /**  提示账户被删除  */
		}

		inviteMessgeDao = new InviteMessgeDao(this);
		userDao = new UserDao(this);
		// 这个fragment只显示好友和群组的聊天记录
		// chatHistoryFragment = new ChatHistoryFragment();
		// 显示所有人消息记录的fragment

		/**    主界面中的三个  Fragment  */
		chatHistoryFragment = new ChatAllHistoryFragment();
		contactListFragment = new ContactlistFragment();
		settingFragment = new SettingsFragment();

		fragments = new Fragment[] { chatHistoryFragment, contactListFragment, settingFragment };
		// 添加显示第一个fragment
		getSupportFragmentManager()  //  动态加载  Fragment 操作
				.beginTransaction()//   开始事务
				.add(R.id.fragment_container, chatHistoryFragment)// 添加历史聊天碎片
				.add(R.id.fragment_container, contactListFragment)// 添加联系人碎片
				.hide(contactListFragment)//   隐藏联系人碎片
				.show(chatHistoryFragment)//  显示历史聊天碎片
				.commit();    // 提交事务

		init();

		//异步获取当前用户的昵称和头像
		((DemoHXSDKHelper)HXSDKHelper.getInstance()).getUserProfileManager().asyncGetCurrentUserInfo();

	}

	private void init() {

		// setContactListener监听联系人的变化等
		EMContactManager.getInstance().setContactListener(new MyContactListener());   /**   添加联系人监听
		 */

		// 注册一个监听连接状态的listener
		connectionListener = new MyConnectionListener();     /**   添加连接状态监听 */
		EMChatManager.getInstance().addConnectionListener(connectionListener);

		groupChangeListener = new MyGroupChangeListener();// 注册群聊相关的listener
        EMGroupManager.getInstance().addGroupChangeListener(groupChangeListener);  /**   添加群相关链接 */

		
		//内部测试方法，请忽略
		registerInternalDebugReceiver();
	}


	
	static void asyncFetchGroupsFromServer(){    /**   异步从服务器获取群组  */
	    HXSDKHelper.getInstance().asyncFetchGroupsFromServer(new EMCallBack()
		{

			@Override
			public void onSuccess()
			{
				HXSDKHelper.getInstance().noitifyGroupSyncListeners(true);  /**  通知群异步监听  */
				if (HXSDKHelper.getInstance().isContactsSyncedWithServer())
				{
					HXSDKHelper.getInstance().notifyForRecevingEvents();
				}
			}

			@Override
			public void onError(int code, String message)
			{
				HXSDKHelper.getInstance().noitifyGroupSyncListeners(false);
			}

			@Override
			public void onProgress(int progress, String status)
			{

			}

		});
	}
	
	static void asyncFetchContactsFromServer(){   /**  从服务器获取联系人  */
	    HXSDKHelper.getInstance().asyncFetchContactsFromServer(new EMValueCallBack<List<String>>()
		{

			@Override
			public void onSuccess(List<String> usernames)
			{    /**   异步回调方法，回调中的参数， user 的 List  */
				Context context = HXSDKHelper.getInstance().getAppContext();

				System.out.println("----------------" + usernames.toString());
				EMLog.d("roster", "contacts size: " + usernames.size());
				Map<String, User> userlist = new HashMap<String, User>();
				for (String username : usernames)
				{
					User user = new User();
					user.setUsername(username);
					setUserHearder(username, user);
					userlist.put(username, user);
				}

				// 添加user"申请与通知"
				User newFriends = new User();
				newFriends.setUsername(Constant.NEW_FRIENDS_USERNAME);
				String strChat = context.getString(R.string.Application_and_notify);
				newFriends.setNick(strChat);
				userlist.put(Constant.NEW_FRIENDS_USERNAME, newFriends);

				// 添加"群聊"
				User   groupUser = new User();
				String strGroup  = context.getString(R.string.group_chat);
				groupUser.setUsername(Constant.GROUP_USERNAME);
				groupUser.setNick(strGroup);
				groupUser.setHeader("");
				userlist.put(Constant.GROUP_USERNAME, groupUser);

				// 添加"聊天室"
				User   chatRoomItem = new User();
				String strChatRoom  = context.getString(R.string.chat_room);
				chatRoomItem.setUsername(Constant.CHAT_ROOM);
				chatRoomItem.setNick(strChatRoom);
				chatRoomItem.setHeader("");
				userlist.put(Constant.CHAT_ROOM, chatRoomItem);

				// 添加"Robot"
				User   robotUser = new User();
				String strRobot  = context.getString(R.string.robot_chat);
				robotUser.setUsername(Constant.CHAT_ROBOT);
				robotUser.setNick(strRobot);
				robotUser.setHeader("");
				userlist.put(Constant.CHAT_ROBOT, robotUser);

				// 存入内存
				((DemoHXSDKHelper) HXSDKHelper.getInstance()).setContactList(userlist);
				// 存入db
				UserDao    dao   = new UserDao(context);
				List<User> users = new ArrayList<User>(userlist.values());
				dao.saveContactList(users);

				HXSDKHelper.getInstance().notifyContactsSyncListener(true);

				if (HXSDKHelper.getInstance().isGroupsSyncedWithServer())
				{
					HXSDKHelper.getInstance().notifyForRecevingEvents();
				}

				((DemoHXSDKHelper) HXSDKHelper.getInstance()).getUserProfileManager().asyncFetchContactInfosFromServer(usernames, new EMValueCallBack<List<User>>()
				{

					@Override
					public void onSuccess(List<User> uList)
					{
						((DemoHXSDKHelper) HXSDKHelper.getInstance()).updateContactList(uList);
						((DemoHXSDKHelper) HXSDKHelper.getInstance()).getUserProfileManager().notifyContactInfosSyncListener(true);
					}

					@Override
					public void onError(int error, String errorMsg)
					{
					}
				});
			}

			@Override
			public void onError(int error, String errorMsg)
			{
				HXSDKHelper.getInstance().notifyContactsSyncListener(false);
			}

		});
	}
	
	static void asyncFetchBlackListFromServer(){   /**   异步获取黑名单 List  */
	    HXSDKHelper.getInstance().asyncFetchBlackListFromServer(new EMValueCallBack<List<String>>()
		{

			@Override
			public void onSuccess(List<String> value)
			{
				EMContactManager.getInstance().saveBlackList(value);
				HXSDKHelper.getInstance().notifyBlackListSyncListener(true);
			}

			@Override
			public void onError(int error, String errorMsg)
			{
				HXSDKHelper.getInstance().notifyBlackListSyncListener(false);
			}

		});
	}
	
	/**
     * 设置hearder属性，方便通讯中对联系人按header分类显示，以及通过右侧ABCD...字母栏快速定位联系人
     * 
     * @param username
     * @param user
     */
    private static void setUserHearder(String username, User user) {   /**   设置联系人首字母     */
        String headerName = null;
        if (!TextUtils.isEmpty(user.getNick())) {
            headerName = user.getNick();
        } else {
            headerName = user.getUsername();
        }
        if (username.equals(Constant.NEW_FRIENDS_USERNAME)) {
            user.setHeader("");
        } else if (Character.isDigit(headerName.charAt(0))) {
            user.setHeader("#");
        } else {
            user.setHeader(HanziToPinyin.getInstance().get(headerName.substring(0, 1)).get(0).target.substring(0, 1)
                    .toUpperCase());
            char header = user.getHeader().toLowerCase().charAt(0);
            if (header < 'a' || header > 'z') {
                user.setHeader("#");
            }
        }
    }
    
	/**
	 * 初始化组件
	 */
	private void initView() {

		unreadLabel = (TextView) findViewById(R.id.unread_msg_number);
		unreadAddressLable = (TextView) findViewById(R.id.unread_address_number);

		mTabs = new Button[3];          /**     和 Fragment  相对应的三个按钮  */
		mTabs[0] = (Button) findViewById(R.id.btn_conversation);
		mTabs[1] = (Button) findViewById(R.id.btn_address_list);
		mTabs[2] = (Button) findViewById(R.id.btn_setting);
		// 把第一个tab设为选中状态
		mTabs[0].setSelected(true);

		registerForContextMenu(mTabs[1]);
	}

	/**
	 * button点击事件
	 * 
	 * @param view
	 */
	public void onTabClicked(View view) {        /**     xml 布局中 ，静态注册 onclick   */
		switch (view.getId()) {
		case R.id.btn_conversation:
			index = 0;
			break;
		case R.id.btn_address_list:
			index = 1;
			break;
		case R.id.btn_setting:
			index = 2;
			break;
		}
		if (currentTabIndex != index) {
			FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
			trx.hide(fragments[currentTabIndex]);
			if (!fragments[index].isAdded()) {
				trx.add(R.id.fragment_container, fragments[index]);
			}
			trx.show(fragments[index]).commit();
		}
		mTabs[currentTabIndex].setSelected(false);
		// 把当前tab设为选中状态
		mTabs[index].setSelected(true);
		currentTabIndex = index;
	}

	/**
	 * 监听事件
     */
	@Override
	public void onEvent(EMNotifierEvent event) {         /**     环信监听事件   */
		switch (event.getEvent()) {
		case EventNewMessage: // 普通消息
		{
			EMMessage message = (EMMessage) event.getData();

			// 提示新消息
			HXSDKHelper.getInstance().getNotifier().onNewMsg(message);

			refreshUI();
			break;
		}

		case EventOfflineMessage: {
			refreshUI();
			break;
		}

		case EventConversationListChanged: {
		    refreshUI();
		    break;
		}
		
		default:
			break;
		}
	}

	private void refreshUI() {        /**   在主线程中刷新UI线程     */
		runOnUiThread(new Runnable() {
			public void run() {
				// 刷新bottom bar消息未读数
				updateUnreadLabel();
				if (currentTabIndex == 0) {
					// 当前页面如果为聊天历史页面，刷新此页面
					if (chatHistoryFragment != null) {
						chatHistoryFragment.refresh();
					}
				}
			}
		});
	}

	@Override
	public void back(View view) {
		super.back(view);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();		
		
		if (conflictBuilder != null) {
			conflictBuilder.create().dismiss();
			conflictBuilder = null;
		}

		if(connectionListener != null){
		    EMChatManager.getInstance().removeConnectionListener(connectionListener);
		}
		
		if(groupChangeListener != null){
		    EMGroupManager.getInstance().removeGroupChangeListener(groupChangeListener);
		}
		
		try {
            unregisterReceiver(internalDebugReceiver);
        } catch (Exception e) {
        }
	}

	/**
	 * 刷新未读消息数
	 */
	public void updateUnreadLabel() {
		int count = getUnreadMsgCountTotal();
		if (count > 0) {
			unreadLabel.setText(String.valueOf(count));
			unreadLabel.setVisibility(View.VISIBLE);
		} else {
			unreadLabel.setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * 刷新申请与通知消息数
	 */
	public void updateUnreadAddressLable() {
		runOnUiThread(new Runnable() {
			public void run() {
				int count = getUnreadAddressCountTotal();
				if (count > 0) {
//					unreadAddressLable.setText(String.valueOf(count));
					unreadAddressLable.setVisibility(View.VISIBLE);
				} else {
					unreadAddressLable.setVisibility(View.INVISIBLE);
				}
			}
		});

	}

	/**
	 * 获取未读申请与通知消息
	 * 
	 * @return
	 */
	public int getUnreadAddressCountTotal() {
		int unreadAddressCountTotal = 0;
		if (((DemoHXSDKHelper)HXSDKHelper.getInstance()).getContactList().get(Constant.NEW_FRIENDS_USERNAME) != null)
			unreadAddressCountTotal = ((DemoHXSDKHelper)HXSDKHelper.getInstance()).getContactList().get(Constant.NEW_FRIENDS_USERNAME)
					.getUnreadMsgCount();
		return unreadAddressCountTotal;
	}

	/**
	 * 获取未读消息数
	 * 
	 * @return
	 */
	public int getUnreadMsgCountTotal() {
		int unreadMsgCountTotal = 0;
		int chatroomUnreadMsgCount = 0;
		unreadMsgCountTotal = EMChatManager.getInstance().getUnreadMsgsCount();
		for(EMConversation conversation: EMChatManager.getInstance().getAllConversations().values()){
			if(conversation.getType() == EMConversationType.ChatRoom)
			chatroomUnreadMsgCount=chatroomUnreadMsgCount+conversation.getUnreadMsgCount();
		}
		return unreadMsgCountTotal-chatroomUnreadMsgCount;
	}

	private InviteMessgeDao inviteMessgeDao;
	private UserDao userDao;

	/***
	 * 好友变化listener
	 * 新增：别人同意你的请求之后，你的好友 List 里必然增加一个好友       删除：别人把你拉黑之后，你的好友List ，少一个         添加通知：申请别人为好友，之后的监听
	 */
	public class MyContactListener implements EMContactListener {

		@Override
		public void onContactAdded(List<String> usernameList) {       /**     新增   */
			// 保存增加的联系人
			Map<String, User> localUsers = ((DemoHXSDKHelper)HXSDKHelper.getInstance()).getContactList();
			Map<String, User> toAddUsers = new HashMap<String, User>();
			for (String username : usernameList) {
				User user = setUserHead(username);
				// 添加好友时可能会回调added方法两次
				if (!localUsers.containsKey(username)) {
					userDao.saveContact(user);
				}
				toAddUsers.put(username, user);
			}
			localUsers.putAll(toAddUsers);
			// 刷新ui
			if (currentTabIndex == 1)
				contactListFragment.refresh();

		}

		@Override
		public void onContactDeleted(final List<String> usernameList) {     /**      删除  */
			// 被删除
			Map<String, User> localUsers = ((DemoHXSDKHelper)HXSDKHelper.getInstance()).getContactList();
			for (String username : usernameList) {
				localUsers.remove(username);
				userDao.deleteContact(username);
				inviteMessgeDao.deleteMessage(username);
			}
			runOnUiThread(new Runnable() {
				public void run() {
					// 如果正在与此用户的聊天页面
					String st10 = getResources().getString(R.string.have_you_removed);
					if (ChatActivity.activityInstance != null
							&& usernameList.contains(ChatActivity.activityInstance.getToChatUsername())) {
						Toast.makeText(MainActivity.this, ChatActivity.activityInstance
								.getToChatUsername() + st10, Toast.LENGTH_SHORT)
								.show();
						ChatActivity.activityInstance.finish();
					}
					updateUnreadLabel();
					// 刷新ui
					contactListFragment.refresh();
					chatHistoryFragment.refresh();
				}
			});

		}

		@Override
		public void onContactInvited(String username, String reason) {       /**     收到好友申请   */
			
			// 接到邀请的消息，如果不处理(同意或拒绝)，掉线后，服务器会自动再发过来，所以客户端不需要重复提醒
			List<InviteMessage> msgs = inviteMessgeDao.getMessagesList();

			for (InviteMessage inviteMessage : msgs) {
				if (inviteMessage.getGroupId() == null && inviteMessage.getFrom().equals(username)) {
					inviteMessgeDao.deleteMessage(username);
				}
			}
			// 自己封装的javabean
			InviteMessage msg = new InviteMessage();
			msg.setFrom(username);
			msg.setTime(System.currentTimeMillis());
			msg.setReason(reason);
			Log.d(TAG, username + "请求加你为好友,reason: " + reason);
			// 设置相应status
			msg.setStatus(InviteMesageStatus.BEINVITEED);
			notifyNewIviteMessage(msg);

		}

		@Override
		public void onContactAgreed(String username) {    /**     别人同意你的好友请求   */
			List<InviteMessage> msgs = inviteMessgeDao.getMessagesList();
			for (InviteMessage inviteMessage : msgs) {
				if (inviteMessage.getFrom().equals(username)) {
					return;
				}
			}
			// 自己封装的javabean
			InviteMessage msg = new InviteMessage();
			msg.setFrom(username);
			msg.setTime(System.currentTimeMillis());
			Log.d(TAG, username + "同意了你的好友请求");
			msg.setStatus(InviteMesageStatus.BEAGREED);
			notifyNewIviteMessage(msg);

		}

		@Override
		public void onContactRefused(String username) {      /**     别人拒绝你   */
			
			// 参考同意，被邀请实现此功能,demo未实现
			Log.d(username, username + "拒绝了你的好友请求");
		}

	}

	/**
	 * 连接监听listener
	 * 
	 */
	public class MyConnectionListener implements EMConnectionListener {

		@Override
		public void onConnected() {    /**     连接时的回调   */
            boolean groupSynced = HXSDKHelper.getInstance().isGroupsSyncedWithServer();
            boolean contactSynced = HXSDKHelper.getInstance().isContactsSyncedWithServer();
            
            // in case group and contact were already synced, we supposed to notify sdk we are ready to receive the events
            if(groupSynced && contactSynced){
                new Thread(){
                    @Override
                    public void run(){
                        HXSDKHelper.getInstance().notifyForRecevingEvents();
                    }
                }.start();
            }else{
                if(!groupSynced){
                    asyncFetchGroupsFromServer();
                }
                
                if(!contactSynced){
                    asyncFetchContactsFromServer();
                }
                
                if(!HXSDKHelper.getInstance().isBlackListSyncedWithServer()){
                    asyncFetchBlackListFromServer();
                }
            }
            
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					chatHistoryFragment.errorItem.setVisibility(View.GONE);
				}

			});
		}

		@Override
		public void onDisconnected(final int error) {    /**     断开连接时回调   */
			final String st1 = getResources().getString(R.string.can_not_connect_chat_server_connection);
			final String st2 = getResources().getString(R.string.the_current_network);
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (error == EMError.USER_REMOVED) {
						// 显示帐号已经被移除
						showAccountRemovedDialog();
					} else if (error == EMError.CONNECTION_CONFLICT) {
						// 显示帐号在其他设备登陆dialog
						showConflictDialog();
					} else {
						chatHistoryFragment.errorItem.setVisibility(View.VISIBLE);
						if (NetUtils.hasNetwork(MainActivity.this))
							chatHistoryFragment.errorText.setText(st1);
						else
							chatHistoryFragment.errorText.setText(st2);

					}
				}

			});
		}
	}

	/**
	 * MyGroupChangeListener
	 * 群变化的监听
	 */
	public class MyGroupChangeListener implements EMGroupChangeListener {

		@Override        /**    收到群邀请    */
		public void onInvitationReceived(String groupId, String groupName, String inviter, String reason) {
			
			boolean hasGroup = false;
			for (EMGroup group : EMGroupManager.getInstance().getAllGroups()) {
				if (group.getGroupId().equals(groupId)) {
					hasGroup = true;
					break;
				}
			}
			if (!hasGroup)
				return;

			// 被邀请
			String st3 = getResources().getString(R.string.Invite_you_to_join_a_group_chat);
			EMMessage msg = EMMessage.createReceiveMessage(Type.TXT);
			msg.setChatType(ChatType.GroupChat);
			msg.setFrom(inviter);
			msg.setTo(groupId);
			msg.setMsgId(UUID.randomUUID().toString());
			msg.addBody(new TextMessageBody(inviter + " " +st3));
			// 保存邀请消息
			EMChatManager.getInstance().saveMessage(msg);
			// 提醒新消息
			HXSDKHelper.getInstance().getNotifier().viberateAndPlayTone(msg);

			runOnUiThread(new Runnable() {
				public void run() {
					updateUnreadLabel();
					// 刷新ui
					if (currentTabIndex == 0)
						chatHistoryFragment.refresh();
					if (CommonUtils.getTopActivity(MainActivity.this).equals(GroupsActivity.class.getName())) {
						GroupsActivity.instance.onResume();
					}
				}
			});

		}

		@Override
		public void onInvitationAccpted(String groupId, String inviter, String reason) {
			
		}

		@Override
		public void onInvitationDeclined(String groupId, String invitee, String reason) {

		}


		@Override        /**    用户被踢    */
		public void onUserRemoved(String groupId, String groupName) {
						
			// 提示用户被T了，demo省略此步骤
			// 刷新ui
			runOnUiThread(new Runnable() {
				public void run() {
					try {
						updateUnreadLabel();
						if (currentTabIndex == 0)
							chatHistoryFragment.refresh();
						if (CommonUtils.getTopActivity(MainActivity.this).equals(GroupsActivity.class.getName())) {
							GroupsActivity.instance.onResume();
						}
					} catch (Exception e) {
						EMLog.e(TAG, "refresh exception " + e.getMessage());
					}
				}
			});
		}

		@Override    /**    群被解散    */
		public void onGroupDestroy(String groupId, String groupName) {
			
			// 群被解散
			// 提示用户群被解散,demo省略
			// 刷新ui
			runOnUiThread(new Runnable() {
				public void run() {
					updateUnreadLabel();
					if (currentTabIndex == 0)
						chatHistoryFragment.refresh();
					if (CommonUtils.getTopActivity(MainActivity.this).equals(GroupsActivity.class.getName())) {
						GroupsActivity.instance.onResume();
					}
				}
			});

		}

		@Override    /**   用户申请加入群     */
		public void onApplicationReceived(String groupId, String groupName, String applyer, String reason) {
			
			// 用户申请加入群聊
			InviteMessage msg = new InviteMessage();
			msg.setFrom(applyer);
			msg.setTime(System.currentTimeMillis());
			msg.setGroupId(groupId);
			msg.setGroupName(groupName);
			msg.setReason(reason);
			Log.d(TAG, applyer + " 申请加入群聊：" + groupName);
			msg.setStatus(InviteMesageStatus.BEAPPLYED);
			notifyNewIviteMessage(msg);
		}

		@Override      /**    加群申请被同意    */
		public void onApplicationAccept(String groupId, String groupName, String accepter) {

			String st4 = getResources().getString(R.string.Agreed_to_your_group_chat_application);
			// 加群申请被同意
			EMMessage msg = EMMessage.createReceiveMessage(Type.TXT);
			msg.setChatType(ChatType.GroupChat);
			msg.setFrom(accepter);
			msg.setTo(groupId);
			msg.setMsgId(UUID.randomUUID().toString());
			msg.addBody(new TextMessageBody(accepter + " " +st4));
			// 保存同意消息
			EMChatManager.getInstance().saveMessage(msg);
			// 提醒新消息
			HXSDKHelper.getInstance().getNotifier().viberateAndPlayTone(msg);

			runOnUiThread(new Runnable() {
				public void run() {
					updateUnreadLabel();
					// 刷新ui
					if (currentTabIndex == 0)
						chatHistoryFragment.refresh();
					if (CommonUtils.getTopActivity(MainActivity.this).equals(GroupsActivity.class.getName())) {
						GroupsActivity.instance.onResume();
					}
				}
			});
		}

		@Override
		public void onApplicationDeclined(String groupId, String groupName, String decliner, String reason) {
			// 加群申请被拒绝，demo未实现
		}
	}

	/**
	 * 保存提示新消息
	 * 
	 * @param msg
	 */
	private void notifyNewIviteMessage(InviteMessage msg) {      /**     当有新消息时，刷新bar   */
		saveInviteMsg(msg);
		// 提示有新消息
		HXSDKHelper.getInstance().getNotifier().viberateAndPlayTone(null);

		// 刷新bottom bar消息未读数
		updateUnreadAddressLable();
		// 刷新好友页面ui
		if (currentTabIndex == 1)
			contactListFragment.refresh();
	}

	/**
	 * 保存邀请等msg
	 * 
	 * @param msg
	 */
	private void saveInviteMsg(InviteMessage msg) {    /**     保存邀请信息   */
		// 保存msg
		inviteMessgeDao.saveMessage(msg);
		// 未读数加1
		User user = ((DemoHXSDKHelper)HXSDKHelper.getInstance()).getContactList().get(Constant.NEW_FRIENDS_USERNAME);
		if (user.getUnreadMsgCount() == 0)
			user.setUnreadMsgCount(user.getUnreadMsgCount() + 1);
	}

	/**
	 * set head
	 * 
	 * @param username
	 * @return
	 */
	User setUserHead(String username) {    /**      添加  username header(首字母) */
		User user = new User();
		user.setUsername(username);
		String headerName = null;
		if (!TextUtils.isEmpty(user.getNick())) {
			headerName = user.getNick();
		} else {
			headerName = user.getUsername();
		}
		if (username.equals(Constant.NEW_FRIENDS_USERNAME)) {
			user.setHeader("");
		} else if (Character.isDigit(headerName.charAt(0))) {
			user.setHeader("#");
		} else {
			user.setHeader(HanziToPinyin.getInstance().get(headerName.substring(0, 1)).get(0).target.substring(0, 1)
					.toUpperCase());
			char header = user.getHeader().toLowerCase().charAt(0);
			if (header < 'a' || header > 'z') {
				user.setHeader("#");
			}
		}
		return user;
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!isConflict && !isCurrentAccountRemoved) {
			updateUnreadLabel();
			updateUnreadAddressLable();
			EMChatManager.getInstance().activityResumed();
		}

		// unregister this event listener when this activity enters the
		// background
		DemoHXSDKHelper sdkHelper = (DemoHXSDKHelper) DemoHXSDKHelper.getInstance();
		sdkHelper.pushActivity(this);

		// register the event listener when enter the foreground
		EMChatManager.getInstance().registerEventListener(this,
				new EMNotifierEvent.Event[] { EMNotifierEvent.Event.EventNewMessage , EMNotifierEvent.Event.EventOfflineMessage, EMNotifierEvent.Event.EventConversationListChanged});
	}

	@Override
	protected void onStop() {
		EMChatManager.getInstance().unregisterEventListener(this);
		DemoHXSDKHelper sdkHelper = (DemoHXSDKHelper) DemoHXSDKHelper.getInstance();
		sdkHelper.popActivity(this);

		super.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("isConflict", isConflict);
		outState.putBoolean(Constant.ACCOUNT_REMOVED, isCurrentAccountRemoved);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			moveTaskToBack(false);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private android.app.AlertDialog.Builder conflictBuilder;
	private android.app.AlertDialog.Builder accountRemovedBuilder;
	private boolean isConflictDialogShow;
	private boolean isAccountRemovedDialogShow;
    private BroadcastReceiver internalDebugReceiver;

	/**
	 * 显示帐号在别处登录dialog
	 */
	private void showConflictDialog() {       /**    显示当前账号在异地登陆    */
		isConflictDialogShow = true;
		DemoHXSDKHelper.getInstance().logout(false,null);
		String st = getResources().getString(R.string.Logoff_notification);
		if (!MainActivity.this.isFinishing()) {
			// clear up global variables
			try {
				if (conflictBuilder == null)
					conflictBuilder = new android.app.AlertDialog.Builder(MainActivity.this);
				conflictBuilder.setTitle(st);
				conflictBuilder.setMessage(R.string.connect_conflict);
				conflictBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						conflictBuilder = null;
						finish();
						startActivity(new Intent(MainActivity.this, LoginActivity.class));
					}
				});
				conflictBuilder.setCancelable(false);
				conflictBuilder.create().show();
				isConflict = true;
			} catch (Exception e) {
				EMLog.e(TAG, "---------color conflictBuilder error" + e.getMessage());
			}

		}

	}

	/**
	 * 帐号被移除的dialog
	 */
	private void showAccountRemovedDialog() {    /**     显示账号被移除   */
		isAccountRemovedDialogShow = true;
		DemoHXSDKHelper.getInstance().logout(true,null);
		String st5 = getResources().getString(R.string.Remove_the_notification);
		if (!MainActivity.this.isFinishing()) {
			// clear up global variables
			try {
				if (accountRemovedBuilder == null)
					accountRemovedBuilder = new android.app.AlertDialog.Builder(MainActivity.this);
				accountRemovedBuilder.setTitle(st5);
				accountRemovedBuilder.setMessage(R.string.em_user_remove);
				accountRemovedBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						accountRemovedBuilder = null;
						finish();
						startActivity(new Intent(MainActivity.this, LoginActivity.class));
					}
				});
				accountRemovedBuilder.setCancelable(false);
				accountRemovedBuilder.create().show();
				isCurrentAccountRemoved = true;
			} catch (Exception e) {
				EMLog.e(TAG, "---------color userRemovedBuilder error" + e.getMessage());
			}

		}

	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		/**  如果    （出现矛盾   且  还没有 跳出Dialog  ）  则跳出来 Dialog     */
		if (getIntent().getBooleanExtra("conflict", false) && !isConflictDialogShow) {
			showConflictDialog();
			/**    如果   （被移除  且 还没有跳出Dialog  ）  则调出来 Dialog  */
		} else if (getIntent().getBooleanExtra(Constant.ACCOUNT_REMOVED, false) && !isAccountRemovedDialogShow) {
			showAccountRemovedDialog();
		}
	}
	
	/**
	 * 内部测试代码，开发者请忽略
	 */
	private void registerInternalDebugReceiver() {
	    internalDebugReceiver = new BroadcastReceiver() {
            
            @Override
            public void onReceive(Context context, Intent intent) {
                DemoHXSDKHelper.getInstance().logout(true,new EMCallBack() {
                    
                    @Override
                    public void onSuccess() {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                // 重新显示登陆页面
                                finish();
                                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                                
                            }
                        });
                    }
                    
                    @Override
                    public void onProgress(int progress, String status) {}
                    
                    @Override
                    public void onError(int code, String message) {}
                });
            }
        };
        IntentFilter filter = new IntentFilter(getPackageName() + ".em_internal_debug");
        registerReceiver(internalDebugReceiver, filter);
    }

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		//getMenuInflater().inflate(R.menu.context_tab_contact, menu);
	}
}
