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

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.easemob.EMCallBack;
import com.easemob.mychat.applib.controller.HXSDKHelper;
import com.easemob.chat.EMChatManager;
import com.easemob.chat.EMGroupManager;
import com.easemob.mychat.chatuidemo.Constant;
import com.easemob.mychat.chatuidemo.DemoApplication;
import com.easemob.mychat.chatuidemo.DemoHXSDKHelper;
import com.easemob.mychat.chatuidemo.R;
import com.easemob.mychat.chatuidemo.db.UserDao;
import com.easemob.mychat.chatuidemo.domain.User;
import com.easemob.mychat.chatuidemo.utils.CommonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * 登陆页面,登录并初始化联系人，数据用于下一界面 （MainActivity 显示
 * 
 */
public class LoginActivity extends BaseActivity {
	private static final String TAG = "LoginActivity";
	public static final int REQUEST_CODE_SETNICK = 1;
	private EditText usernameEditText;
	private EditText passwordEditText;

	private boolean progressShow;
	private boolean autoLogin = false;

	private String currentUsername;
	private String currentPassword;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// 如果用户名密码都有，直接进入主页面
		if (DemoHXSDKHelper.getInstance().isLogined()) {
			autoLogin = true;
			startActivity(new Intent(LoginActivity.this, MainActivity.class));  /**进入主界面*/
			return;
		}
		/**    否则留在登录界面  */
		setContentView(R.layout.activity_login);

		usernameEditText = (EditText) findViewById(R.id.username);
		passwordEditText = (EditText) findViewById(R.id.password);

		// 如果用户名改变，清空密码
		usernameEditText.addTextChangedListener(new TextWatcher() {
			@Override         /**    先输入用户名，在输入密码。之后再修改用户名的时候，密码会变空   */
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				passwordEditText.setText(null);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});
		/**   获取用户名   */
		if (DemoApplication.getInstance().getUserName() != null) {
			usernameEditText.setText(DemoApplication.getInstance().getUserName());
		}
	}

	/**
	 * 登录
	 * 
	 * @param view
	 */
	public void login(View view) {

		/**   如果没有网络，则提示用户无网络   */
		if (!CommonUtils.isNetWorkConnected(this)) {
			Toast.makeText(this, R.string.network_isnot_available, Toast.LENGTH_SHORT).show();
			return;
		}

		/**  获取用户名和密码    */
		currentUsername = usernameEditText.getText().toString().trim();
		currentPassword = passwordEditText.getText().toString().trim();

		/**   提示用户名不能为空   */
		if (TextUtils.isEmpty(currentUsername)) {
			Toast.makeText(this, R.string.User_name_cannot_be_empty, Toast.LENGTH_SHORT).show();
			return;
		}

		/**   提示密码不能为空   */
		if (TextUtils.isEmpty(currentPassword)) {
			Toast.makeText(this, R.string.Password_cannot_be_empty, Toast.LENGTH_SHORT).show();
			return;
		}


		/**    显示登录中的进度条  */
		progressShow = true;
		final ProgressDialog progressDialog = new ProgressDialog(LoginActivity.this);
		progressDialog.setCanceledOnTouchOutside(false);
		progressDialog.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface dialog) {
				progressShow = false;
			}
		});
		progressDialog.setMessage(getString(R.string.Is_landing));
		progressDialog.show();

		final long start = System.currentTimeMillis();
		/**   传入用户名和密码，进行登录   */
		// 调用sdk登陆方法登陆聊天服务器
		EMChatManager.getInstance().login(currentUsername, currentPassword, new EMCallBack() {

			@Override
			public void onSuccess() {        /**  登录成功回调函数    */
				if (!progressShow) {
					return;
				}
				// 登陆成功，保存用户名密码
				DemoApplication.getInstance().setUserName(currentUsername);
				DemoApplication.getInstance().setPassword(currentPassword);

				try {
					// ** 第一次登录或者之前logout后再登录，加载所有本地群和回话
					// ** manually load all local groups and
				    EMGroupManager.getInstance().loadAllGroups();      //加载所有本地的群组
					EMChatManager.getInstance().loadAllConversations();    // 加载所有回话
					// 处理好友和群组
					initializeContacts();       /**   初始化联系人列表   */
				} catch (Exception e) {
					e.printStackTrace();
					// 取好友或者群聊失败，不让进入主页面
					runOnUiThread(new Runnable() {
						public void run() {
							progressDialog.dismiss();
							DemoHXSDKHelper.getInstance().logout(true,null);         /**    退出登录  */
							Toast.makeText(getApplicationContext(), R.string
									.login_failure_failed, Toast.LENGTH_SHORT).show();
						}
					});
					return;
				}
				// 更新当前用户的nickname 此方法的作用是在ios离线推送时能够显示用户nick
				boolean updatenick = EMChatManager.getInstance().updateCurrentUserNick(
						DemoApplication.currentUserNick.trim());
				if (!updatenick) {
					Log.e("LoginActivity", "update current user nick fail");
				}
				if (!LoginActivity.this.isFinishing() && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				// 进入主页面
				Intent intent = new Intent(LoginActivity.this,
						MainActivity.class);        /**   MainActivity  是多个 Fragment 聊天的界面 */
				startActivity(intent);           /**   启动主界面   */
				
				finish();
			}

			@Override
			public void onProgress(int progress, String status) {
			}

			@Override
			public void onError(final int code, final String message) {
				if (!progressShow) {
					return;
				}
				runOnUiThread(new Runnable() {         /**    弹出登录失败   Toast  */
					public void run() {
						progressDialog.dismiss();
						Toast.makeText(getApplicationContext(), getString(R.string.Login_failed) + message,
								Toast.LENGTH_SHORT).show();
					}
				});
			}
		});
	}

	private void initializeContacts() {        /**    初始化联系人列表  */
		Map<String, User> userlist = new HashMap<String, User>();
		// 添加user"申请与通知"
		/**   加载 好友申请与通知 数据   */
		User newFriends = new User();
		newFriends.setUsername(Constant.NEW_FRIENDS_USERNAME);
		String strChat = getResources().getString(
				R.string.Application_and_notify);
		newFriends.setNick(strChat);
		userlist.put(Constant.NEW_FRIENDS_USERNAME, newFriends);
		// 添加"群聊"
		/**   加载群数据   */
		User groupUser = new User();
		String strGroup = getResources().getString(R.string.group_chat);
		groupUser.setUsername(Constant.GROUP_USERNAME);
		groupUser.setNick(strGroup);
		groupUser.setHeader("");
		userlist.put(Constant.GROUP_USERNAME, groupUser);
		
		// 添加"Robot"
		/**   加载机器人   */
		User robotUser = new User();
		String strRobot = getResources().getString(R.string.robot_chat);
		robotUser.setUsername(Constant.CHAT_ROBOT);
		robotUser.setNick(strRobot);
		robotUser.setHeader("");
		userlist.put(Constant.CHAT_ROBOT, robotUser);
		
		// 存入内存
		/**    userlist  存入内存  */
		((DemoHXSDKHelper)HXSDKHelper.getInstance()).setContactList(userlist);
		// 存入db
		/**  userList 存入 DB    */
		UserDao dao = new UserDao(LoginActivity.this);
		List<User> users = new ArrayList<User>(userlist.values());
		dao.saveContactList(users);
	}
	
	/**
	 * 注册
	 * 
	 * @param view
	 */
	public void register(View view) {
		startActivityForResult(new Intent(this, RegisterActivity.class), 0);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (autoLogin) {
			return;
		}
	}
}
