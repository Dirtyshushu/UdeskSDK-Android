package cn.udesk.presenter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Message;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.util.Log;

import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.udesk.ChatMessageEvent;
import cn.udesk.JsonUtils;
import cn.udesk.UdeskConst;
import cn.udesk.UdeskSDKManager;
import cn.udesk.UdeskUtil;
import cn.udesk.activity.UdeskChatActivity.MessageWhat;
import cn.udesk.adapter.UDEmojiAdapter;
import cn.udesk.db.UdeskDBManager;
import cn.udesk.model.SurveyOptionsModel;
import cn.udesk.voice.AudioRecordState;
import cn.udesk.voice.AudioRecordingAacThread;
import cn.udesk.voice.VoiceRecord;
import cn.udesk.xmpp.UdeskMessageManager;
import udesk.core.UdeskCallBack;
import udesk.core.UdeskCoreConst;
import udesk.core.UdeskHttpFacade;
import udesk.core.model.AgentInfo;
import udesk.core.model.MessageInfo;
import udesk.core.utils.UdeskIdBuild;
import udesk.core.utils.UdeskUtils;
import udesk.core.xmpp.XmppInfo;

public class ChatActivityPresenter implements ChatMessageEvent {

	private IChatActivityView mChatView;
	private VoiceRecord mVoiceRecord = null;
	private String mRecordTmpFile = "";
	MyUpCompletionImgHandler mMyUpCompletionImgHandler = null;
	MyUpCompletionAudioHandler mMyUpCompletionAudioHandler = null;

	public ChatActivityPresenter(IChatActivityView chatview) {
		this.mChatView = chatview;
		UdeskMessageManager.getInstance().setChatMessageEvent(this);
	}

	/**
	 * 收到消息回执
	 */
	@Override
	public void onMessageReceived(String msgId) {
		if (mChatView.getHandler() != null) {
			Message message = mChatView.getHandler().obtainMessage(
					MessageWhat.changeImState);
			message.obj = msgId;
			message.arg1 = UdeskConst.SendFlag.RESULT_SUCCESS;
			mChatView.getHandler().sendMessage(message);
		}

	}

	/**
	 * 收到新消息
	 */
	@Override
	public void onNewMessage(MessageInfo msgInfo) {

		if (mChatView.getHandler() != null) {
			Message messge = mChatView.getHandler().obtainMessage(
					MessageWhat.onNewMessage);
			messge.obj = msgInfo;
			mChatView.getHandler().sendMessage(messge);
		}
	}
	

	/**
	 * 收到客服在线下线的通知
	 */
	@Override
	public void onPrenseMessage(String jid, int onlineFlag) {
		if (mChatView.getHandler() != null) {
			Message messge = mChatView.getHandler().obtainMessage(
					MessageWhat.status_notify);
			messge.arg1 = onlineFlag;
			messge.obj = jid;
			mChatView.getHandler().sendMessage(messge);
		}
		
	}
	
