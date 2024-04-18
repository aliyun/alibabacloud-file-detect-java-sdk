package com.aliyun.filedetect.sample;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.aliyun.filedetect.*;

public class Sample {

	/**
	 * 同步检测文件接口
	 * @param detector 检测器对象
	 * @param path 待检测的文件路径
	 * @param timeout_ms 设置超时时间，单位为毫秒
	 * @param wait_if_queuefull 如果检测队列满了，false表示不等待直接返回错误，true表示一直等待直到队列不满时
	 * @throws InterruptedException 
	 */
	public static DetectResult detectFileSync(OpenAPIDetector detector, String path, int timeout_ms, boolean wait_if_queuefull) throws InterruptedException {
		if (null == detector || null == path) return null;
		DetectResult result = null;
		while(true) {
			result = detector.detectSync(path, timeout_ms);
			if (null == result) break;
			if (result.error_code != ERR_CODE.ERR_DETECT_QUEUE_FULL) break;
			if (!wait_if_queuefull) break;
			detector.waitQueueAvailable(-1);
		}
		return result;
	}
	
	/**
	 * 异步检测文件接口
	 * @param detector 检测器对象
	 * @param path 待检测的文件路径
	 * @param timeout_ms 设置超时时间，单位为毫秒
	 * @param wait_if_queuefull 如果检测队列满了，false表示不等待直接返回错误，true表示一直等待直到队列不满时
	 * @param callback 结果回调函数
	 * @throws InterruptedException 
	 */
	public static int detectFile(OpenAPIDetector detector, String path, int timeout_ms, boolean wait_if_queuefull, IDetectResultCallback callback) throws InterruptedException {
		if (null == detector || null == path || null == callback) return ERR_CODE.ERR_INIT.value();
		int result = ERR_CODE.ERR_INIT.value();
		if (wait_if_queuefull) {
			final IDetectResultCallback real_callback = callback;
			callback = new IDetectResultCallback() {
				public void onScanResult(int seq, String file_path, DetectResult callback_res) {
					if (callback_res.error_code == ERR_CODE.ERR_DETECT_QUEUE_FULL) return;
					real_callback.onScanResult(seq, file_path, callback_res);
				}
			};
		}
		while(true) {
			result = detector.detect(path, timeout_ms, callback);
			if (result != ERR_CODE.ERR_DETECT_QUEUE_FULL.value()) break;
			if (!wait_if_queuefull) break;
			detector.waitQueueAvailable(-1);
		}
		return result;
	}
	
	/**
	 * 同步检测URL文件接口
	 * @param detector 检测器对象
	 * @param url 待检测的文件URL
	 * @param md5 待检测的文件md5
	 * @param timeout_ms 设置超时时间，单位为毫秒
	 * @param wait_if_queuefull 如果检测队列满了，false表示不等待直接返回错误，true表示一直等待直到队列不满时
	 * @throws InterruptedException 
	 */
	public static DetectResult detectUrlSync(OpenAPIDetector detector, String url, String md5, int timeout_ms, boolean wait_if_queuefull) throws InterruptedException {
		if (null == detector || null == url || null == md5) return null;
		DetectResult result = null;
		while(true) {
			result = detector.detectUrlSync(url, md5, timeout_ms);
			if (null == result) break;
			if (result.error_code != ERR_CODE.ERR_DETECT_QUEUE_FULL) break;
			if (!wait_if_queuefull) break;
			detector.waitQueueAvailable(-1);
		}
		return result;
	}
	
	/**
	 * 异步检测URL文件接口
	 * @param detector 检测器对象
	 * @param url 待检测的文件URL
	 * @param md5 待检测的文件md5
	 * @param timeout_ms 设置超时时间，单位为毫秒
	 * @param wait_if_queuefull 如果检测队列满了，false表示不等待直接返回错误，true表示一直等待直到队列不满时
	 * @param callback 结果回调函数
	 * @throws InterruptedException 
	 */
	public static int detectUrl(OpenAPIDetector detector, String url, String md5, int timeout_ms, boolean wait_if_queuefull, IDetectResultCallback callback) throws InterruptedException {
		if (null == detector || null == url || null == md5 || null == callback) return ERR_CODE.ERR_INIT.value();
		int result = ERR_CODE.ERR_INIT.value();
		if (wait_if_queuefull) {
			final IDetectResultCallback real_callback = callback;
			callback = new IDetectResultCallback() {
				public void onScanResult(int seq, String file_path, DetectResult callback_res) {
					if (callback_res.error_code == ERR_CODE.ERR_DETECT_QUEUE_FULL) return;
					real_callback.onScanResult(seq, file_path, callback_res);
				}
			};
		}
		while(true) {
			result = detector.detectUrl(url, md5, timeout_ms, callback);
			if (result != ERR_CODE.ERR_DETECT_QUEUE_FULL.value()) break;
			if (!wait_if_queuefull) break;
			detector.waitQueueAvailable(-1);
		}
		return result;
	}
	
