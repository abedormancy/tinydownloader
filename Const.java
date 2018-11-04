package ga.uuid.app;

import java.util.stream.IntStream;

import org.nocrala.tools.texttablefmt.BorderStyle;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.CellStyle.HorizontalAlign;

import ga.uuid.app.util.FixedList;

import org.nocrala.tools.texttablefmt.ShownBorders;
import org.nocrala.tools.texttablefmt.Table;

/**
 * 
 * <b>Description:</b><br> 
 * 一些配置和静态辅助方法
 * @author abeholder
 */
public interface Const {
	
	int MS_CMD = System.getProperty("sun.stdout.encoding", "").startsWith("ms") ? 1 : 0; // 是否运行在MS-CMD中
	
	CellStyle right = new CellStyle(HorizontalAlign.right); // table 样式
	CellStyle center = new CellStyle(HorizontalAlign.center);
	
	int THREAD_SIZE = 10; // 线程数量 （同时下载文件数）
	int RETRY_COUNT = 3; // 下载失败重试次数
	int SAME_ORIGIN_DELAY = 100; // 同源任务延迟毫秒数
	int OUTPUT_MODE = 1 & MS_CMD; // 0: 统计模式; 1: 实时模式
	int OUTPUT_INTERVAL = OUTPUT_MODE == 1 ? 128 : 2000; // 输出间隔，毫秒
	int DOWNLOAD_TIMEOUT = 3000; // 下载任务连接超时时间
	
	
	int PROGRESS_BAR_LENGTH = 20; // 进度条长度
	String[] UNFINISHED_MAPPER = initMapperCache('-'); // 未下载样式百分比样式
	String[] FINISHED_MAPPER = initMapperCache('='); // 已下载百分比样式
	String[] SERIAL_MAPPER = initMapperCache('0', 4);
	/**
	 * 
	 * <b>Description:</b><br> 
	 * 初始化进度条缓存
	 * @param ch
	 * @return
	 * <b>Author:</b> abeholder
	 */
	static String[] initMapperCache(char ch) {
		String[] result = initMapperCache(ch, PROGRESS_BAR_LENGTH);
		
		// custom
		if ("=".equals(result[1])) {
			for (int i = 1; i < result.length; i++) {
				StringBuilder sb = new StringBuilder(result[i]);
				sb.setCharAt(i - 1, '>');
				result[i] = sb.toString();
			}
		}
		return result;
	}
	
	static String[] initMapperCache(char ch, int number) {
		String[] result = new String[number + 1];
		IntStream.rangeClosed(0, number).forEach(x -> {
			StringBuilder sb = new StringBuilder(x);
			for (int i = 0; i < x; i++) {
				sb.append(ch);
			}
			result[x] = sb.toString();
		});
		return result;
	}
	
	static boolean isEmpty(Object obj) {
		return obj == null || String.valueOf(obj).trim().length() == 0;
	}
	
	static boolean isNotEmpty(Object obj) {
		return !isEmpty(obj);
	}
	
	static void requireNonBlank(Object obj, String ... message) {
		if (isEmpty(obj)) {
			throw new NullPointerException(message.length > 0 ? message[0] : null);
		}
	}
	
	static String rep2empty(String filename) {
//		return filename.replaceAll("[<>*|?\\\"/\\\\:]", "_");
		char[] cs = "<>*|?\"/\\:".toCharArray();
		for (char c : cs) {
			if (filename.indexOf(c) != -1) 
				filename = filename.replace(c, '_');
		}
		return filename;
	}
	
	static void delay(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	static StringBuilder speed(int bytes) {
		return prettySize(bytes).append("/s");
	}

	static StringBuilder prettySize(int bytes) {
		float sp = bytes;
		String text;
		if (sp < 1024) {
			text = " B";
		} else if (sp >= 1024 && sp < 1048576) {
			sp /= 1024;
			text = " KB";
		} else {
			sp /= 1048576;
			text = " MB";
		}
		return new StringBuilder(String.format("%.2f", sp)).append(text);
	}
	
	static Table table() {
		Table t = new Table(5, BorderStyle.CLASSIC, ShownBorders.SURROUND_HEADER_AND_COLUMNS);
		t.setColumnWidth(0, 2, 2);
		t.setColumnWidth(1, 32, 32);
		t.setColumnWidth(2, PROGRESS_BAR_LENGTH + 2, PROGRESS_BAR_LENGTH + 2);
		t.setColumnWidth(3, 10, 10);
		t.setColumnWidth(4, 16, 16);
		t.addCell("#", center);
		t.addCell("filename", center);
		t.addCell("percent", center);
		t.addCell("size", center);
		t.addCell("speed", center);
		return t;
	}

	static long now() {
		return System.currentTimeMillis();
	}
	
	static String progressBar(float percent) {
		if (percent > 1) percent = 1f;
		if (percent < 0) percent = 0f;
		
		int v = (int) (percent  * PROGRESS_BAR_LENGTH) ;
		String finished = FINISHED_MAPPER[v];
		String unfinished = UNFINISHED_MAPPER[PROGRESS_BAR_LENGTH - v];
		return new StringBuilder(PROGRESS_BAR_LENGTH).append(finished).append(unfinished).toString();
	}
	
	/**
	 * 返回渲染完成后的表格
	 * @param list
	 * @return
	 */
	static String renderTable(FixedList<DownloadTask> list) {
		Table t = table();
		int seq = 1;
		for (int i = 0; i < THREAD_SIZE; i++) {
//			Optional.ofNullable(list.get(i)).orElseGet(other)
			DownloadTask task = list.get(i, DownloadTask::empty);
			if (task.getReceivedSize() > 0) { // downloading...
				t.addCell(String.valueOf(seq++), center);
				t.addCell(task.getFilename());
				t.addCell(progressBar(task.getReceivedSize() / 1f / task.getFilesize()), center);
				t.addCell(Const.prettySize(task.getFilesize()).toString(), right);
				t.addCell(Const.speed(task.getSpeed()).toString(), right);
			} else {
				if (task == DownloadTask.empty()) { // none
//					t.addCell("-", center, 5);
					t.addCell("");
					t.addCell("");
					t.addCell("-", center);
					t.addCell("");
					t.addCell("");
				} else { // connecting
					t.addCell(String.valueOf(seq++), center);
					t.addCell("");
					t.addCell("connecting ...", center);
					t.addCell("");
					t.addCell("");
				}
			}
		}
		return t.render();
	}
	
	static String serial(int value) {
		int max = SERIAL_MAPPER.length - 1;
		int len = max - ((int) Math.log10(value) + 1);
		if (len < max && len > 0) {
			return SERIAL_MAPPER[len] + value;
		} return String.valueOf(value);
//		return String.format("%04d", value);
	}
}
