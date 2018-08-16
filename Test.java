package ga.uuid.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Test {
	
	public static void main(String[] args) {
		try {
			Files.lines(Paths.get("R:/1.txt"))
				.filter(Const::isNotEmpty)
				.forEach(url -> TinyDownloader.add(url, "R:/helloworld"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		TinyDownloader.ensure();
	}
}





