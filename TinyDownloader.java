package ga.uuid.app;

import static ga.uuid.app.Const.*;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


public class TinyDownloader {
	
	// 固定线程池，线程池大小即为同时下载任务数 
	private static final ExecutorService pool = Executors.newFixedThreadPool(THREAD_SIZE);
	// 等待添加的任务队列
	private static final BlockingDeque<Runnable> taskQueue = new LinkedBlockingDeque<>();
	// 正在下载的任务
	private static final FixedList<DownloadTask> downloadingList = new FixedList<>(THREAD_SIZE);
	
	// 统计 [LongAdder 替代 AtomicLong ]
	private static LongAdder successCount = new LongAdder();
	private static LongAdder failCount = new LongAdder();
	private static LongAdder skipCount = new LongAdder();
	private static final Map<State, LongAdder> counterMap = new HashMap<>();
	
	// 类加载后即为下载开启状态
	private static transient boolean start = true;
	
	// 实时下载总字节数统计，为了性能不使用同步，允许些许误差 （统计可能比实际小）
	private static transient int allBytes = 0;
	private static transient int bytesPerSecond = 0;
	
	// 同源任务下载检测 (不打算对其进行同步，不打算释放其内容) [key: host, value: visittime]
//	private static transient Map<String, Long> sameOrigin = new ConcurrentHashMap<>();
	private static transient Map<String, Long> sameOrigin = new HashMap<>();
	
	private static transient LongAdder currentSerial = new LongAdder(); 
	
	private static TrustManager[] tm = { new TrustAnyTrustManager() };
	private static SSLContext sc;
	private static TrustAnyHostnameVerifier tv = new TrustAnyHostnameVerifier();
	
	private static Map<String, String> requestHeaders = new HashMap<>();
	
