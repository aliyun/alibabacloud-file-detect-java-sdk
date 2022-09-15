package com.aliyun.filedetect;

class Config {
	public final static int THREAD_POOL_SIZE = 64; // 线程池大小
	public final static int QUEUE_SIZE_MAX = 200; // 队列最大个数
	public final static int QUERY_RESULT_INTERVAL = 100; // 查询检测结果间隔时间，单位为毫秒
	public final static int REQUEST_TOO_FREQUENTLY_SLEEP_TIME = 100; // 请求太过频繁时，需要休眠时间，单位为毫秒
	public final static int HTTP_CONNECT_TIMEOUT = 6000; // 网络连接超时时间，单位为毫秒
	public final static int HTTP_READ_TIMEOUT = 6000; // 网络连接超时时间，单位为毫秒
	public final static int HTTP_UPLOAD_TIMEOUT = 60000; // 上传文件超时时间，单位为毫秒
}
