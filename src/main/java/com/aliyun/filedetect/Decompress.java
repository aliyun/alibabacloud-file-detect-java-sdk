package com.aliyun.filedetect;

// 解压缩参数
class Decompress {
	private boolean m_open = false;  // 是否识别压缩文件并解压，默认为false
	private int m_maxlayer =  5; // 最大解压层数，m_open参数为true时生效
	private int m_maxfilecount = 1000; // 最大解压文件数，m_open参数为true时生效
	
	public Decompress(boolean open, int maxlayer, int maxfilecount) {
		m_open = open;
		m_maxlayer = maxlayer;
		m_maxfilecount = maxfilecount;
	}
	
	public boolean isOpen() {
		return m_open;
	}
	
	public int getMaxLayer() {
		return m_maxlayer;
	}
	public int getMaxFileCount() {
		return m_maxfilecount;
	}

}
