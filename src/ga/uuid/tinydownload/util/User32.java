package ga.uuid.tinydownload.util;

import com.sun.jna.*;

/**
 * 定义
 * @author abeholder
 *
 */
public interface User32 extends Library {
	
	User32 INSTANCE = (User32) Native.loadLibrary(null, User32.class);
	
	int GetStdHandle(int ok);
	int CreateConsoleScreenBuffer(int access, int shareMode, Object security, int flags, Object data);
	int SetConsoleActiveScreenBuffer(int hwnd);
	int SetConsoleScreenBufferSize(int hwnd, int size);
	int WriteConsoleA(int hwnd, String text, int size, int lpNumberOfCharsWritten, Object flags);
	int FlushConsoleInputBuffer(int hwnd);
	int SetConsoleTitleA(String title);
}