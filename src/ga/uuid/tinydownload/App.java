package ga.uuid.tinydownload;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class App {
	
	public static void main(String[] args) {
		try {
			if (args.length != 0) {
				Path urlPath = Paths.get(args[0]);
				List<String> urls = Files.readAllLines(urlPath, StandardCharsets.UTF_8)
										.stream()
										.map(s -> s.split("[,;\\s]"))
										.flatMap(Arrays::stream)
										.filter(App::isNotEmpty)
										.distinct()
										.collect(Collectors.toList());
				if (urls.size() > 0) {
					System.out.println("\n\n\n\tunique_count: " + urls.size());
					String result = readLine("\n\tsave to: ");
					Path directory = null;
					
					if (isEmpty(result)) {
						directory = urlPath.getParent();
					} else directory = Paths.get(result);
					
					if (Files.notExists(directory)) Files.createDirectories(directory);
					
					String folder = directory.toString();
					urls.forEach(url -> {
						TinyDownloader.add(url, folder);
					});
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		TinyDownloader.ensure();
	}
	
	private static final boolean isEmpty(Object obj) {
		return obj == null || String.valueOf(obj).trim().length() == 0;
	}
	
	private static final boolean isNotEmpty(Object obj) {
		return !isEmpty(obj);
	}
	
	private static final String readLine(String msg) {
		String value = "";
		try (Scanner sc = new Scanner(System.in)) {
			System.out.print(msg);
			value = sc.nextLine();
			System.out.println();
		}
		return value;
	}
}





