package edu.umd.cs.bachaistats15;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore
import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore
import edu.umd.cs.psl.application.inference.MPEInference
import edu.umd.cs.psl.application.util.GroundKernels
import edu.umd.cs.psl.application.util.Grounding
import edu.umd.cs.psl.config.ConfigBundle
import edu.umd.cs.psl.config.ConfigManager
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Partition
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.groovy.PSLModel
import edu.umd.cs.psl.model.argument.ArgumentType
import edu.umd.cs.psl.model.atom.GroundAtom
import edu.umd.cs.psl.model.atom.RandomVariableAtom
import edu.umd.cs.psl.model.atom.SimpleAtomManager
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.rule.GroundCompatibilityRule;
import edu.umd.cs.psl.model.parameters.PositiveWeight
import edu.umd.cs.psl.reasoner.ExecutableReasoner
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory
import edu.umd.cs.psl.reasoner.bool.BooleanMaxWalkSatFactory
import edu.umd.cs.psl.reasoner.bool.UAIFormatReasonerFactory
import edu.umd.cs.psl.ui.loading.InserterLookupMap
import edu.umd.cs.psl.ui.loading.InserterUtils
import edu.umd.cs.psl.util.database.Queries

/*
 * Reads in settings
 */
String method = args[0];
String size = args[1];
String number = args[2];

/*
 * Initializes DataStore, ConfigBundle, and PSLModel
 */
Logger log = LoggerFactory.getLogger(this.class)

ConfigManager cm = ConfigManager.getManager()
ConfigBundle cb = cm.getBundle("solvempe")
//cb.setProperty("rdbmsdatastore.usestringids", true)

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = cb.getString("dbpath", defaultPath + File.separator + "solvempe")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), cb)

PSLModel m = new PSLModel(this, data)

/*
 * Defines Predicates
 */

m.add predicate: "bias" , types: [ArgumentType.UniqueID, ArgumentType.Double]

m.add predicate: "friends" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "upvoted" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "downvoted" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

m.add predicate: "votesDem", types: [ArgumentType.UniqueID]

/*
 * Defines model
 */

m.addKernel(new BiasKernel(votesDem, bias, new PositiveWeight(1.0)));

m.add rule : ( votesDem(A) & upvoted(B,A) ) >> votesDem(B),  weight : 1.0, squared: false
m.add rule : ( ~votesDem(A) & upvoted(B,A) ) >> ~votesDem(B),  weight : 1.0, squared: false

m.add rule : ( ~votesDem(A) & downvoted(B,A) ) >> votesDem(B),  weight : 1.0, squared: false
m.add rule : ( votesDem(A) & downvoted(B,A) ) >> ~votesDem(B),  weight : 1.0, squared: false

/*
 * Loads data
 */

Partition part = new Partition(0);

def file = 'data/socialnet-' + size + '-' + number + '.txt';
InserterLookupMap inserterLookup = new InserterLookupMap();
inserterLookup.put("anon1", data.getInserter(bias, part));
inserterLookup.put("friends", data.getInserter(friends, part));
inserterLookup.put("upvoted", data.getInserter(upvoted, part));
inserterLookup.put("downvoted", data.getInserter(downvoted, part));
InserterUtils.loadDelimitedMultiData(inserterLookup, 1, file);

rvDB = data.getDatabase(new Partition(0), [bias, friends, upvoted, downvoted] as Set);

/*
 * Populates target atoms
 */
for (GroundAtom regAtom : Queries.getAllAtoms(rvDB, bias)) {
	rvDB.getAtom(votesDem, regAtom.getArguments()[0]).commitToDB();
}

/*
 * Performs MPE inference
 */

if (method.equals("hlmrf")) {
	cb.setProperty(MPEInference.REASONER_KEY, new ADMMReasonerFactory());
	MPEInference mpe = new MPEInference(m, rvDB, cb)
	mpe.mpeInference()
	
	/* Re-grounds for rounding procedure */
	GroundKernelStore gks = new MemoryGroundKernelStore();
	Grounding.groundAll(m, new SimpleAtomManager(rvDB), gks);
	
	System.out.println("Current score: " + GroundKernels.getTotalWeightedCompatibility(gks.getCompatibilityKernels()));
	
	/* Collects and modifies RandomVariableAtoms */
	Set<RandomVariableAtom> rvs = new HashSet<RandomVariableAtom>();
	for (GroundAtom atom : Queries.getAllAtoms(rvDB, votesDem)) {
		if (atom instanceof RandomVariableAtom) {
			rvs.add((RandomVariableAtom) atom);
			((RandomVariableAtom) atom).setValue(0.25 + 0.5 * atom.getValue());
		}
	}
	
	System.out.println("Expected score: " + GroundKernels.getExpectedTotalWeightedCompatibility(gks.getCompatibilityKernels()));
	System.out.println("Number of variables: " + rvs.size());
	System.out.println("Number of clauses: " + gks.size());
	
	/* Performs greedy rounding */
	for (RandomVariableAtom rv : rvs) {
		int bestValue = -1;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int value = 0; value <= 1; value++) {
			rv.setValue(value);
			double score = 0.0;
			for (GroundKernel gk : rv.getRegisteredGroundKernels()) {
				if (gk instanceof GroundCompatibilityKernel)
					score += GroundKernels.getExpectedWeightedCompatibility((GroundCompatibilityKernel) gk);
			}
			if (score > bestScore) {
				bestValue = value;
				bestScore = score;
			}
		}
		rv.setValue(bestValue);
	}
	
	System.out.println("Final score: " + GroundKernels.getTotalWeightedCompatibility(gks.getCompatibilityKernels()));
}
else if (method.equals("mplp") || method.equals("mplp-cycles")) {
	cb.setProperty(MPEInference.REASONER_KEY, new UAIFormatReasonerFactory());
	if (method.equals("mplp"))
		cb.setProperty(ExecutableReasoner.EXECUTABLE_KEY, "solver-nocycles");
	else
		cb.setProperty(ExecutableReasoner.EXECUTABLE_KEY, "solver");
	MPEInference mpe = new MPEInference(m, rvDB, cb)
	mpe.mpeInference();
	
	/* Re-grounds for scoring */
	GroundKernelStore gks = new MemoryGroundKernelStore();
	Grounding.groundAll(m, new SimpleAtomManager(rvDB), gks);
	
	System.out.println("Final score: " + GroundKernels.getTotalWeightedCompatibility(gks.getCompatibilityKernels()));
	
}
else if (method.equals("maxwalksat")) {
	cb.setProperty(MPEInference.REASONER_KEY, new BooleanMaxWalkSatFactory());
	MPEInference mpe = new MPEInference(m, rvDB, cb)
	mpe.mpeInference();
	
	/* Re-grounds for scoring */
	GroundKernelStore gks = new MemoryGroundKernelStore();
	Grounding.groundAll(m, new SimpleAtomManager(rvDB), gks);
	
	System.out.println("Final score: " + GroundKernels.getTotalWeightedCompatibility(gks.getCompatibilityKernels()));
}
else if (method.equals("exact")) {
	
}
else
	throw new IllegalArgumentException("Unrecognized solver. Options are 'hlmrf', 'mplp', 'mplp-cycles, 'maxwalksat', or 'exact'");

rvDB.close();