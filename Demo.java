package ga.uuid.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Demo {
	
	public static void main(String[] args) {
		try {
			Files.lines(Paths.get("./resources/urls.txt"))
				.filter(Const::isNotEmpty)
				.forEach(url -> TinyDownloader.add(url, "r:/tinydownloader_demo"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		TinyDownloader.ensure();
	}
}





