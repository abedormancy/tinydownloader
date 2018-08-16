package ga.uuid.app;

import static ga.uuid.app.Const.*;

import ga.uuid.app.TinyDownloader.State;

public class DownloadTask implements Runnable {
	
	private String url; // 下载地址
	private String path; // 本地目录
	private String filename; // 文件名
	
	private int filesize; // 文件字节大小
	private transient int receivedSize = 0; // 已经接收的总字节数
	private transient int receivedSizeTemp = 0; // 上次接收总字节数
	private transient boolean started = false; // 任务状态 -> false:未开始; true:下载中; 
	private transient int retryCount = 0; // 当前重试次数
	
	private transient long beginStamp = 0L; // 开始下载时间戳
	private transient long endStamp = 0L; // 结束时间戳
	
	private transient int speed = 0; // 实时下载速度 字节/秒
	private transient long lastStamp = 0L; // 上次接收时间
	
	public DownloadTask(String url, String path) {
		requireNonBlank(url, "下载地址不能为空");
		requireNonBlank(path, "文件保存路径不能为空");
		this.url = url;
		path = path.replace('\\', '/');
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		this.path = path;
	}
	
	public DownloadTask(String url, String path, String filename) {
		this(url, path);
		this.filename = filename;
	}
	
	@Override
	public void run() {
		if (started) return;
		started = true;
		
		TinyDownloader.downloading_add(this);
		State state = State.FAIL;
		beginStamp = now();
		lastStamp = beginStamp;
		for (;state == State.FAIL && retryCount <= Const.RETRY_COUNT; retryCount++) {
			state = TinyDownloader.download(this);
		}
		if (state == State.SUCCESS) {
			endStamp = now();
		} else endStamp = 0L;
		TinyDownloader.downloading_remove(this);
		// 统计
		TinyDownloader.statistcsCount(state);
	}
	
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public int getFilesize() {
		return filesize;
	}
	public void setFilesize(int filesize) {
		this.filesize = filesize;
	}
	public int getReceivedSize() {
		return receivedSize;
	}
	public void setReceivedSize(int receivedSize) {
		long elapsed = now() - this.lastStamp;
		// 超过指定时间才进行计算
		if (elapsed >= 256) {
			// 使用 endStamp 间隔来计算当前下载速度
			setSpeed(receivedSize - receivedSizeTemp, elapsed);
			// 重置上次记录时间
			this.lastStamp = now();
		}
		this.receivedSize = receivedSize;
	}
	public boolean isStarted() {
		return started;
	}
	public void setStarted(boolean started) {
		this.started = started;
	}
	public int getRetryCount() {
		return retryCount;
	}
	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}
	
	/**
	 * 下载所用时间，单位 毫秒
	 * @return
	 */
	public long downloadUsedMillis() {
		long used = endStamp - beginStamp;
		return used > 0 ? used : 0;
	}
	
	/**
	 * 计算实时下载速度 单位  字节/秒
	 * @param size 本次接收的字节数
	 * @param elapsed 消耗的时间
	 */
	private void setSpeed(int size, long elapsed) {
		if (size < 0) size = receivedSize;
		speed = (int) (size / (elapsed / 1000f));
		receivedSizeTemp = receivedSize;
	}
	
	/**
	 * 返回实时下载速度
	 * @return
	 */
	public int getSpeed() {
		if (now() - lastStamp > 2048) {
			speed = 0; 
		}
		return speed;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		DownloadTask that = (DownloadTask) obj;
		return this.url.equals(that.url);
	}
	
	public static DownloadTask empty() {
		return empty;
	}
	
	private static final DownloadTask empty = new DownloadTask(".", ".");
	
	@Override
	public String toString() {
		return ">>" + filename + " ["+Const.prettySize(receivedSize)+"/"+Const.prettySize(filesize)+"] speed: " + Const.speed(speed) ;
	}
}