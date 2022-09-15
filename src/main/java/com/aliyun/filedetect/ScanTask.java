package com.aliyun.filedetect;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import javax.activation.MimetypesFileTypeMap;
import org.apache.commons.codec.binary.Hex;

import com.aliyun.sas20181203.models.CreateFileDetectRequest;
import com.aliyun.sas20181203.models.CreateFileDetectUploadUrlRequest;
import com.aliyun.sas20181203.models.CreateFileDetectUploadUrlResponse;
import com.aliyun.sas20181203.models.GetFileDetectResultRequest;
import com.aliyun.sas20181203.models.GetFileDetectResultResponse;
import com.aliyun.sas20181203.models.GetFileDetectResultResponseBody.GetFileDetectResultResponseBodyResultList;
import com.aliyun.sas20181203.models.CreateFileDetectUploadUrlResponseBody.CreateFileDetectUploadUrlResponseBodyUploadUrlList;
import com.aliyun.sas20181203.models.CreateFileDetectUploadUrlResponseBody.CreateFileDetectUploadUrlResponseBodyUploadUrlListContext;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import com.google.gson.Gson;

class ScanTask implements Runnable {
	private int m_seq = 0;
	private String m_path = null;
	private long m_size = 0;
	private int m_timeout = 0;
	private IDetectResultCallback m_callback = null;
	private DetectResult m_result = new DetectResult();
	
	private long m_start_time = 0;
	private long m_last_time = 0;
	
	public static interface TaskCallback {
		public void onTaskEnd(ScanTask task);
		public void onTaskBegin(ScanTask task);
	}
	private TaskCallback m_taskCallback = null;

	public ScanTask(String file_path, long size, int timeout, IDetectResultCallback callback) {
		m_path = file_path;
		m_size = size;
		m_timeout = timeout;
		m_callback = callback;
		m_start_time = System.currentTimeMillis();
	}

	public void setSeq(int seq) {
		m_seq = seq;
	}

	public int getSeq() {
		return m_seq;
	}
	
	public void setTaskCallback(TaskCallback callback) {
		m_taskCallback = callback;
		if (null != m_taskCallback) {
			m_taskCallback.onTaskBegin(this);
		}
	}
	
	private static final int GET_RESULT_FAIL = 1000; // 获取结果失败，未找到文件推送记录或者检测结果已过期
	private static final int REQUEST_TOO_FREQUENTLY = 2000; // 请求太频繁，请稍后再试
	private static final int HAS_EXCEPTION = -1; // 存在异常
	private static final int IS_OK = 0;
	private static final int IS_BLACK = 1; // 可疑文件
	private static final int IS_DETECTING = 3; // 检测中，请等待