	static {
		
		try {
			sc = SSLContext.getInstance("SSL", "SunJSSE");
			sc.init(null, tm, new java.security.SecureRandom());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// 统计映射
		counterMap.put(State.SUCCESS, successCount);
		counterMap.put(State.FAIL, failCount);
		counterMap.put(State.EXISTED, skipCount);
		
		// 下载任务队列监控线程
		new Thread(() -> {
			while (start) {
				try {
					Runnable task = taskQueue.take();
					pool.execute(task);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (!pool.isShutdown()) pool.shutdown();
		}, "monitor thread").start();
		
		// 统计线程
		new Thread(() -> {
			Consumer<StringBuilder> consumer = null;
			if (OUTPUT_MODE == 1) {
				consumer = s -> {
					// 附加实时下载任务数据
					s.append("\n").append(renderTable(downloadingList));
					// 输出实时下载任务面板
					Console.print(s.toString());
				};
			} else consumer = System.out::println;
			
			// 默认console恢复标识
			boolean recoveredConsole = false;
			ThreadPoolExecutor _pool = (ThreadPoolExecutor) pool;
			// 统计总览
			while (!pool.isTerminated()) {
				delay(OUTPUT_INTERVAL);
				int activeCount = _pool.getActiveCount();
				// 当前有下载任务才进行输出
				if (activeCount > 0) {
					recoveredConsole = false;
					StringBuilder sb = statisticsInfo();
					long pending = _pool.getTaskCount() - _pool.getCompletedTaskCount();
					if (!start) pending -= 1;
					sb.append("  pending: ").append(pending);
					output(sb, consumer);
				} else {
					if (!recoveredConsole) {
						// 向默认console输出统计信息
						System.out.println(statisticsInfo());
						Console.recover();
					}
					recoveredConsole = true;
				}
			}
		}, "statistics_thread").start();
		
		// 下载总速度监控线程
		new Thread(() -> {
			while (!pool.isTerminated()) {
				delay(1000);
				bytesPerSecond = allBytes;
				allBytes = 0;
			}
		}, "network_monitor_thread").start();
	}
	
	/**
	 * 确认下载任务，确认后不能再通过add方法添加下载任务
	 * 在确认没有新的下载任务时，显示调用此方法来保证程序能正常退出
	 */
	public static void ensure() {
		if (start) {
			// 如果下载任务队列中不为空的话，等待下载队列消耗完毕后才设置状态
			while (!taskQueue.isEmpty()) {
				delay(100);
			}
			start = false;
			taskQueue.add(() -> {});
		}
	}
	
	/**
	 * 添加下载任务
	 * @param url 远程url地址
	 * @param path 保存的目录
	 */
	public static void add(String url, String path) {
		add(url, path, null);
	}
	
	/**
	 * 添加下载任务
	 * @param url 远程url地址
	 * @param path 保存的目录
	 * @param filename 保存的文件名
	 * @return
	 */
	public static void add(String url, String path, String filename) {
		if (start) {
			DownloadTask task = new DownloadTask(url, path, filename);
			// 如果文件名为空 ，自动为其增加下载序列前缀
			if (isEmpty(filename)) {
				task.setSerial(currentSerial.intValue());
				currentSerial.increment();
			}
			taskQueue.add(task);
		} else {
			// TODO else 任务队列已关闭
			System.out.println("the pool already closed.");
		}
	}
	
	/**
	 * 以阻塞方式直接下载文件
	 * @param url 远程url地址
	 * @param path 保存的目录
	 * @return
	 */
	public static State download(String url, String path) {
		return download(url, path, null);
	}
	
	/**
	 * 以阻塞方式直接下载文件
	 * @param url 远程url地址
	 * @param path 保存的目录
	 * @param filename 保存的文件名
	 * @return
	 */
	public static State download(String url, String path, String filename) {
		System.out.println("downloading " + url + " --> " + path);
		State state = download(new DownloadTask(url, path, filename));
		System.out.println(state);
		return state;
	}
	
	/**
	 * 下载文件简单实现 ，buff中直接保存文件所有字节，下载完后写入磁盘
	 * 不支持断点续传，主要用于爬取的图片等小文件下载，不推荐大文件下载
	 * @param task
	 * @return
	 */
	static State download(DownloadTask task) {
		Objects.requireNonNull(task);
		
		URL url = null;
		URLConnection conn = null;
		String destFile = "";
		final boolean isHttps = task.getUrl().toLowerCase().startsWith("https://");
		
		try {
			url = new URL(task.getUrl());
			// origin checked. (with delay)
			verifyOrigin(url.getHost());
			
			if (isHttps) {
				conn = (HttpsURLConnection) url.openConnection();
				((HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
				((HttpsURLConnection) conn).setHostnameVerifier(tv);
			} else {
				conn = (HttpURLConnection) url.openConnection();
			}
			conn.setConnectTimeout(DOWNLOAD_TIMEOUT);  
			conn.setReadTimeout(DOWNLOAD_TIMEOUT);
			conn.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36" + Math.random());
			
			// 设置用户自定义的 request header
			if (requestHeaders.size() > 0) {
				Set<Entry<String, String>> es = requestHeaders.entrySet();
				Iterator<Entry<String, String>> iterator = es.iterator();
				while (iterator.hasNext()) {
					Entry<String, String> next = iterator.next();
					conn.setRequestProperty(next.getKey(), next.getValue());
				}
//				requestHeaders.forEach((key, value) -> {
//					conn.setRequestProperty(key, value);
//				});
			}
			
			// 下载任务中的文件名未指定的话，那么通过 header 获取，如果 header 中未获取成功那么通过 url 截取
			if (isEmpty(task.getFilename())) {
				Map<String, List<String>> headers = conn.getHeaderFields();
				Optional<String> filenameOpt = parseFilename(headers);
				String filename = filenameOpt.orElseGet(() -> {
					String _url = task.getUrl();
					return _url.substring(_url.lastIndexOf("/")+1, _url.length());
				});
				// TODO 返回的文件没有扩展名需要通过Content-Type进行识别处理
				task.setFilename(filename);
			}
			// 文件名处理
			task.setFilename(rep2empty(task.getFilename()));
			
			// 下载目录如果不存在，那么创建该目录
			Path directory = Paths.get(task.getPath());
			if (Files.notExists(directory)) {
				synchronized (TinyDownloader.class) {
					if (Files.notExists(directory)) Files.createDirectories(directory);
				}
			}
			
			destFile = String.join("/", task.getPath(), task.getFilenameWithSerial());
			// 检查当前文件是否存在，如果存在那么跳过
			Path path = Paths.get(destFile);
			if (Files.exists(path)) {
				if (Files.size(path) > 0) return State.EXISTED;
			}
		} catch (IOException e) {
			if (e.getClass() == FileSystemException.class) {
				throw new RuntimeException(e);
			}
			// TODO
			e.printStackTrace();
		}
		
		// 下载文件的简单实现
		try (InputStream in = conn.getInputStream()) {
			task.setFilesize(conn.getContentLength());
			if (task.getFilesize() < 1) {
				// TODO
				return State.FAIL;
			}
			byte[] binary = new byte[task.getFilesize()];
			int buf = 8192 << 1, size = binary.length;
			int index = 0;
			while (index < size) {
				int len = in.read(binary,  index, size - index > buf ? buf : size - index);
				index += len;
				task.setReceivedSize(index);
				allBytes += len;
			}
			writeFile(binary, destFile);
		} catch (IOException e) {
			// TODO
			e.printStackTrace();
			return State.FAIL;
		}
		return State.SUCCESS;
	}
	
	private static void writeFile(byte[] bytes, String file) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file);
				BufferedOutputStream bos = new BufferedOutputStream(fos)) {
			bos.write(bytes);
		}
	}
	
	/**
	 * 同源任务检查，mapper 中存在那么给予 100 ms 延迟下载，避免 503
	 * @param host
	 */
	private static void verifyOrigin(String host) {
		synchronized (host.intern()) {
			long now = now();
			Long last = sameOrigin.get(host);
			if (last == null) {
				sameOrigin.put(host, now + SAME_ORIGIN_DELAY);
				return;
			}
			Optional.of(last)
					.filter(l -> l > now)
					.ifPresent(l -> {
						sameOrigin.put(host, l + SAME_ORIGIN_DELAY);
						delay((int) (l - now));
//						System.out.println(System.currentTimeMillis() + " : " + host + " -> " + (l - now));
					});
		}
	}

	/**
	 * 通过headers解析出文件名
	 * @param map the headers map.
	 * @return
	 */
	private static final Optional<String> parseFilename(Map<String, List<String>> map) {
		String filename = null;
		try {
			String disposition = map.get("Content-Disposition").get(0);
			Pattern pattern = Pattern.compile("(filename\\*?)=([^;]+)");
			Matcher matcher = pattern.matcher(disposition);
			while (matcher.find()) {
				filename = matcher.group(2);
				if ("filename*".equals(matcher.group(1))) {
					break;
				}
			}
		} catch (NullPointerException e) {}
		if (isEmpty(filename)) filename = null; 
		return Optional.ofNullable(filename);
	}
	
	/**
	 * 加入到正在下载列表
	 * @param task
	 * @return
	 */
	synchronized static boolean downloading_add(DownloadTask task) {
		return downloadingList.add(task);
	}
	
	/**
	 * 将该任务移除正在下载列表
	 * @param task
	 * @return
	 */
	synchronized static boolean downloading_remove(DownloadTask task) {
		return downloadingList.remove(task);
	}
	
	/**
	 * 统计下载成功、失败、跳过等数量
	 * @param state
	 */
	static void statistcsCount(State state) {
		counterMap.get(state).increment();
	}
	
	/**
	 * 统计信息
	 * @return
	 */
	private static StringBuilder statisticsInfo() {
		StringBuilder sb = new StringBuilder("\n speed: ");
		sb.append(speed(bytesPerSecond == 0 ? allBytes : bytesPerSecond));
		sb.append("  success: ").append(successCount);
		sb.append("  fail: ").append(failCount);
		sb.append("  skip: ").append(skipCount);
		return sb;
	}
	
	private static <T> void output(T t, Consumer<T> consumer) {
		consumer.accept(t);
	}
	
	public static void setRequestHeaders(Map<String, String> headers) {
		Objects.requireNonNull(headers);
		requestHeaders = headers;
	}
	
	public static void setRequestHeader(String key, String value) {
		requestHeaders.put(key, value);
	}
	
	/**
	 * 任务下载状态
	 * @author abeholder
	 *
	 */
	static enum State {
		SUCCESS, FAIL, EXISTED;
	}
	
	
	private static class TrustAnyTrustManager implements X509TrustManager {
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[] {};
		}
	}

	private static class TrustAnyHostnameVerifier implements HostnameVerifier {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}
}
