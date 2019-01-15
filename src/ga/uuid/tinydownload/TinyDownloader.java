package ga.uuid.tinydownload;

import static ga.uuid.tinydownload.Const.*;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import ga.uuid.tinydownload.util.Console;
import ga.uuid.tinydownload.util.FixedList;
import ga.uuid.tinydownload.util.TrustAnyHostnameVerifier;
import ga.uuid.tinydownload.util.TrustAnyTrustManager;

public class TinyDownloader {
	
	private static final ExecutorService pool = new ThreadPoolExecutor(
			THREAD_SIZE,
			THREAD_SIZE,
			0L,
			TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<>(8192),
			Executors.defaultThreadFactory(), (r, e) -> {
				// 饱和策略 r -> Runnable; e -> ThreadPoolExecutor
				if (!e.isShutdown()) {
					delay(30_000);
					e.execute(r);
				}
			}
	);
	// 正在下载的任务
	private static final FixedList<DownloadTask> downloadingList = new FixedList<>(THREAD_SIZE);
	
	// 统计
	private static LongAdder successCount = new LongAdder();
	private static LongAdder failCount = new LongAdder();
	private static LongAdder skipCount = new LongAdder();
	@SuppressWarnings("serial")
	private static final Map<State, LongAdder> counterMap = new HashMap<State, LongAdder>() {{
		// 统计映射
		put(State.SUCCESS, successCount);
		put(State.FAIL, failCount);
		put(State.EXISTED, skipCount);
	}};
	
	// 不使用同步，允许误差
	private static transient int allBytes = 0;
	private static transient int bytesPerSecond = 0;
	
