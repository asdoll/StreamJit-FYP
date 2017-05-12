package edu.mit.streamjit.impl.compiler2;

import static com.google.common.base.Preconditions.*;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import edu.mit.streamjit.api.Rate;
import edu.mit.streamjit.util.ilpsolve.ILPSolver;
import edu.mit.streamjit.util.ilpsolve.SolverException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 8/1/2013
 */
public final class Schedule2<T> {
	private final ImmutableSet<T> things;
	private final ImmutableSet<BufferingConstraint<T>> constraints;
	private final ImmutableMap<T, Integer> schedule;
	private Schedule2(ImmutableSet<T> things, ImmutableSet<BufferingConstraint<T>> constraints, ImmutableMap<T, Integer> schedule) {
		this.things = things;
		this.constraints = constraints;
		this.schedule = schedule;
	}
	private static <T> Schedule2<T> schedule(ImmutableSet<T> things,
			ImmutableSet<ExecutionConstraint<T>> executionConstraints,
			ImmutableSet<BufferingConstraint<T>> bufferingConstraints,
			int multiplier, int fireCost, int excessBufferCost) {
		ILPSolver solver = new ILPSolver();
		//There's one variable for each thing, which represents the number of
		//times it fires.  This uses the default bounds.  (TODO: perhaps a bound
		//at 1 if we're steady-state scheduling, maybe by marking things as
		//must-fire and marking the bottommost thing?)
		ImmutableMap.Builder<T, ILPSolver.Variable> variablesBuilder = ImmutableMap.builder();
		for (T thing : things)
			variablesBuilder.put(thing, solver.newVariable(thing.toString()));
		ImmutableMap<T, ILPSolver.Variable> variables = variablesBuilder.build();

		for (ExecutionConstraint<T> constraint : executionConstraints)
			solver.constrainAtLeast(variables.get(constraint.thing).asLinearExpr(1), constraint.minExecutions);

		HashMap<ILPSolver.Variable, Integer> sumOfConstraints = new HashMap<>();
		for (ILPSolver.Variable v : variables.values())
			sumOfConstraints.put(v, 0);
		for (BufferingConstraint<T> constraint : bufferingConstraints) {
			ILPSolver.Variable upstreamVar = variables.get(constraint.upstream),
					downstreamVar = variables.get(constraint.downstream);
			ILPSolver.LinearExpr expr = upstreamVar.asLinearExpr(constraint.pushRate.max())
					.minus(constraint.popRate.max(), downstreamVar);
			switch (constraint.condition) {
				case LESS_THAN_EQUAL:
					solver.constrainAtMost(expr, constraint.bufferDelta);
					break;
				case EQUAL:
					solver.constrainEquals(expr, constraint.bufferDelta);
					break;
				case GREATER_THAN_EQUAL:
					solver.constrainAtLeast(expr, constraint.bufferDelta);
					break;
				default:
					throw new AssertionError(constraint.condition);
			}

			sumOfConstraints.put(upstreamVar, sumOfConstraints.get(upstreamVar) + constraint.pushRate.max());
			sumOfConstraints.put(downstreamVar, sumOfConstraints.get(downstreamVar) - constraint.popRate.max());
		}

		//Add a special constraint to ensure at least one filter fires.
		//TODO: in init schedules we might not always need this...
		Iterator<ILPSolver.Variable> variablesIter = variables.values().iterator();
		ILPSolver.LinearExpr totalFirings = variablesIter.next().asLinearExpr(1);
		while (variablesIter.hasNext())
			totalFirings = totalFirings.plus(1, variablesIter.next());
		solver.constrainAtLeast(totalFirings, 1);

		for (ILPSolver.Variable v : variables.values())
			sumOfConstraints.put(v, sumOfConstraints.get(v) * excessBufferCost + fireCost);
		ILPSolver.ObjectiveFunction objFn = solver.minimize(solver.newLinearExpr(
			Maps.filterValues(sumOfConstraints, Predicates.not(Predicates.equalTo(0)))));

		try {
			solver.solve();
		} catch (SolverException ex) {
			throw new ScheduleException(ex);
		}

		ImmutableMap.Builder<T, Integer> schedule = ImmutableMap.builder();
		for (Map.Entry<T, ILPSolver.Variable> e : variables.entrySet())
			schedule.put(e.getKey(), e.getValue().value() * multiplier);
		return new Schedule2<>(things, bufferingConstraints, schedule.build());
	}

	public static <T> Builder<T> builder() {
		return new Builder<>();
	}

	public static final class Builder<T> {
		private final Set<T> things = new HashSet<>();
		private final Set<ExecutionConstraint<T>> executionConstraints = new HashSet<>();
		private final Set<BufferingConstraint<T>> bufferingConstraints = new HashSet<>();
		private int multiplier = 1, fireCost = 1, excessBufferCost = 0;
		private Builder() {}

		public Builder<T> add(T thing) {
			things.add(checkNotNull(thing));
			return this;
		}
		public Builder<T> addAll(Iterable<? extends T> things) {
			for (T thing : things)
				add(thing);
			return this;
		}

