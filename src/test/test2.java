package test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import edu.mit.streamjit.api.CompiledStream;
import edu.mit.streamjit.api.Filter;
import edu.mit.streamjit.api.OneToOneElement;
import edu.mit.streamjit.api.Output;
import edu.mit.streamjit.api.StreamCompiler;
import edu.mit.streamjit.impl.compiler2.Compiler2StreamCompiler;
import edu.mit.streamjit.test.Benchmark.Dataset;
import edu.mit.streamjit.test.Datasets;
import edu.mit.streamjit.util.Pair;

public class test2 {

	private transient static ObjectMapper jsonParser;
	
	public static void main(String[] args) throws Exception {
		Scanner scanner = new Scanner(System.in);
		try {
			System.out.println(flatMap(args[0]));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println(flatMap(scanner.nextLine()));
		}
	}
	
	public static Pair<String, Integer> flatMap(String value) throws Exception {
		Pair<String, Integer> out= null;
		if(jsonParser == null) {
			jsonParser = new ObjectMapper();
		}
		JsonNode jsonNode = jsonParser.readValue(value, JsonNode.class);
		boolean hasText = jsonNode.has("text");
		if (hasText) {
			// message of tweet
			StringTokenizer tokenizer = new StringTokenizer(jsonNode.get("text").asText());

			// split the message
			while (tokenizer.hasMoreTokens()) {
				String result = tokenizer.nextToken().replaceAll("\\s*", "").toLowerCase();

				if (!result.equals("")) {
					out= new Pair<>(result, 1);
				}
			}
		}
		return out;
	}
}