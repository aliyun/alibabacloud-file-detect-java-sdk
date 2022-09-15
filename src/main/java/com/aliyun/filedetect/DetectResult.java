package com.aliyun.filedetect;

public class DetectResult {
	
	// 检测是否成功完成，true: 可通过getDetectResultInfo查看结果; false: 可通过getErrorInfo获取错误信息
    public boolean isSucc() {
    	return this.error_code == ERR_CODE.ERR_SUCC;
    }

    //to developer:支持有null的语言，可以直接返回ErrorInfo对象信息，如果没有错误码，返回null
    //获取错误信息
    public ErrorInfo getErrorInfo() {
    	if (isSucc()) return null;
    	ErrorInfo info = new ErrorInfo();
    	info.md5 = this.md5;
    	info.time = this.time;
    	info.error_code = this.error_code;
    	info.error_string = this.error_string;
    	return info;
    }

    //to developer:支持有null的语言，可以直接返回DetectResultInfo对象信息，如果没有错误码，返回null
    //获取检测结果信息
    public DetectResultInfo getDetectResultInfo() {
    	if (!isSucc()) return null;
    	VirusInfo vinfo = null;
    	if (this.result == RESULT.RES_BLACK) {
    		vinfo = new VirusInfo();
    		vinfo.virus_type = this.virus_type;
    		vinfo.ext_info = this.ext_info;
    	}
    	DetectResultInfo info = new DetectResultInfo(vinfo);
    	info.md5 = this.md5;
    	info.time = this.time;
    	info.result = this.result;
    	info.score = this.score;
    	return info;
    }
	
	public static class ErrorInfo {
		public String md5 = null; // 样本md5
		public long time = 0; // 用时，单位为毫秒
		public ERR_CODE error_code = ERR_CODE.ERR_INIT; // 错误码
		
		// 扩展错误信息，如果error_code 为 ERR_CALL_API，此字段有效
	    // 此字段为json字符串，格式如下
	    // { "action":"xxx", "error_code":"yyy", "error_message":"zzz" }
	    // yyy为错误码，如 ServerError
	    // zzz为错误信息， 如：ServerError
	    // xxx为api的名字 如：CreateFileDetectUploadUrl
	    // 当网络出现问题时(未获取到服务应答)，返回 
	    // {"action":"xxx", "error_code":"NetworkError", "error_message":"zzz"}
		public String error_string = null; // 扩展错误信息
	}

	public static class VirusInfo {
		public String  virus_type = null; //病毒类型，如“黑客工具”
		public String  ext_info = null; //扩展信息为json字符串
	}

	public static enum RESULT {
		RES_WHITE, // 样本白
		RES_BLACK, // 样本黑
		RES_UNKNOWN // 未知样本
	}
	
	public static class DetectResultInfo {
		public String md5 = null; // 样本md5
		public long time = 0; // 用时，单位为毫秒
		public RESULT result = RESULT.RES_UNKNOWN; // 检测结果
		public int score = 0;				     // 分值，取值范围0-100
		//to developer:支持有null的语言，可以直接返回VirusInfo对象信息，如果没有错误码，返回null
	    //获取病毒信息,如result为RES_BLACK，可通过此接口获取病毒信息
		public VirusInfo getVirusInfo() {
			return virusinfo;
		}
		public DetectResultInfo(VirusInfo vinfo) {
			this.virusinfo = vinfo;
		}
		private VirusInfo virusinfo = null;
	}
	
	public String md5 = null; // 样本md5
	public long time = 0; // 用时，单位为毫秒
	
	public ERR_CODE error_code = ERR_CODE.ERR_INIT; // 错误码
	// 扩展错误信息，如果error_code 为 ERR_CALL_API，此字段有效
    // 此字段为json字符串，格式如下
    // { "action":"xxx", "error_code":"yyy", "error_message":"zzz" }
    // yyy为错误码，如: ServerError
    // zzz为错误信息， 如: ServerError
    // xxx为api的名字 如: CreateFileDetectUploadUrl
    // 当网络出现问题时(未获取到服务应答)，返回 
    // {"action":"xxx", "error_code":"NetworkError", "error_message":"zzz"} 
	public String error_string = null; // 扩展错误信息

	public RESULT result = RESULT.RES_UNKNOWN; // 检测结果
	public int score = 0;				     // 分值，取值范围0-100
	public String virus_type = null;	     // 病毒类型，如“黑客工具”
	public String ext_info = null;	         // 扩展信息为json字符串
}
