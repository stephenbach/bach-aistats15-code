package edu.umd.cs.bachaistats15;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.DoubleAttribute;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.kernel.AbstractKernel;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.util.database.Queries;

public class BiasKernel extends AbstractKernel implements CompatibilityKernel {
	
	private final StandardPredicate predicate;
	private final StandardPredicate valuePredicate;
	private PositiveWeight weight;
	private boolean weightMutable;
	
	public BiasKernel(StandardPredicate predicate, StandardPredicate valuePredicate, PositiveWeight weight) {
		this.predicate = predicate;
		if (valuePredicate.getArity() < 2 || !valuePredicate.getArgumentType(valuePredicate.getArity()-1).equals(ArgumentType.Double))
			throw new IllegalArgumentException("valuePredicate must have arity at least 2 and Double as last argument type.");
		this.valuePredicate = valuePredicate;
		this.weight = weight;
		this.weightMutable = false;
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundKernelStore gks) {
		ResultList results = atomManager.executeQuery(Queries.getQueryForAllAtoms(valuePredicate));
		for (int i = 0; i < results.size(); i++) {
			GroundTerm[] args = Arrays.copyOf(results.get(i), results.get(i).length - 1);
			double bias = ((DoubleAttribute) results.get(i)[results.get(i).length - 1]).getValue();
			gks.addGroundKernel(new GroundBiasKernel(atomManager.getAtom(predicate, args), bias));
		}
	}

	@Override
	public Weight getWeight() {
		return weight;
	}

	@Override
	public void setWeight(Weight w) {
		if (!weightMutable)
			throw new IllegalStateException("Weight is not mutable.");
		
		if (w instanceof PositiveWeight)
			weight = (PositiveWeight) w;
		else
			throw new IllegalArgumentException("Weight must be instance of PositiveWeight.");
	}

	@Override
	public boolean isWeightMutable() {
		return weightMutable;
	}

	@Override
	public void setWeightMutable(boolean mutable) {
		weightMutable = mutable;
	}

	@Override
	protected void notifyAtomEvent(AtomEvent event, GroundKernelStore gks) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void registerForAtomEvents(AtomEventFramework eventFramework) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void unregisterForAtomEvents(AtomEventFramework eventFramework) {
		throw new UnsupportedOperationException();
	}
	
	private class GroundBiasKernel implements GroundCompatibilityKernel {
		
		private final GroundAtom atom;
		private final double bias;
		
		private GroundBiasKernel(GroundAtom atom, double bias) {
			this.atom = atom;
			this.bias = bias;
			atom.registerGroundKernel(this);
		}

		@Override
		public boolean updateParameters() {
			return false;
		}

		@Override
		public Set<GroundAtom> getAtoms() {
			HashSet<GroundAtom> atoms = new HashSet<GroundAtom>();
			atoms.add(atom);
			return atoms;
		}

		@Override
		public BindingMode getBinding(Atom atom) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CompatibilityKernel getKernel() {
			return BiasKernel.this;
		}

		@Override
		public Weight getWeight() {
			return new PositiveWeight(getKernel().getWeight().getWeight() * Math.abs(bias));
		}

		@Override
		public void setWeight(Weight w) {
			throw new UnsupportedOperationException();
		}

		@Override
		public FunctionTerm getFunctionDefinition() {
			FunctionSum f = new FunctionSum();
			if (bias > 0) {
				f.add(new FunctionSummand(1.0, new ConstantNumber(1.0)));
				f.add(new FunctionSummand(-1.0, atom.getVariable()));
			}
			else if (bias < 0) {
				f.add(new FunctionSummand(1.0, atom.getVariable()));
			}
			else {
				f.add(new FunctionSummand(0.0, new ConstantNumber(0.0)));
			}
			
			return f;
		}

		@Override
		public double getIncompatibility() {
			return getFunctionDefinition().getValue();
		}
		
	}

}