	public void run() {
		// 缓存对象
		OpenAPIDetector detector = OpenAPIDetector.getInstance();
		com.aliyun.sas20181203.Client client = detector.m_client;
		RuntimeOptions client_opt = detector.m_client_opt;
		LinkedBlockingDeque<Runnable> queue = detector.m_queue;
		if (!detector.m_is_inited || null == client || null == queue) {
			errorCallback(ERR_CODE.ERR_INIT, null);
			return;
		}
		// 判断是否已超时
		if (checkTimeout()) {
			return;
		}
		
		// 计算文件md5
		if (null == m_result.md5) {
			m_result.md5 = calcMd5(m_path);
			if (null == m_result.md5) {
				errorCallback(ERR_CODE.ERR_FILE_NOT_FOUND, null);
				return;
			}
		}
		
		// 如果距离上次查询过短，则需要等待一会
		if (System.currentTimeMillis() - m_last_time < Config.QUERY_RESULT_INTERVAL) {
			synchronized(queue) {
				try {
					queue.wait(Config.QUERY_RESULT_INTERVAL);
				} catch (InterruptedException e) {
				}
			}
			if (System.currentTimeMillis() - m_last_time < Config.QUERY_RESULT_INTERVAL) {
				// 时间不够就放到队列里去
				queue.addLast(this);
				return;
			}
		}
		
		// 更新时间戳
		m_last_time = System.currentTimeMillis();
		
		// 获取扫描结果
		ResultInfo resultinfo = null;
		while (true) {
			resultinfo = getResultByAPI(client, client_opt, m_result.md5);
			if (resultinfo.result != REQUEST_TOO_FREQUENTLY) {
				break;
			}
			needSleep(Config.REQUEST_TOO_FREQUENTLY_SLEEP_TIME); // 请求太过频繁，需要休眠
			// 判断是否已超时
			if (checkTimeout()) {
				return;
			}
		}
		
		switch(resultinfo.result) {
		case HAS_EXCEPTION:
			return; // 出错，退出
		case GET_RESULT_FAIL:
		{// 没有结果，则尝试上传文件
			int detect_ret = 0;
			while (true) {
				detect_ret = uploadAndDetectByAPI(client, client_opt, m_path, m_result.md5);
				if (detect_ret != REQUEST_TOO_FREQUENTLY) {
					break;
				}
				needSleep(Config.REQUEST_TOO_FREQUENTLY_SLEEP_TIME); // 请求太过频繁，需要休眠
				// 判断是否已超时
				if (checkTimeout()) {
					return;
				}
			}
			
			if (HAS_EXCEPTION == detect_ret) {
				return; // 出错，退出
			}
			queue.addLast(this); // 重新添加到队列，等待再次查询扫描结果
		} break;
		case IS_BLACK:
			okCallback(true, resultinfo); // 报黑
			break;
		case IS_DETECTING:
			queue.addLast(this); // 检测中，请等待
			break;
		default:
			okCallback(false, resultinfo); // 其他结果均为白
			break;
		}
	}

	public void errorCallback(ERR_CODE errCode, String errString) {
		m_result.error_code = errCode;
		m_result.error_string = errString;
		m_result.time = System.currentTimeMillis() - m_start_time;
		if (null != m_taskCallback) {
			m_taskCallback.onTaskEnd(this);
		}
		if (null != m_callback) {
			m_callback.onScanResult(m_seq, m_path, m_result);
		}
	}
	
	public void okCallback(boolean is_black, ResultInfo resultinfo) {
		m_result.error_code = ERR_CODE.ERR_SUCC;
		m_result.result = is_black ? DetectResult.RESULT.RES_BLACK : DetectResult.RESULT.RES_WHITE;
		m_result.time = System.currentTimeMillis() - m_start_time;
		m_result.score = resultinfo.score;
		m_result.virus_type = resultinfo.virus_type;
		m_result.ext_info = resultinfo.ext;
		if (null != m_taskCallback) {
			m_taskCallback.onTaskEnd(this);
		}
		if (null != m_callback) {
			m_callback.onScanResult(m_seq, m_path, m_result);
		}
	}
	
	private boolean checkTimeout() {
		long curr_time = System.currentTimeMillis();
		if (m_timeout >= 0) {
			if (curr_time - m_start_time > m_timeout) {
				if (null == m_result.md5) {
					errorCallback(ERR_CODE.ERR_TIMEOUT_QUEUE, null);
				} else {
					errorCallback(ERR_CODE.ERR_TIMEOUT, null);
				}
				return true;
			}
		}
		return false;
	}
	
	private void needSleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	private String calcMd5(String path) {
		FileInputStream fileInputStream = null;
		try {
			MessageDigest MD5 = MessageDigest.getInstance("MD5");
			fileInputStream = new FileInputStream(path);
			byte[] buffer = new byte[8192];
			int length;
			while ((length = fileInputStream.read(buffer)) != -1) {
				MD5.update(buffer, 0, length);
			}
			return new String(Hex.encodeHex(MD5.digest()));
		} catch (Exception e) {
			return null;
		} finally {
			try {
				if (fileInputStream != null) {
					fileInputStream.close();
				}
			} catch (IOException e) {
			}
		}
	}
	