	/**
	 * 格式化检测结果
	 * @param result 检测结果对象
	 * @return 格式化后的字符串
	 */
	public static String formatDetectResult(DetectResult result) {
		if (result.isSucc()) {
			DetectResult.DetectResultInfo info = result.getDetectResultInfo();
			String msg = String.format("[DETECT RESULT] [SUCCEED] %s", formatDetectResultInfo(info));
			if (info.compresslist != null) {
				int idx = 1;
				for (DetectResult.CompressFileDetectResultInfo comp_res : info.compresslist) {
					msg += String.format("\n\t\t\t [COMPRESS FILE] [IDX:%d] %s", idx++, formatCompressFileDetectResultInfo(comp_res));
				}
			}
			return msg;
		} 
		DetectResult.ErrorInfo info = result.getErrorInfo();
		return String.format("[DETECT RESULT] [FAIL] md5: %s, time: %d, error_code: %s, error_message: %s"
				, info.md5, info.time, info.error_code.name(), info.error_string);
	}
	
	private static String formatDetectResultInfo(DetectResult.DetectResultInfo info) {
		String msg = String.format("MD5: %s, TIME: %d, RESULT: %s, SCORE: %d", info.md5, info.time, info.result.name(), info.score);
		if (info.compresslist != null) {
			msg += String.format(", COMPRESS_FILES: %d", info.compresslist.size());
		}
		DetectResult.VirusInfo vinfo = info.getVirusInfo();
		if (vinfo != null) {
			msg += String.format(", VIRUS_TYPE: %s, EXT_INFO: %s", vinfo.virus_type, vinfo.ext_info);
		}
		return msg;
	}
	private static String formatCompressFileDetectResultInfo(DetectResult.CompressFileDetectResultInfo info) {
		String msg = String.format("PATH: %s, \t\t RESULT: %s, SCORE: %d", info.path, info.result.name(), info.score);
		DetectResult.VirusInfo vinfo = info.getVirusInfo();
		if (vinfo != null) {
			msg += String.format(", VIRUS_TYPE: %s, EXT_INFO: %s", vinfo.virus_type, vinfo.ext_info);
		}
		return msg;
	}
	
	/**
	 * 同步检测目录或文件
	 * @param path 指定路径，可以是文件或者目录。目录的话就会递归遍历
	 * @param is_sync 是否使用同步接口，推荐使用异步。 true是同步， false是异步
	 * @throws InterruptedException 
	 */
	public static void detectDirOrFileSync(OpenAPIDetector detector, String path, int timeout_ms, Map<String, DetectResult> result_map) throws InterruptedException {
		File file = new File(path);
		String abs_path = file.getAbsolutePath();
		if (file.isDirectory()) {
			String[] ss = file.list();
	        if (ss == null) return;
	        for (String s : ss) {
	        	String subpath = abs_path + File.separator + s;
	        	detectDirOrFileSync(detector, subpath, timeout_ms, result_map);
	        }
			return;
		}

    	System.out.println(String.format("[detectFileSync] [BEGIN] queueSize: %d, path: %s, timeout: %d", detector.getQueueSize(), abs_path, timeout_ms));
		DetectResult res = detectFileSync(detector, abs_path, timeout_ms, true);
    	System.err.println(String.format("                 [ END ] %s", formatDetectResult(res)));
		result_map.put(abs_path, res);
	}
	
	/**
	 * 异步检测目录或文件
	 * @param path 指定路径，可以是文件或者目录。目录的话就会递归遍历
	 * @param is_sync 是否使用同步接口，推荐使用异步。 true是同步， false是异步
	 * @throws InterruptedException 
	 */
	public static void detectDirOrFile(OpenAPIDetector detector, String path, int timeout_ms, IDetectResultCallback callback) throws InterruptedException {
		File file = new File(path);
		String abs_path = file.getAbsolutePath();
		if (file.isDirectory()) {
			String[] ss = file.list();
	        if (ss == null) return;
	        for (String s : ss) {
	        	String subpath = abs_path + File.separator + s;
	        	detectDirOrFile(detector, subpath, timeout_ms, callback);
	        }
	        return;
		}

    	
		int seq = detectFile(detector, abs_path, timeout_ms, true, callback);
		System.out.println(String.format("[detectFile] [BEGIN] seq: %d, queueSize: %d, path: %s, timeout: %d", seq, detector.getQueueSize(), abs_path, timeout_ms));
	}
	
