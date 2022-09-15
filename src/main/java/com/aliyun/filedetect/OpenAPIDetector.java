package com.aliyun.filedetect;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import com.aliyun.teautil.models.RuntimeOptions;

public class OpenAPIDetector implements ScanTask.TaskCallback {
	/**
	 * 获取单例检测器
	 * 
	 * @return 单例对象
	 */
	public static OpenAPIDetector getInstance() {
		if (null != m_instance)
			return m_instance;
		synchronized (OpenAPIDetector.class) {
			if (null != m_instance)
				return m_instance;
			m_instance = new OpenAPIDetector();
		}
		return m_instance;
	}

	/**
	 * 检测器初始化
	 * 
	 * @param accessKeyId
	 * @param accessKeySecret
	 * @return
	 * @throws Exception
	 */
	public ERR_CODE init(String accessKeyId, String accessKeySecret) throws Exception {
		if (m_is_inited) {
			return ERR_CODE.ERR_INIT;
		}
		
		com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
		config.endpoint = "tds.aliyuncs.com";
		m_client = new com.aliyun.sas20181203.Client(config);
		m_client_opt = new RuntimeOptions();
		m_client_opt.connectTimeout = Config.HTTP_CONNECT_TIMEOUT;
		m_client_opt.readTimeout = Config.HTTP_READ_TIMEOUT;

		m_rej_handler = new RejectedExecutionHandler() {
			@Override
			public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
				if (r instanceof ScanTask) {
					((ScanTask) r).errorCallback(ERR_CODE.ERR_ABORT, null);
				}
			}
		};
		m_queue = new LinkedBlockingDeque<Runnable>();
		m_threadpool = new ThreadPoolExecutor(Config.THREAD_POOL_SIZE, Config.THREAD_POOL_SIZE, 0, TimeUnit.MILLISECONDS, m_queue);
		m_threadpool.prestartAllCoreThreads();
		m_threadpool.setRejectedExecutionHandler(m_rej_handler);
		
