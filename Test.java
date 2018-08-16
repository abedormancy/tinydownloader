package ga.uuid.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Test {
	
	public static void main(String[] args) {
		try {
			Files.lines(Paths.get("./urls.txt"))
				.filter(Const::isNotEmpty)
				.forEach(url -> TinyDownloader.add(url, "R:/download_demo"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		TinyDownloader.ensure();
	}
}