	/**
	 * 开始对文件或目录进行
	 * @param path 指定路径，可以是文件或者目录。目录的话就会递归遍历
	 * @param is_sync 是否使用同步接口，推荐使用异步。 true是同步， false是异步
	 * @throws InterruptedException 
	 */
	public static void scan(final OpenAPIDetector detector, String path, int detect_timeout_ms, boolean is_sync) throws InterruptedException {
		System.out.println(String.format("[SCAN] [START] path: %s, detect_timeout_ms: %d, is_sync: %b", path, detect_timeout_ms, is_sync));
		long start_time = System.currentTimeMillis();
		final Map<String, DetectResult> result_map = new HashMap<>();
		if (is_sync) {
			detectDirOrFileSync(detector, path, detect_timeout_ms, result_map);
		} else {
			detectDirOrFile(detector, path, detect_timeout_ms, new IDetectResultCallback() {
				public void onScanResult(int seq, String file_path, DetectResult callback_res) {
			    	System.err.println(String.format("[detectFile] [ END ] seq: %d, queueSize: %d, %s", seq, detector.getQueueSize(), formatDetectResult(callback_res)));
					result_map.put(file_path, callback_res);
				}
			});
			// 等待任务执行完成
			detector.waitQueueEmpty(-1);
		}
		long used_time = System.currentTimeMillis() - start_time;
		System.out.println(String.format("[SCAN] [ END ] used_time: %d, files: %d", used_time, result_map.size()));
		
		int fail_count = 0;
		int white_count = 0;
		int black_count = 0;
		for (Map.Entry<String, DetectResult> entry : result_map.entrySet()) {
			DetectResult res = entry.getValue();
			if (res.isSucc()) {
				if (res.getDetectResultInfo().result == DetectResult.RESULT.RES_BLACK) {
					black_count ++;
				} else {
					white_count ++;
				}
			} else {
				fail_count ++;
			}
		}
		System.out.println(String.format("             fail_count: %d, white_count: %d, black_count: %d"
				, fail_count, white_count, black_count));
	}
	
    public static void main(String[] args_) throws Exception {
    	// 获取检测器实例
    	OpenAPIDetector detector = OpenAPIDetector.getInstance();
    	
    	// 初始化
    	ERR_CODE init_ret = detector.init("<AccessKey ID>", "<AccessKey Secret>");
    	System.out.println("INIT RET: " + init_ret.name());
    	
    	// 设置解压缩参数(可选，默认不解压压缩包)
    	boolean decompress = true; // 是否识别压缩文件并解压，默认为false
    	int decompressMaxLayer = 5; // 最大解压层数，decompress参数为true时生效
    	int decompressMaxFileCount = 1000; // 最大解压文件数，decompress参数为true时生效
    	ERR_CODE initdec_ret = detector.initDecompress(decompress, decompressMaxLayer, decompressMaxFileCount);
    	System.out.println("INIT_DECOMPRESS RET: " + initdec_ret.name());
    	
    	if (true) {
    		// 示例用法1：扫描本地目录或文件
    		boolean is_sync_scan = false; // 是异步检测还是同步检测。异步检测性能更好。false表示异步检测
        	int timeout_ms = 500000;  // 单个样本检测时间，单位为毫秒
        	String path = "test2.php"; // 待扫描的文件或目录
        	// 启动扫描，直到扫描结束
        	scan(detector, path, timeout_ms, is_sync_scan);
    	}
    	
    	if (true) {
    		// 示例用法2：扫描URL文件
        	int timeout_ms = 500000;  // 单个样本检测时间，单位为毫秒
        	String url = "https://xxxxxxxx.oss-cn-hangzhou-1.aliyuncs.com/xxxxx/xxxxxxxxxxxxxx?Expires=1671448125&OSSAccessKeyId=xxx"; // 待扫描的URL文件
        	String md5 = "a767ffc59d93125c7505b6e21d000000"; // 待扫描的文件MD5
        	// 同步扫描。如果需要异步扫描，调用detectUrl接口
        	System.out.println(String.format("[detectUrlSync] [BEGIN] URL: %s, MD5: %s, TIMEOUT: %d", url, md5, timeout_ms));
        	DetectResult result = detectUrlSync(detector, url, md5, timeout_ms, true);
        	System.err.println(String.format("[detectUrlSync] [ END ] %s", formatDetectResult(result)));
    	}
    	
    	
		// 反初始化
		System.out.println("Over.");
    	detector.uninit();
    }
}