	@Override
	public void onReqsurveyMsg(boolean isSurvey) {

		if(isSurvey){
			getIMSurveyOptions();
		}
	
	}
	
	
	private void getIMSurveyOptions(){
		UdeskHttpFacade.getInstance().getIMSurveyOptions(
				UdeskSDKManager.getInstance().getDomain(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSecretKey(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSdkToken(mChatView.getContext()),  new UdeskCallBack() {
					
					@Override
					public void onSuccess(String message) {
						String SurveyMsg = message;
						SurveyOptionsModel model = JsonUtils.parseSurveyOptions(SurveyMsg);
						if (mChatView.getHandler() != null) {
								Message messge = mChatView.getHandler().obtainMessage(
								MessageWhat.surveyNotify);
								messge.obj = model;
								mChatView.getHandler().sendMessage(messge);
						}
					}
					
					@Override
					public void onFail(String message) {
						// TODO Auto-generated method stub
						
					}
				});
	}
	
	public void putIMSurveyResult(String optionId){
		
		UdeskHttpFacade.getInstance().putSurveyVote(
				UdeskSDKManager.getInstance().getDomain(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSecretKey(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSdkToken(mChatView.getContext()),
				mChatView.getAgentInfo().agent_id, 
				UdeskSDKManager.getInstance().getUserId(mChatView.getContext()),
				optionId, new UdeskCallBack() {
					
					@Override
					public void onSuccess(String message) {
						String SurveyMsg = message;
						
					}
					
					@Override
					public void onFail(String message) {
						// TODO Auto-generated method stub
						
					}
				});
	}
	

	/**
	 * 先查看下终端用户账号ID是否创建， 如果终端用户账号ID没有创建，则先创建。 如果创建则直接使用获取 连接openfire的信息
	 */

	public void getIMCustomerInfo() {
		String userId = UdeskSDKManager.getInstance().getUserId(mChatView.getContext());
		if (TextUtils.isEmpty(userId)) {
			getCustomerId();
		} else {
			getIMJson(userId);
		}
	}
	
	public void getCustomerId(){
		UdeskHttpFacade.getInstance().setUserInfo(UdeskSDKManager.getInstance().getDomain(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSecretKey(mChatView.getContext()),UdeskSDKManager.getInstance().getSdkToken(mChatView.getContext()),
				UdeskSDKManager.getInstance().getUserinfo(), UdeskSDKManager.getInstance().getTextField(),
				UdeskSDKManager.getInstance().getRoplist(), new UdeskCallBack() {
					
					@Override
					public void onSuccess(String string) {
						UdeskSDKManager.getInstance().parserCustomersJson(mChatView.getContext(),string);
						getIMCustomerInfo();
					}
					
					@Override
					public void onFail(String string) {
						mChatView.showFailToast(string);
					}
				});
	}
	
	public void getIMJson(String userId){
		UdeskHttpFacade.getInstance().getIMJsonAPi(UdeskSDKManager.getInstance().getDomain(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSecretKey(mChatView.getContext()),userId,
				new UdeskCallBack() {

					@Override
					public void onSuccess(String message) {
						// 成功之后，提交设备信息
						putDevices();
					}

					@Override
					public void onFail(String message) {
						// 失败给出错误提示 结束流程
						mChatView.showFailToast(message);
					}
				});
	}

	public void putDevices() {
		UdeskHttpFacade.getInstance().putDevicesJson(mChatView.getContext(),
				UdeskSDKManager.getInstance().getDomain(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSecretKey(mChatView.getContext()),
				UdeskSDKManager.getInstance().getUserId(mChatView.getContext()),
				new UdeskCallBack() {

					@Override
					public void onSuccess(String message) {
						// 成功之后 获取客服
						getAgentInfo();
					}

					@Override
					public void onFail(String message) {
						// 失败给出错误提示 结束流程
						mChatView.showFailToast(message);
					}
				});
	}

	public void getAgentInfo() {
		UdeskHttpFacade.getInstance().getAgentJsonAPi(
				UdeskSDKManager.getInstance().getDomain(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSecretKey(mChatView.getContext()),
				UdeskSDKManager.getInstance().getUserId(mChatView.getContext()),
				new UdeskCallBack() {

			@Override
			public void onSuccess(String message) {
				// 获取客户成功，显示在线客服的信息，连接xmpp，进行会话
				AgentInfo agentInfo = JsonUtils.parseAgentResult(message);
				mChatView.dealAgentInfo(agentInfo);

			}

			@Override
			public void onFail(String message) {
				// 失败给出错误提示 结束流程
				mChatView.showFailToast(message);
			}
		});

	}
	
	public void getRedirectAgentInfo(String agent_id,String group_id){
		UdeskHttpFacade.getInstance().getRedirectAgentInfo(
				UdeskSDKManager.getInstance().getDomain(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSecretKey(mChatView.getContext()),
				UdeskSDKManager.getInstance().getSdkToken(mChatView.getContext()),
				agent_id, group_id, 
				new UdeskCallBack() {
					
					@Override
					public void onSuccess(String message) {
						// 获取客户成功，显示在线客服的信息，连接xmpp，进行会话
						AgentInfo agentInfo = JsonUtils.parseAgentResult(message);
						mChatView.dealRedirectAgentInfo(agentInfo);
					}
					
					@Override
					public void onFail(String message) {
						// 失败给出错误提示 结束流程
						mChatView.showFailToast(message);
					}
				});
	}

	public void sendTxtMessage() {
		MessageInfo msg = buildSendMessage(
				UdeskConst.ChatMsgTypeString.TYPE_TEXT,
				System.currentTimeMillis(), mChatView.getInputContent()
						.toString(), "");
		saveMessage(msg);
		mChatView.clearInputContent();
		mChatView.addMessage(msg);
		UdeskMessageManager.getInstance().sendMessage(msg.getMsgtype(),
				msg.getMsgContent(), msg.getMsgId(),
				mChatView.getAgentInfo().agentJid, msg.getDuration());
		UdeskDBManager.getInstance().addSendingMsg(msg.getMsgId(),
				UdeskConst.SendFlag.RESULT_SEND, System.currentTimeMillis());
	}

	public void sendBitmapMessage(Bitmap bitmap) {
		if (bitmap == null) {
			return;
		}
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		int max = Math.max(width, height);

		BitmapFactory.Options factoryOptions = new BitmapFactory.Options();
		factoryOptions.inJustDecodeBounds = false;
		factoryOptions.inPurgeable = true;
		// 获取原图数据
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
		byte[] data = stream.toByteArray();

		String imageName = UdeskUtils.MD5(data);
		File scaleImageFile = UdeskUtil.getOutputMediaFile(imageName
				+ UdeskConst.ORIGINAL_SUFFIX);
		if (!scaleImageFile.exists()) {
			if (max > 1024) {
				factoryOptions.inSampleSize = max / 1024;
			} else {
				factoryOptions.inSampleSize = 1;
			}
			FileOutputStream fos;
			try {
				fos = new FileOutputStream(scaleImageFile);
				bitmap = BitmapFactory.decodeByteArray(data, 0, data.length,
						factoryOptions);
				bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
				fos.close();
				fos = null;
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		if (bitmap != null) {
			bitmap.recycle();
			bitmap = null;
		}
		data = null;
		if (TextUtils.isEmpty(scaleImageFile.getPath())) {
			UdeskUtils.showToast(mChatView.getContext(), "上传图片失败，请重试");
			return;
		}
		MessageInfo msg = buildSendMessage(
				UdeskConst.ChatMsgTypeString.TYPE_IMAGE,
				System.currentTimeMillis(), "", scaleImageFile.getPath());
		saveMessage(msg);
		mChatView.addMessage(msg);
		upLoadImageFile(msg.getLocalPath(), msg);
	}

	public void sendBitmapMessage(String photoPath) {
		if (TextUtils.isEmpty(photoPath)) {
			UdeskUtils.showToast(mChatView.getContext(), "上传图片失败，请重试");
			return;
		}
		// showTime(System.currentTimeMillis());
		MessageInfo msg = buildSendMessage(
				UdeskConst.ChatMsgTypeString.TYPE_IMAGE,
				System.currentTimeMillis(), "", photoPath);

		saveMessage(msg);
		mChatView.addMessage(msg);
		upLoadImageFile(photoPath, msg);
	}

	private void upLoadImageFile(String filePath, MessageInfo message) {
		com.qiniu.android.storage.UploadManager uploadManager = new com.qiniu.android.storage.UploadManager();
		if (mMyUpCompletionImgHandler == null) {
			mMyUpCompletionImgHandler = new MyUpCompletionImgHandler();
		}
		String md5 = UdeskUtils.getMd5ByFile(new File(filePath));
		mMyUpCompletionImgHandler.putCacheMessage(md5, message);
		uploadManager.put(filePath, md5,
				XmppInfo.getInstance().getQiniuToken(),
				mMyUpCompletionImgHandler,
				new com.qiniu.android.storage.UploadOptions(null, null, false,
						mUpProgressHandler, null));
	}

	/**
	 * 七牛上传进度
	 */
	private com.qiniu.android.storage.UpProgressHandler mUpProgressHandler = new com.qiniu.android.storage.UpProgressHandler() {
		public void progress(String key, double percent) {
		}
	};

	public MessageInfo buildSendMessage(String msgType, long time, String text,
			String location) {
		MessageInfo msg = new MessageInfo();
		msg.setMsgtype(msgType);
		msg.setTime(time);
		msg.setMsgId(UdeskIdBuild.buildMsgId());
		msg.setDirection(UdeskConst.ChatMsgDirection.Send);
		msg.setSendFlag(UdeskConst.SendFlag.RESULT_SEND);
		msg.setReadFlag(UdeskConst.ChatMsgReadFlag.read);
		msg.setMsgContent(text);
		msg.setPlayflag(UdeskConst.PlayFlag.NOPLAY);
		msg.setLocalPath(location);
		msg.setDuration(0);
		return msg;
	}

	public void saveMessage(MessageInfo msg) {
		UdeskDBManager.getInstance().addMessageInfo(msg);
	}

	/**
	 * 表情28个,最后一个标签显示删除了，只显示了27个
	 * 
	 * @param id
	 * @param emojiCount
	 * @param emojiString
	 */
	public void clickEmoji(long id, int emojiCount, String emojiString) {
		if (id == (emojiCount - 1)) {
			String str = mChatView.getInputContent().toString();
			CharSequence text = mChatView.getInputContent();
			int selectionEnd = Selection.getSelectionEnd(text);
			String string = str.substring(0, selectionEnd);
			if (string.length() > 0) {

				String deleteLastEmotion = deleteLastEmotion(string);
				if (deleteLastEmotion.length() > 0) {

					mChatView.refreshInputEmjio(deleteLastEmotion
							+ str.substring(selectionEnd));
				} else {
					mChatView.refreshInputEmjio(""
							+ str.substring(selectionEnd));
				}
				CharSequence c = mChatView.getInputContent();
				if (c instanceof Spannable) {
					Spannable spanText = (Spannable) c;
					Selection
							.setSelection(spanText, deleteLastEmotion.length());
				}
			}
		} else {
			CharSequence text = mChatView.getInputContent();
			int selectionEnd = Selection.getSelectionEnd(text);
			String editString = text.toString().substring(0, selectionEnd)
					+ emojiString + text.toString().substring(selectionEnd);
			mChatView.refreshInputEmjio(editString);
			CharSequence c = mChatView.getInputContent();
			if (c instanceof Spannable) {
				Spannable spanText = (Spannable) c;
				Selection.setSelection(spanText,
						selectionEnd + emojiString.length());
			}
		}
	}

	private String deleteLastEmotion(String str) {
		if (TextUtils.isEmpty(str)) {
			return "";
		}
		List<String> emotionList = mChatView.getEmotionStringList();
		int lastIndexOf = str.lastIndexOf(UDEmojiAdapter.EMOJI_PREFIX);
		if (lastIndexOf > -1) {
			String substring = str.substring(lastIndexOf);
			boolean contains = emotionList.contains(substring);
			if (contains) {
				return str.substring(0, lastIndexOf);
			}
		}
		return str.substring(0, str.length() - 1);
	}

	/**
	 * 七牛图片上传完成
	 *
	 */
	class MyUpCompletionImgHandler implements UpCompletionHandler {

		private Map<String, MessageInfo> mToMsgMap = new HashMap<String, MessageInfo>();

		public MyUpCompletionImgHandler() {

		}

		public void putCacheMessage(String md5, MessageInfo message) {
			mToMsgMap.put(md5, message);
		}

		@Override
		public void complete(String key, ResponseInfo info, JSONObject response) {

			MessageInfo msg = mToMsgMap.get(key);
			if (key != null && null != response && response.has("key")
					&& msg != null) {
				if (UdeskCoreConst.isDebug) {
					Log.i("DialogActivityPresenter", "UpCompletion : key="
							+ key + "\ninfo=" + info.toString() + "\nresponse="
							+ response.toString());
				}
				String qiniuKey = response.optString("key");
				String qiniuUrl = UdeskCoreConst.UD_QINIU_UPLOAD + qiniuKey;
				UdeskMessageManager.getInstance().sendMessage(msg.getMsgtype(),
						qiniuUrl, msg.getMsgId(),
						mChatView.getAgentInfo().agentJid, 0);
				UdeskDBManager.getInstance().updateMsgContent(msg.getMsgId(),
						qiniuUrl);

				UdeskDBManager.getInstance().addSendingMsg(msg.getMsgId(),
						UdeskConst.SendFlag.RESULT_SEND,
						System.currentTimeMillis());
				mToMsgMap.remove(key);
			} else {
				if (mChatView.getHandler() != null) {
					Message message = mChatView.getHandler().obtainMessage(
							MessageWhat.changeImState);
					message.obj = msg.getMsgId();
					message.arg1 = UdeskConst.SendFlag.RESULT_FAIL;
					mChatView.getHandler().sendMessage(message);
				}
				UdeskDBManager.getInstance().updateMsgSendFlag(msg.getMsgId(),
						UdeskConst.SendFlag.RESULT_FAIL);
			}
		}
	}

	// 开始录音
	public void recordStart() {
		// 录音这块功能，我们分离为UI和实际功能.
		// UdeskPopVoiceWindow负责界面。AudioRecordingAacThread负责具体录音。RecordTouchListener则负责手势判断
		// 在此之前，请确保SD卡是可以使用的
		// 后台录音开始
		mChatView.showmVoicePopWindow();
		mVoiceRecord = new AudioRecordingAacThread();// new
		mRecordTmpFile = UdeskUtil.getOutputAudioPath();
		mVoiceRecord.initResource(mRecordTmpFile, new AudioRecordState() {
			@Override
			public void onRecordingError() {
				// mdailogView.onRecordingError();
				if (mChatView.getHandler() != null) {
					mChatView.getHandler().sendEmptyMessage(
							MessageWhat.RECORD_ERROR);
				}
			}

			@Override
			public void onRecordSuccess(final String resultFilePath,
					long duration) {
				mChatView.onRecordSuccess(resultFilePath, duration);
			}

			@Override
			public void onRecordSaveError() {
			}

			@Override
			public void onRecordTooShort() {
				if (mChatView.getHandler() != null) {
					mChatView.getHandler().sendEmptyMessage(
							MessageWhat.RECORD_Too_Short);
				}
			}

			@Override
			public void onRecordCancel() {
				if (mChatView.getHandler() != null) {
					mChatView.getHandler().sendEmptyMessage(
							MessageWhat.RECORD_CANCEL);
				}
			}

			@Override
			public void updateRecordState(int micAmplitude) {

				if (mChatView.getHandler() != null) {
					Message message = mChatView.getHandler().obtainMessage(
							MessageWhat.UPDATE_VOCIE_STATUS);
					message.arg1 = micAmplitude;
					mChatView.getHandler().sendMessage(message);
				}
			}

			@Override
			public void onRecordllegal() {
				// 停止录音，提示开取录音权限
				if (mChatView.getHandler() != null) {
					mChatView.getHandler().sendEmptyMessage(
							MessageWhat.recordllegal);
				}

			}
		});
		mVoiceRecord.startRecord();
	}

	public void doRecordStop(boolean isCancel) {
		// 结束后台录音功能
		if (mVoiceRecord != null) {
			if (isCancel) {
				mVoiceRecord.cancelRecord();

			} else {
				mVoiceRecord.stopRecord();
			}
			mVoiceRecord = null;
		}

		// 不再监听Record的移动事件
		mChatView.setRecordBackgroundNullTouchListener();
	}

	// 发送录音信息
	public void sendRecordAudioMsg(String audiopath, long duration) {
		MessageInfo msg = buildSendMessage(
				UdeskConst.ChatMsgTypeString.TYPE_AUDIO,
				System.currentTimeMillis(), "", audiopath);
		duration = duration / 1000 + 1;
		msg.setDuration(duration);
		saveMessage(msg);
		mChatView.addMessage(msg);
		upLoadVodieFile(audiopath, msg);
	}

	private void upLoadVodieFile(String filePath, MessageInfo message) {
		com.qiniu.android.storage.UploadManager uploadManager = new com.qiniu.android.storage.UploadManager();
		if (mMyUpCompletionAudioHandler == null) {
			mMyUpCompletionAudioHandler = new MyUpCompletionAudioHandler();
		}
		String key = filePath.substring(filePath.lastIndexOf("/") + 1)
				+ UdeskConst.AUDIO_SUF;
		mMyUpCompletionAudioHandler.putCacheMessage(key, message);
		uploadManager.put(filePath, key,
				XmppInfo.getInstance().getQiniuToken(),
				mMyUpCompletionAudioHandler,
				new com.qiniu.android.storage.UploadOptions(null, null, false,
						mUpProgressHandler, null));
	}

	/**
	 * 七牛语音上传完成
	 *
	 */
	class MyUpCompletionAudioHandler implements UpCompletionHandler {

		private Map<String, MessageInfo> mToMsgMap = new HashMap<String, MessageInfo>();

		public MyUpCompletionAudioHandler() {

		}

		public void putCacheMessage(String key, MessageInfo message) {
			mToMsgMap.put(key, message);
		}

		@Override
		public void complete(String key, ResponseInfo info, JSONObject response) {

			MessageInfo msg = mToMsgMap.get(key);
			if (key != null && null != response && response.has("key")
					&& msg != null) {
				if (UdeskCoreConst.isDebug) {
					Log.w("DialogActivityPresenter", "UpCompletion : key="
							+ key + "\ninfo=" + info.toString() + "\nresponse="
							+ response.toString());
				}
				String qiniuKey = response.optString("key");
				String qiniuUrl = UdeskCoreConst.UD_QINIU_UPLOAD + qiniuKey;

				UdeskMessageManager.getInstance().sendMessage(msg.getMsgtype(),
						qiniuUrl, msg.getMsgId(),
						mChatView.getAgentInfo().agentJid, msg.getDuration());

				UdeskDBManager.getInstance().updateMsgContent(msg.getMsgId(),
						qiniuUrl);

				UdeskDBManager.getInstance().addSendingMsg(msg.getMsgId(),
						UdeskConst.SendFlag.RESULT_SEND,
						System.currentTimeMillis());
				mToMsgMap.remove(key);
			} else {
				if (mChatView.getHandler() != null) {
					Message message = mChatView.getHandler().obtainMessage(
							MessageWhat.changeImState);
					message.obj = msg.getMsgId();
					message.arg1 = UdeskConst.SendFlag.RESULT_FAIL;
					mChatView.getHandler().sendMessage(message);
				}
				UdeskDBManager.getInstance().updateMsgSendFlag(msg.getMsgId(),
						UdeskConst.SendFlag.RESULT_FAIL);
			}
		}

	}

	public void startRetryMsg(MessageInfo message) {
		if (message.getMsgtype() == UdeskConst.ChatMsgTypeString.TYPE_TEXT) {
			UdeskMessageManager.getInstance().sendMessage(message.getMsgtype(),
					message.getMsgContent(), message.getMsgId(),
					mChatView.getAgentInfo().agentJid, message.getDuration());
			UdeskDBManager.getInstance()
					.addSendingMsg(message.getMsgId(),
							UdeskConst.SendFlag.RESULT_SEND,
							System.currentTimeMillis());
		} else if (message.getMsgtype() == UdeskConst.ChatMsgTypeString.TYPE_IMAGE) {
			upLoadImageFile(message.getLocalPath(), message);
		} else if (message.getMsgtype() == UdeskConst.ChatMsgTypeString.TYPE_AUDIO) {
			upLoadVodieFile(message.getLocalPath(), message);
		}
		return;
	}

	public void SelfretrySendMsg() {
		if (mChatView.getHandler() != null) {
			mChatView.getHandler().postDelayed(runnable, 5000);
		}
	}

	Runnable runnable = new Runnable() {
		@Override
		public void run() {
			if (mChatView.getHandler() != null) {
				updateSendFailedFlag();
				retrySendMsg();
				mChatView.getHandler().postDelayed(this, 5000);
			}
		}
	};

	private void retrySendMsg() {
		if (!UdeskUtils.isNetworkConnected(mChatView.getContext())) {

			return;
		}

		List<String> retryMsgIds = UdeskDBManager.getInstance()
				.getNeedRetryMsg(System.currentTimeMillis());
		if (retryMsgIds == null || retryMsgIds.isEmpty()) {
			return;
		}
		if (retryMsgIds != null) {
			for (String msgID : retryMsgIds) {
				MessageInfo msg = UdeskDBManager.getInstance().getMessage(msgID);
				UdeskMessageManager.getInstance().sendMessage(msg.getMsgtype(), msg.getMsgContent(),
						msg.getMsgId(), mChatView.getAgentInfo().agentJid, msg.getDuration());

			}
		}
	}
	
	private void updateSendFailedFlag() {
		if (!UdeskUtils.isNetworkConnected(mChatView.getContext())) {

			return;
		}
		List<String> msgIds = UdeskDBManager.getInstance()
				.getNeedUpdateFailedMsg(System.currentTimeMillis());
		if (msgIds == null || msgIds.isEmpty()) {
			return;
		}
		for (String msgId : msgIds) {
			if (mChatView.getHandler() != null) {
				Message message = mChatView.getHandler().obtainMessage(
						MessageWhat.changeImState);
				message.obj = msgId;
				message.arg1 = UdeskConst.SendFlag.RESULT_FAIL;
				mChatView.getHandler().sendMessage(message);
			}
			UdeskDBManager.getInstance().deleteSendingMsg(msgId);
			UdeskDBManager.getInstance().updateMsgSendFlag(msgId,
					UdeskConst.SendFlag.RESULT_FAIL);
		}

	}



}