	static class ResultInfo {
		public int result = 0;
		public int score = 0;
		public String virus_type = null;
		public String ext = null;
		public ResultInfo(int result) {
			this.result = result;
		}
		public ResultInfo(int result, int score, String virus_type, String ext) {
			this.result = result;
			this.score = score;
			this.virus_type = virus_type;
			this.ext = ext;
		}
	}
	
	private String getErrorMessage(String name, String code, String msg) {
		Map<String, String> map = new HashMap<>();
		map.put("action", name);
		map.put("error_code", code);
		map.put("error_message", msg);
		return new Gson().toJson(map);
	}
	
	private ResultInfo getResultByAPI(com.aliyun.sas20181203.Client client, RuntimeOptions client_opt, String md5) {
        String api_name = "GetFileDetectResult";
		try {
            List<String> hashKeyList = new ArrayList<>();
            hashKeyList.add(md5);
			GetFileDetectResultRequest request = new GetFileDetectResultRequest();
            request.setHashKeyList(hashKeyList);
            request.setType(0);
            GetFileDetectResultResponse response = client.getFileDetectResultWithOptions(request, client_opt);
            GetFileDetectResultResponseBodyResultList org_result = response.body.resultList.get(0);
            int result = 0;
            if (null != org_result.result) {
            	result = org_result.result;
            }
            int score = 0;
            if (null != org_result.score) {
            	score = org_result.score;
            }
            return new ResultInfo(result, score, org_result.virusType, org_result.ext);
        } catch (TeaException error) {
        	if ("GetResultFail".equals(error.code)) {
        		return new ResultInfo(GET_RESULT_FAIL); 
        	}
        	if ("RequestTooFrequently".equals(error.code)) {
        		return new ResultInfo(REQUEST_TOO_FREQUENTLY);
        	}

        	if ("Throttling.User".equals(error.code)) {
        		return new ResultInfo(REQUEST_TOO_FREQUENTLY);
        	}
        	errorCallback(ERR_CODE.ERR_CALL_API, getErrorMessage(api_name, error.code, error.message));
        	return new ResultInfo(HAS_EXCEPTION);
        } catch (Exception error) {
        	errorCallback(ERR_CODE.ERR_CALL_API, getErrorMessage(api_name, "ERR_NETWORK", error.getMessage()));
        	return new ResultInfo(HAS_EXCEPTION);
        }
	}
	
	private int uploadAndDetectByAPI(com.aliyun.sas20181203.Client client, RuntimeOptions client_opt, String path, String md5) {
		String api_name = "";
		ERR_CODE api_callerr = ERR_CODE.ERR_CALL_API;
		try {
        	CreateFileDetectUploadUrlResponseBodyUploadUrlList upload_url_response = null;
        	{
        		// 获取上传参数
        		api_name = "CreateFileDetectUploadUrl";
        		api_callerr = ERR_CODE.ERR_CALL_API;
        		
        		CreateFileDetectUploadUrlRequest.CreateFileDetectUploadUrlRequestHashKeyContextList hashKeyContextList0 = new CreateFileDetectUploadUrlRequest.CreateFileDetectUploadUrlRequestHashKeyContextList()
                        .setHashKey(md5)
                        .setFileSize((int)m_size);
                CreateFileDetectUploadUrlRequest request = new CreateFileDetectUploadUrlRequest()
                        .setHashKeyContextList(java.util.Arrays.asList(
                            hashKeyContextList0
                        ));
	            request.setType(0);
	            CreateFileDetectUploadUrlResponse response = client.createFileDetectUploadUrlWithOptions(request, client_opt);
	            upload_url_response = response.body.getUploadUrlList().get(0);
        	}
            if (!upload_url_response.fileExist) {
            	// 上传文件
            	api_name = "UploadFile";
            	api_callerr = ERR_CODE.ERR_UPLOAD;
            	uploadFile(path, upload_url_response.publicUrl, upload_url_response.context);
            }
            {
            	// 发起检测
            	api_name = "CreateFileDetect";
            	api_callerr = ERR_CODE.ERR_CALL_API;
            	CreateFileDetectRequest request = new CreateFileDetectRequest();
	            request.setHashKey(md5);
	            request.setOssKey(upload_url_response.context.ossKey);
	            request.setType(0);
	            client.createFileDetectWithOptions(request, client_opt);
            }
            
        } catch (TeaException error) {
        	if ("RequestTooFrequently".equals(error.code)) {
        		return REQUEST_TOO_FREQUENTLY;
        	}
        	if ("Throttling.User".equals(error.code)) {
        		return REQUEST_TOO_FREQUENTLY;
        	}
        	errorCallback(api_callerr, getErrorMessage(api_name, error.code, error.message));
        	return HAS_EXCEPTION;
        } catch (Exception error) {
        	errorCallback(api_callerr, getErrorMessage(api_name, "ERR_NETWORK", error.getMessage()));
        	return HAS_EXCEPTION;
        }
        return IS_OK;
	}
	