		m_counter = 0;
		m_alive_task_num = 0;
		m_is_inited = true;
		return ERR_CODE.ERR_SUCC;
	}

	/**
	 * 检测器反初始化
	 * 
	 * @throws InterruptedException
	 */
	public void uninit() throws InterruptedException {
		if (!m_is_inited) {
			return;
		}
		m_is_inited = false;
		List<Runnable> tasks = m_threadpool.shutdownNow();
		for (Runnable r : tasks) {
			m_rej_handler.rejectedExecution(r, m_threadpool);
		}
		synchronized(m_queue) {
			m_queue.notifyAll();
		}
		m_threadpool.awaitTermination(5, TimeUnit.SECONDS);
		

		synchronized (this) {
			m_threadpool = null;
			m_rej_handler = null;
			m_queue = null;
			m_client = null;
			m_client_opt = null;
		}
	}

	/**
	 * 同步文件检测
	 * 
	 * @param file_path 待检测文件路径
	 * @param timeout   超时时长，单位毫秒， < 0 无限等待
	 * @param res       检测结果
	 * @throws InterruptedException
	 */
	public DetectResult detectSync(String file_path, int timeout) throws InterruptedException {
		final DetectResult res[] = new DetectResult[1];
		int seq = detect(file_path, timeout, new IDetectResultCallback() {
			public void onScanResult(int seq, String file_path, DetectResult callback_res) {
				res[0] = callback_res;
				synchronized(res) {
					res.notify();
				}
			}
		});
		
		if (seq > 0) {
			synchronized(res) {
				try {
					res.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		return res[0];
	}

	/**
	 * 异步文件检测
	 * 
	 * @param file_path 待检测文件路径
	 * @param timeout   超时时长，单位毫秒， < 0 无限等待
	 * @param callback  检测结果
	 * @return >0 发起检测成功，检测请求序列号 < 0 错误码，参见ERR_CODE
	 */
	public int detect(String file_path, int timeout, IDetectResultCallback callback) {
		long filesize = get_filesize(file_path);
		ScanTask task = new ScanTask(file_path, filesize, timeout, callback);
		if (filesize < 0) {
			task.errorCallback(ERR_CODE.ERR_FILE_NOT_FOUND, null);
			return ERR_CODE.ERR_FILE_NOT_FOUND.value();
		}
		int seq = 0;
		try {
			if (m_is_inited) {
				synchronized (this) {
					if (m_is_inited) {
						task.setSeq(++m_counter);
						if (m_counter <= 0) { // 超过2G，从头开始
							m_counter = 0;
							task.setSeq(++m_counter);
						}
						if (this.getQueueSize() >= Config.QUEUE_SIZE_MAX) {
							throw new IllegalStateException("Deque full");
						}
						task.setTaskCallback(this);
						m_queue.addLast(task);
						synchronized(m_queue) {
							m_queue.notify();
						}
						seq = task.getSeq();
					}
				}
			}
		} catch (IllegalStateException e) {
			task.errorCallback(ERR_CODE.ERR_DETECT_QUEUE_FULL, null);
			return ERR_CODE.ERR_DETECT_QUEUE_FULL.value();
		}

		if (seq <= 0) {
			task.errorCallback(ERR_CODE.ERR_INIT, null);
			return ERR_CODE.ERR_INIT.value();
		}
		return seq;
	}
	
    /** 
    * @brief 获取检测队列长度
    * @return 检测队列长度
    */
    public int getQueueSize() {
    	if (m_is_inited) {
			synchronized (this) {
				if (m_is_inited) {
					return m_alive_task_num;
				}
			}
		}
    	return 0;
    }

    /** 
    * @brief 等待队列空间可用（可进行新样本插入）
    * @param timeout 超时时长，单位毫秒， < 0 无限等待
    * @return ERR_SUCC 成功，队列已有可用空间 ERR_TIMEOUT 失败，队列仍然满
     * @throws InterruptedException 
    */
    public ERR_CODE waitQueueAvailable(int timeout) throws InterruptedException {
    	ERR_CODE code = ERR_CODE.ERR_TIMEOUT;
    	int all_time = 0;
    	do {
    		if (this.getQueueSize() < Config.QUEUE_SIZE_MAX) {
				code = ERR_CODE.ERR_SUCC;
				break;
			}
    		
    		if (timeout >= 0 && all_time >= timeout) {
    			break;
    		}
    		int sleep_unit = 200;
    		if (timeout >= 0 && timeout > all_time  && timeout - all_time  < sleep_unit) {
    			sleep_unit = timeout - all_time;
    		}
    		long start_time = System.currentTimeMillis();
			Thread.sleep(sleep_unit);
			all_time += System.currentTimeMillis() - start_time;
    	} while(true);
    	return code;
    }
    
    /** 
    * @brief 等待队列为空
    * @param timeout 超时时长，单位毫秒， < 0 无限等待
    * @return ERR_SUCC 成功，队列已空，所有检测工作完成 ERR_TIMEOUT 失败，队列中仍然有检测任务
     * @throws InterruptedException 
    */
    public ERR_CODE waitQueueEmpty(int timeout) throws InterruptedException {
    	ERR_CODE code = ERR_CODE.ERR_TIMEOUT;
    	int all_time = 0;
    	do {
    		if (this.getQueueSize() == 0) {
				code = ERR_CODE.ERR_SUCC;
				break;
			}
    		
    		if (timeout >= 0 && all_time >= timeout) {
    			break;
    		}
    		int sleep_unit = 200;
    		if (timeout >= 0 && timeout > all_time  && timeout - all_time  < sleep_unit) {
    			sleep_unit = timeout - all_time;
    		}
    		long start_time = System.currentTimeMillis();
			Thread.sleep(sleep_unit);
			all_time += System.currentTimeMillis() - start_time;
    	} while(true);
    	return code;
    }

	private static OpenAPIDetector m_instance = null;
	private int m_counter = 0;
	private ThreadPoolExecutor m_threadpool = null;
	private RejectedExecutionHandler m_rej_handler = null;

	volatile boolean m_is_inited = false;
	com.aliyun.sas20181203.Client m_client = null;
	RuntimeOptions m_client_opt = null;
	LinkedBlockingDeque<Runnable> m_queue = null;
	
	private OpenAPIDetector() {

	}
	
	private long get_filesize(String path) {
		try {
			File file = new File(path);
			return file.length();
		} catch(Exception e) {
		}
		return -1;
	}

	private int m_alive_task_num = 0;

	@Override
	public void onTaskEnd(ScanTask task) {
		if (m_is_inited) {
			synchronized (this) {
				if (m_is_inited) {
					m_alive_task_num --;
				}
			}
		}
	}

	@Override
	public void onTaskBegin(ScanTask task) {
		if (m_is_inited) {
			synchronized (this) {
				if (m_is_inited) {
					m_alive_task_num ++;
				}
			}
		}
	}
}