	// 同源任务检查 map
	private static transient Map<String, Long> sameOrigin = new ConcurrentHashMap<>();
	
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
			throw new RuntimeException(e);
		}
		
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
				// sameOrigin clear
				sameOrigin.values().removeIf(i -> {
					return now() - 1000 > i;
				});
			}
		}, "network_monitor_thread").start();
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 确认下载任务，确认后不能再通过 add 方式添加下载任务
	 * @return
	 * <b>Author:</b> abeholder
	 */
	public static void ensure() {
		pool.shutdown();
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 添加下载任务
	 * @param url 文件远程url地址
	 * @param path 保存的目录
	 * @return
	 * <b>Author:</b> abeholder
	 */
	public static void add(String url, String path) {
		add(url, path, null);
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 添加下载任务
	 * @param url 远程url地址
	 * @param path 保存的目录
	 * @param filename 保存的文件名
	 * @return
	 * <b>Author:</b> abeholder
	 */
	public static void add(String url, String path, String filename) {
		if (!pool.isShutdown()) {
			DownloadTask task = new DownloadTask(url, path, filename);
			// 如果文件名为空 ，自动为其增加下载序列前缀
			if (isEmpty(filename)) {
				task.setSerial(currentSerial.intValue());
				currentSerial.increment();
			}
			pool.execute(task);
		} else {
			// TODO else 任务队列已关闭
			System.out.println("the Thread pool has been closed");
		}
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 以阻塞方式直接下载文件
	 * @param url 远程url地址
	 * @param path 保存的目录
	 * @return
	 * <b>Author:</b> abeholder
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
	 * <b>Author:</b> abeholder
	 */
	public static State download(String url, String path, String filename) {
		System.out.println("downloading " + url + " --> " + path);
		State state = download(new DownloadTask(url, path, filename));
		System.out.println(state);
		return state;
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 下载文件简单实现 ，buff中直接保存文件所有字节，下载完后写入磁盘
	 * 不支持断点续传，主要用于爬取的图片等小文件下载，不推荐大文件下载
	 * @param task
	 * @return
	 * <b>Author:</b> abeholder
	 */
	static State download(DownloadTask task) {
		Objects.requireNonNull(task);
		
		URL url = null;
		URLConnection conn = null;
		String destFile = "";
		final boolean isHttps = task.getUrl().toLowerCase().startsWith("https");
		
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

			setHeaders(conn);
			
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
			
			task.setFilename(rep2empty(task.getFilename()));
			createDirectories(task);
			
			destFile = String.join("/", task.getPath(), task.getFilenameWithSerial());
			if (existFile(destFile)) return State.EXISTED;
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
				// TODO 记录
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
			// TODO 记录
			e.printStackTrace();
			return State.FAIL;
		}
		return State.SUCCESS;
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 检查文件是否存在
	 * @param filepath
	 * @return
	 * @throws IOException
	 * <b>Author:</b> abeholder
	 */
	private static boolean existFile(String filepath) throws IOException {
		Path path = Paths.get(filepath);
		return Files.exists(path) && Files.size(path) > 0;
	}

	/**
	 * 
	 * <b>Description:</b><br> 
	 * 创建对应的文件夹
	 * @param task
	 * @throws IOException
	 * <b>Author:</b> abeholder
	 */
	private static void createDirectories(DownloadTask task) throws IOException {
		Path directory = Paths.get(task.getPath());
		if (Files.notExists(directory)) {
			synchronized (TinyDownloader.class) {
				if (Files.notExists(directory)) 
					Files.createDirectories(directory);
			}
		}
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 设置用户自定义 header 
	 * @param conn
	 * <b>Author:</b> abeholder
	 */
	private static void setHeaders(URLConnection conn) {
		conn.setConnectTimeout(DOWNLOAD_TIMEOUT);  
		conn.setReadTimeout(DOWNLOAD_TIMEOUT);
		conn.setRequestProperty("User-Agent","Mozilla/5.0 AppleWebKit/537.36" + Math.random());
		
		requestHeaders.forEach((key, value) -> {
			conn.setRequestProperty(key, value);
		});
	}

	private static void writeFile(byte[] bytes, String file) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file);
				BufferedOutputStream bos = new BufferedOutputStream(fos)) {
			bos.write(bytes);
		}
	}
	
	/**
	 * 
	 * <b>Description:</b><br>
	 * 同源任务检查 (在间隔时间内延迟下载)，避免 503
	 * @param host
	 * <b>Author:</b> abeholder
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
					});
		}
	}

	/**
	 * 
	 * <b>Description:</b><br> 
	 * 通过headers解析出文件名
	 * @param map the headers map.
	 * @return
	 * <b>Author:</b> abeholder
	 */
	private static final Optional<String> parseFilename(Map<String, List<String>> map) {
		String filename = null;
		try {
			String disposition = map.get("Content-Disposition").get(0);
			Pattern pattern = Pattern.compile("(filename\\*?)=([^;\"\\S']+)");
			Matcher matcher = pattern.matcher(disposition);
			while (matcher.find()) {
				filename = matcher.group(2);
				if ("filename*".equals(matcher.group(1))) break;
			}
		} catch (NullPointerException e) {}
		if (isEmpty(filename)) filename = null; 
		return Optional.ofNullable(filename);
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 将该任务加入到正在下载的列表
	 * @param task
	 * @return
	 * <b>Author:</b> abeholder
	 */
	synchronized static boolean downloading_add(DownloadTask task) {
		return downloadingList.add(task);
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 将该任务移除正在下载列表
	 * @param task
	 * @return
	 * <b>Author:</b> abeholder
	 */
	synchronized static boolean downloading_remove(DownloadTask task) {
		return downloadingList.remove(task);
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 统计下载成功、失败、跳过数量
	 * @param state
	 * <b>Author:</b> abeholder
	 */
	static void statistcsCount(State state) {
		counterMap.get(state).increment();
	}
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 统计信息
	 * @return
	 * <b>Author:</b> abeholder
	 */
	private static StringBuilder statisticsInfo() {
		StringBuilder sb = new StringBuilder(2048);
		sb.append("\n\tspeed: ");
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
	
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 设置请求头
	 * @param key
	 * @param value
	 * <b>Author:</b> abeholder
	 */
	public static void setRequestHeader(String key, String value) {
		requestHeaders.put(key, value);
	}
}