		public final class BufferingConstraintBuilder {
			private final T upstream;
			private final T downstream;
			private Rate pushRate = Rate.create(-1);
			private Rate popRate = Rate.create(-1);
			private Rate peekRate = Rate.create(-1);
			private BufferingConstraint.Condition condition = null;
			private int bufferDelta = -1;
			private BufferingConstraintBuilder(T upstream, T downstream) {
				this.upstream = upstream;
				this.downstream = downstream;
			}
			public BufferingConstraintBuilder push(Rate pushRate) {
				checkArgument(pushRate.min() >= 0);
				this.pushRate = pushRate;
				return this;
			}
			public BufferingConstraintBuilder pop(Rate popRate) {
				checkArgument(popRate.min() >= 0);
				this.popRate = popRate;
				return this;
			}
			public BufferingConstraintBuilder peek(Rate peekRate) {
				checkArgument(peekRate.min() >= 0);
				this.peekRate = peekRate;
				return this;
			}
			public Builder<T> bufferExactly(int items) {
				this.condition = BufferingConstraint.Condition.EQUAL;
				this.bufferDelta = items;
				return build();
			}
			public Builder<T> bufferAtLeast(int items) {
				this.condition = BufferingConstraint.Condition.GREATER_THAN_EQUAL;
				this.bufferDelta = items;
				return build();
			}
			public Builder<T> bufferAtMost(int items) {
				this.condition = BufferingConstraint.Condition.LESS_THAN_EQUAL;
				this.bufferDelta = items;
				return build();
			}
			public Builder<T> unconstrained() {
				return bufferAtLeast(Integer.MIN_VALUE);
			}
			private Builder<T> build() {
				if (pushRate.max() == 0 && popRate.max() == 0) {
					checkArgument(peekRate.max() == 0, "can't peek %d on 0-rate edge", peekRate);
					switch (condition) {
						case EQUAL:
							checkArgument(bufferDelta == 0, "can't be == %d on 0-rate edge", bufferDelta);
							break;
						case GREATER_THAN_EQUAL:
							checkArgument(bufferDelta <= 0, "can't be >= %d on 0-rate edge", bufferDelta);
							break;
						case LESS_THAN_EQUAL:
							checkArgument(bufferDelta >= 0, "can't be <= %d on 0-rate edge", bufferDelta);
							break;
					}
					//Don't add a constraint -- the solver gets confused.
					return Builder.this;
				}
				Builder.this.addConstraint(new BufferingConstraint<>(upstream, downstream, pushRate, popRate, peekRate, condition, bufferDelta));
				return Builder.this;
			}
		}

		public BufferingConstraintBuilder connect(T upstream, T downstream) {
			checkArgument(things.contains(upstream), "upstream %s not in %s", upstream, this);
			checkArgument(things.contains(downstream), "downstream %s not in %s", downstream, this);
			return new BufferingConstraintBuilder(upstream, downstream);
		}

		private void addConstraint(BufferingConstraint<T> constraint) {
			bufferingConstraints.add(constraint);
		}

		public Builder<T> executeAtLeast(T thing, int minExecutions) {
			executionConstraints.add(new ExecutionConstraint<>(thing, minExecutions));
			return this;
		}

		public Builder<T> multiply(int multiplier) {
			checkArgument(multiplier >= 1);
			this.multiplier *= multiplier;
			return this;
		}

		public Builder<T> costs(int fireCost, int excessBufferCost) {
			checkArgument(fireCost >= 0, fireCost);
			checkArgument(excessBufferCost >= 0, excessBufferCost);
			this.fireCost = fireCost;
			this.excessBufferCost = excessBufferCost;
			return this;
		}

		public Schedule2<T> build() {
			return schedule(ImmutableSet.copyOf(things), ImmutableSet.copyOf(executionConstraints), ImmutableSet.copyOf(bufferingConstraints), multiplier, fireCost, excessBufferCost);
		}

		@Override
		public String toString() {
			return "["+things+"; "+bufferingConstraints+"; x"+multiplier+"]";
		}
	}

	private static class BufferingConstraint<T> {
		private static enum Condition {
			LESS_THAN, LESS_THAN_EQUAL, EQUAL, GREATER_THAN_EQUAL, GREATER_THAN;
		};
		private final T upstream, downstream;
		Rate pushRate;
		Rate popRate;
		private final int excessPeeks;
		private final Condition condition;
		private final int bufferDelta;
		private BufferingConstraint(T upstream, T downstream, Rate pushRate, Rate popRate, Rate peekRate, Condition condition, int bufferDelta) {
			this.upstream = upstream;
			this.downstream = downstream;
			this.pushRate = pushRate;
			this.popRate = popRate;
			this.excessPeeks = Math.max(0, peekRate.max() - popRate.max());
			this.condition = condition;
			this.bufferDelta = bufferDelta;
		}
		@Override
		public String toString() {
			return String.format("%s (push %d) -> %s (peek %d pop %d) %s %d",
					upstream, pushRate,
					downstream, excessPeeks , popRate,
					condition, bufferDelta);
		}
	}

	private static class ExecutionConstraint<T> {
		private final T thing;
		private final int minExecutions; //we can use the Condition enum if we ever want exact/less
		private ExecutionConstraint(T thing, int minExecutions) {
			this.thing = thing;
			this.minExecutions = minExecutions;
		}
		@Override
		public String toString() {
			return String.format("exec %s >= %d", thing, minExecutions);
		}
	}

	public ImmutableMap<T, Integer> getSchedule() {
		return schedule;
	}

	public int getExecutions(T thing) {
		return schedule.get(thing);
	}

	public static final class ScheduleException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public ScheduleException() {
		}
		public ScheduleException(String message) {
			super(message);
		}
		public ScheduleException(String message, Throwable cause) {
			super(message, cause);
		}
		public ScheduleException(Throwable cause) {
			super(cause);
		}
	}
}
