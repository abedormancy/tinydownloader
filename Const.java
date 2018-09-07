package ga.uuid.app;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import org.nocrala.tools.texttablefmt.BorderStyle;
import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.CellStyle.HorizontalAlign;
import org.nocrala.tools.texttablefmt.ShownBorders;
import org.nocrala.tools.texttablefmt.Table;

/**
 * 一些配置及静态方法~
 * @author abeholder
 *
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
	Map<Integer, String> UNFINISHED_MAPPER = initMapperCache('='); // 未下载样式百分比映射
	Map<Integer, String> FINISHED_MAPPER = initMapperCache('>'); // 已下载百分比样式映射
	
	/**
	 * 初始化进度条缓存字符
	 * @param ch 字符
	 * @param length 长度
	 * @return
	 */
	static Map<Integer, String> initMapperCache(char ch) {
		return initMapperCache(ch, PROGRESS_BAR_LENGTH);
	}
	
	static Map<Integer, String> initMapperCache(char ch, int number) {
		Map<Integer, String> mapper = new HashMap<>();
		IntStream.rangeClosed(0, number).forEach(x -> {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < x; i++) {
				sb.append(ch);
			}
			mapper.put(x, sb.toString());
		});
		return mapper;
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
			e.printStackTrace();
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
		Table t = new Table(5, BorderStyle.DESIGN_PAPYRUS, ShownBorders.SURROUND_HEADER_AND_COLUMNS);
		t.setColumnWidth(0, 2, 2);
		t.setColumnWidth(1, 32, 32);
		t.setColumnWidth(2, PROGRESS_BAR_LENGTH, PROGRESS_BAR_LENGTH);
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
		String finished = FINISHED_MAPPER.get(v);
		String unfinished = UNFINISHED_MAPPER.get(PROGRESS_BAR_LENGTH - v);
		return new StringBuilder().append(finished).append(unfinished).toString();
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
					t.addCell("", 2);
					t.addCell("-", center);
					t.addCell("", 2);
				} else { // connecting
					t.addCell(String.valueOf(seq++), center);
					t.addCell("");
					t.addCell("connecting ...", center);
					t.addCell("", 2);
				}
			}
		}
		return t.render();
	}
	
	static String serial(int value) {
//		Map<Integer, String> mapper = initMapperCache('0', 4);
		int len = 4 - String.valueOf(value).length();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; i++) {
			sb.append(0);
		}
		return sb.append(value).toString();
	}
}
