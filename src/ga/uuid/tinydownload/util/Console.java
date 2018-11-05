package ga.uuid.tinydownload.util;

/**
 * 
 * <b>Description:</b><br> 
 * 控制台
 * @author abeholder
 */
public class Console {
	
	private static User32 user32 = User32.INSTANCE;
	private static String lastText = "";
	private static String title = "        tiny.downloader        MADE BY abedormancy. T_T";
	
	final static int hwnd = user32.GetStdHandle(-11); // 获取默认console句柄
	
	private Console() {
		throw new UnsupportedOperationException();
	}
	
	public static void print(String text) {
		if (text != null && lastText.equals(text)) return;
		lastText = text;
		
		int console = user32.CreateConsoleScreenBuffer(0x80000000|0x40000000, 1|2, null, 1, null);
		user32.SetConsoleScreenBufferSize(console, 8192);
		user32.WriteConsoleA(console, text, text.length(), 0, null);
		user32.SetConsoleActiveScreenBuffer(console);
		user32.FlushConsoleInputBuffer(console);
		user32.SetConsoleTitleA(title);
	}
	
	static void print(String title, String text) {
		setTitle(title);
		print(text);
	}
	
	public static void setTitle(String title) {
		Console.title = title;
	}
	
	/**
	 * 恢复默认console
	 */
	public static void recover() {
		user32.SetConsoleActiveScreenBuffer(hwnd);
	}
}
