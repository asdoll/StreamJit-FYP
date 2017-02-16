package test;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import edu.mit.streamjit.api.CompiledStream;
import edu.mit.streamjit.api.DuplicateSplitter;
import edu.mit.streamjit.api.Filter;
import edu.mit.streamjit.api.Input;
import edu.mit.streamjit.api.OneToOneElement;
import edu.mit.streamjit.api.Output;
import edu.mit.streamjit.api.Pipeline;
import edu.mit.streamjit.api.RoundrobinJoiner;
import edu.mit.streamjit.api.Splitjoin;
import edu.mit.streamjit.api.StreamCompiler;
import edu.mit.streamjit.impl.compiler2.Compiler2StreamCompiler;
import edu.mit.streamjit.test.Datasets;

public class test {
	public static void main(String[] args) throws InterruptedException {
		// Benchmarker.runBenchmarks(new FMRadioBenchmarkProvider(), new DebugStreamCompiler()).get(0).print(System.out);
		StreamCompiler sc = new Compiler2StreamCompiler();
		OneToOneElement<String, String> fmradio = new Example1();
		
		Path path = Paths.get("data/fmradio.in");
		Input<String> input = Input.fromBinaryFile(path, String.class, ByteOrder.LITTLE_ENDIAN);
		input = Datasets.nCopies(1, input);
		System.out.print(input);
		CompiledStream cs = sc.compile(fmradio, input, Output.toPrintStream(System.out));
		cs.awaitDrained();
	}
	private static final class Example1 extends Filter<String, String> {

		public Example1() {
			super(1, 1);
			
		}

		@Override
		public void work() {
			
			String a = pop();
			String b = a.toLowerCase();
			push(b);
		
		}
		
	}
	private static final class TestPipeline extends Pipeline<Float, Float> {
		private TestPipeline() {
			int n = 10;
			// The splitjoin is BPFCore in the StreamIt source.
			Splitjoin<Float, Float> eqSplit = new Splitjoin<>(
					new DuplicateSplitter<Float>(),
					new RoundrobinJoiner<Float>());
			for (int i = 1; i < n; ++i)
				eqSplit.add(new Pipeline<Float, Float>(new Example1(), new Example1()));
			add(eqSplit);

			add(new Filter<Float, Float>(n - 1, 1) {
				@Override
				public void work() {
					float sum = 0;
					for (int i = 0; i < n - 1; ++i)
						sum += pop();
					push(sum);
					}
				});
		}
	}
	private static final class Subtractor extends Filter<Float, Float> {
		private Subtractor() {
			super(2, 1, 0);
		}

		@Override
		public void work() {
			float a = pop(), b = pop();
			push(b - a);
		}
	}
}