	private boolean uploadFile(String localFilePath, String urlStr, CreateFileDetectUploadUrlResponseBodyUploadUrlListContext context) throws IOException {
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("key", context.ossKey);
        formFields.put("OSSAccessKeyId", context.accessId);
        formFields.put("policy", context.policy);
        formFields.put("Signature", context.signature);
        
        HttpURLConnection conn = null;
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/90.0.4430.212 Safari/537.36";
        String boundary = "9431149156168";
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(Config.HTTP_CONNECT_TIMEOUT);
            conn.setReadTimeout(Config.HTTP_UPLOAD_TIMEOUT);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            OutputStream out = new DataOutputStream(conn.getOutputStream());
            // 遍历读取表单Map中的数据，将数据写入到输出流中。
            if (formFields != null) {
                StringBuffer strBuf = new StringBuffer();
                Iterator<Map.Entry<String, String>> iter = formFields.entrySet().iterator();
                int i = 0;
                while (iter.hasNext()) {
                    Map.Entry<String, String> entry = iter.next();
                    String inputName = entry.getKey();
                    String inputValue = entry.getValue();
                    if (inputValue == null) {
                        continue;
                    }
                    if (i == 0) {
                        strBuf.append("--").append(boundary).append("\r\n");
                        strBuf.append("Content-Disposition: form-data; name=\""
                                + inputName + "\"\r\n\r\n");
                        strBuf.append(inputValue);
                    } else {
                        strBuf.append("\r\n").append("--").append(boundary).append("\r\n");
                        strBuf.append("Content-Disposition: form-data; name=\""
                                + inputName + "\"\r\n\r\n");
                        strBuf.append(inputValue);
                    }
                    i++;
                }
                out.write(strBuf.toString().getBytes());
            }
            // 读取文件信息，将要上传的文件写入到输出流中。
            File file = new File(localFilePath);
            String filename = file.getName();
            String contentType = new MimetypesFileTypeMap().getContentType(file);
            if (contentType == null || contentType.equals("")) {
                contentType = "application/octet-stream";
            }
            StringBuffer strBuf = new StringBuffer();
            strBuf.append("\r\n").append("--").append(boundary)
                    .append("\r\n");
            strBuf.append("Content-Disposition: form-data; name=\"file\"; "
                    + "filename=\"" + filename + "\"\r\n");
            strBuf.append("Content-Type: " + contentType + "\r\n\r\n");
            out.write(strBuf.toString().getBytes());
            DataInputStream in = new DataInputStream(new FileInputStream(file));
            int bytes = 0;
            byte[] bufferOut = new byte[1024];
            while ((bytes = in.read(bufferOut)) != -1) {
                out.write(bufferOut, 0, bytes);
            }
            in.close();
            byte[] endData = ("\r\n--" + boundary + "--\r\n").getBytes();
            out.write(endData);
            out.flush();
            out.close();
            // 读取返回数据。
            strBuf = new StringBuffer();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = null;
            while ((line = reader.readLine()) != null) {
                strBuf.append(line).append("\n");
            }
            String res = strBuf.toString();
            reader.close();
            return true;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
	}
}
