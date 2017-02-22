package twitter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import edu.mit.streamjit.api.CompiledStream;
import edu.mit.streamjit.api.Filter;
import edu.mit.streamjit.api.Input;
import edu.mit.streamjit.api.OneToOneElement;
import edu.mit.streamjit.api.Output;
import edu.mit.streamjit.api.StreamCompiler;
import edu.mit.streamjit.impl.compiler2.Compiler2StreamCompiler;
import edu.mit.streamjit.test.Benchmark.Dataset;
import edu.mit.streamjit.test.Datasets;
import edu.mit.streamjit.util.Pair;

public class TwitterProcessing {

	
	public static void main(String[] args) throws InterruptedException {
		StreamCompiler sc = new Compiler2StreamCompiler();
		Path path = Paths.get("data/tweets.in");
		Input<String> in = Input.fromTextFile(path);
		OneToOneElement<String, Pair<String,Integer>> core = new TwitterFilter();
		CompiledStream stream = sc.compile(core, in, 
				Output.<Pair<String,Integer>> toPrintStream(System.out));
		stream.awaitDrained();
	}

	public static class TwitterFilter extends Filter<String, Pair<String,Integer>> {

		public TwitterFilter() {
			super(1, 1);
		}

		@Override
		public void work() {
			String s = pop();
		    ObjectMapper jsonParser=null;
			Pair<String, Integer> p= null;
			try {
				if(jsonParser == null) {
					jsonParser = new ObjectMapper();
				}
				JsonNode jsonNode = jsonParser.readValue(s, JsonNode.class);
				boolean hasText = jsonNode.has("text");
				if (hasText) {
					// message of tweet
					StringTokenizer tokenizer = new StringTokenizer(jsonNode.get("text").asText());

					// split the message
					while (tokenizer.hasMoreTokens()) {
						String result = tokenizer.nextToken().replaceAll("\\s*", "").toLowerCase();

						if (!result.equals("")) {
							p= new Pair<>(result, 1);
						}
					}
				}
			} catch (Exception e) {
				System.out.print(s);
			}
			push(p);
		}
	}
	
}