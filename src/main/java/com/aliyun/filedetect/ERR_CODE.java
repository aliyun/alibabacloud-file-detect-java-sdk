package com.aliyun.filedetect;

public enum ERR_CODE {
	ERR_INIT(-100), // 需要初始化，或者重复初始化
	ERR_FILE_NOT_FOUND(-99), // 文件未找到
	ERR_DETECT_QUEUE_FULL(-98), // 检测队列满
	ERR_CALL_API(-97), // 调用API错误
	ERR_TIMEOUT(-96), // 超时
    ERR_UPLOAD(-95), //文件上传失败；用户可重新发起检测，再次尝试
    ERR_ABORT(-94), //程序退出，样本未得到检测
    ERR_TIMEOUT_QUEUE(-93), //队列超时，用户发起检测频率过高或超时时间过短
	ERR_SUCC(0); // 成功

	private ERR_CODE(int value) {
		this.m_value = value;
	}

	public static ERR_CODE valueOf(int value) {
		switch (value) {
		case -100:
			return ERR_INIT;
		case -99:
			return ERR_FILE_NOT_FOUND;
		case -98:
			return ERR_DETECT_QUEUE_FULL;
		case -97:
			return ERR_CALL_API;
		case -96:
			return ERR_TIMEOUT;
		case -95:
			return ERR_UPLOAD;
		case -94:
			return ERR_ABORT;
		case -93:
			return ERR_TIMEOUT_QUEUE;
		case 0:
			return ERR_SUCC;
		default:
			return null;
		}
	}

	public int value() {
		return this.m_value;
	}

	private int m_value = 0;
}
