package edu.columbia.psl.cc.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.collections15.Transformer;

import com.google.gson.reflect.TypeToken;

import edu.columbia.psl.cc.datastruct.InstPool;
import edu.columbia.psl.cc.pojo.GraphTemplate;
import edu.columbia.psl.cc.pojo.InstNode;
import edu.columbia.psl.cc.util.GraphUtil;
import edu.columbia.psl.cc.util.SearchUtil;
import edu.columbia.psl.cc.util.StringUtil;
import edu.columbia.psl.cc.util.TemplateLoader;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.algorithms.scoring.PageRankWithPriors;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Hypergraph;


public class PageRankSelector {
	
	private static double alpha = 0.15;
	
	private static int maxIteration = 10;
	
	private static double epsilon = 1e-5;
	
	private static int instLimit = 3000;
	
	private static Comparator<InstWrapper> pageRankSorter = new Comparator<InstWrapper>() {
		public int compare(InstWrapper i1, InstWrapper i2) {
			if (i1.pageRank < i2.pageRank) {
				return 1;
			} else if (i1.pageRank > i2.pageRank) {
				return -1;
			} else {
				if (i1.inst.getOp().getOpcode() > i2.inst.getOp().getOpcode()) {
					return 1;
				} else if (i1.inst.getOp().getOpcode() < i2.inst.getOp().getOpcode()) {
					return -1;
				} else {
					return 0;
				}
			}
		}
	};
	
	private static double levenSimilarity(int dist, int base) {
		double sim = 1 - ((double)dist/base);
		return sim;
	}
	
	private InstPool myPool;
	
	private HashMap<InstNode, Double> priors;
	
	private boolean partialPool;
	
	public PageRankSelector(InstPool myPool, boolean partialPool) {
		this.myPool = myPool;
		this.partialPool = partialPool;
	}
	
	/**
	 * Key is opcode, double is their prior
	 * @param priors
	 */
	public void setPriors(HashMap<InstNode, Double> priors) {
		this.priors = priors;
	}
	
	public DirectedSparseGraph<InstNode, Integer> convertToJungGraph() {
		DirectedSparseGraph<InstNode, Integer> retGraph = new DirectedSparseGraph<InstNode, Integer>();
		
		int edgeId = 0;
		HashMap<String, InstNode> cache = new HashMap<String, InstNode>();
		for (InstNode inst: myPool) {
			retGraph.addVertex(inst);
			
			for (String childKey: inst.getChildFreqMap().keySet()) {
				InstNode childNode = null;
				if (cache.containsKey(childKey)) {
					childNode = cache.get(childKey);
				} else {
					String[] keys = StringUtil.parseIdxKey(childKey);
					childNode = myPool.searchAndGet(keys[0], 
							Long.valueOf(keys[1]), Integer.valueOf(keys[2]), Integer.valueOf(keys[3]));
				}
				
				if (!partialPool)
					retGraph.addEdge(new Integer(edgeId++), inst, childNode);
				else {
					if (childNode != null)
						retGraph.addEdge(new Integer(edgeId++), inst, childNode);
				}
			}
		}
		
		return retGraph;
	}
	
	public List<InstWrapper> computePageRank() {
		Hypergraph<InstNode, Integer> jungGraph = this.convertToJungGraph();
		System.out.println("Vertex size: " + jungGraph.getVertexCount());
		System.out.println("Edge size: " + jungGraph.getEdgeCount());
		
		PageRankWithPriors<InstNode, Integer> ranker = null;
		if (this.priors == null) {
			System.out.println("Rank without priors");
			ranker = new PageRank<InstNode, Integer>(jungGraph, alpha);
		} else {
			System.out.println("Rank with priors");
			Transformer<InstNode, Double> transformer = new Transformer<InstNode, Double>() {
				@Override
				public Double transform(InstNode inst) {
					double prior = priors.get(inst);
					return prior;
				}
			};
			ranker = new PageRankWithPriors<InstNode, Integer>(jungGraph, transformer, alpha);
		}
		
		List<InstWrapper> rankList = new ArrayList<InstWrapper>();
		ranker.setMaxIterations(maxIteration);
		ranker.setTolerance(epsilon);
		ranker.evaluate();
		
		for (InstNode inst: jungGraph.getVertices()) {
			InstWrapper iw = new InstWrapper(inst, ranker.getVertexScore(inst));
			rankList.add(iw);
		}
		
		Collections.sort(rankList, pageRankSorter);
		return rankList;
	}
	
	public InstPool selectRepPool() {
		InstPool ret = new InstPool();
		List<InstWrapper> sorted = this.computePageRank();
		for (int i = 0; i < instLimit; i++) {
			ret.add(sorted.get(i).inst);
		}
		return ret;
	}
	
	public static HashMap<InstNode, InstPool> locateSegments(HashSet<InstNode> assignments, 
			List<InstNode> sortedTarget, 
			int before, 
			int after) {
		HashMap<InstNode, InstPool> candSegs = new HashMap<InstNode, InstPool>();
		for (InstNode inst: assignments) {
			InstPool seg = new InstPool();
			
			for (int i = 0; i < sortedTarget.size(); i++) {
				InstNode curNode = sortedTarget.get(i);
				if (curNode.equals(inst)) {
					//collect backward
					int start = i - before;
					if (start < 0)
						start = 0;
					
					int end = i + after;
					if (end > sortedTarget.size() - 1)
						end = sortedTarget.size() - 1;
					
					seg.addAll(sortedTarget.subList(start, end + 1));
					break ;
				}
			}
			candSegs.put(inst, seg);
		}
		return candSegs;
	}
	
	public static void subgraphSearch(GraphTemplate subGraph, GraphTemplate targetGraph) {
		List<InstNode> sortedSub = GraphUtil.sortInstPool(subGraph.getInstPool(), true);
		List<InstNode> sortedTarget = GraphUtil.sortInstPool(targetGraph.getInstPool(), true);
		
		int segSize = sortedSub.size();
		//Pick the most important node from sorteSob
		PageRankSelector subSelector = new PageRankSelector(subGraph.getInstPool(), false);
		List<InstWrapper> subRank = subSelector.computePageRank();
		int[] subPGRep = SearchUtil.generatePageRankRep(subRank);
		
		//Use the most important inst as the central to collect insts in target
		InstNode mi = subRank.get(0).inst;
		int before = 0, after = 0;
		boolean recordBefore = true;
		for (int i = 0; i < sortedSub.size(); i++) {
			InstNode curNode = sortedSub.get(i);
			
			if (curNode.equals(mi)) {
				recordBefore = false;
				continue ;
			}
			
			if (recordBefore) {
				before++;
			} else {
				after++;
			}
		}
		
		HashSet<InstNode> miAssignments = SearchUtil.possibleSingleAssignment(mi, targetGraph);
		HashMap<InstNode, InstPool> candSegs = locateSegments(miAssignments, sortedTarget, before, after);
		for (InstNode cand: candSegs.keySet()) {
			PageRankSelector ranker = new PageRankSelector(candSegs.get(cand), true);
			List<InstWrapper> ranks = ranker.computePageRank();
			int[] candPGRep = SearchUtil.generatePageRankRep(ranks);
			
			int dist = 0;
			if (candPGRep.length == 0) {
				dist = segSize;
			} else {
				dist = LevenshteinDistance.calculateSimilarity(subPGRep, candPGRep);
			}
			
			double sim = levenSimilarity(dist, segSize);
			
			System.out.println("Distance: " + dist);
			System.out.println("Similarity: " + sim);
		}
	}
				
	public static void main(String[] args) {
		//File f = new File("./template/cc.testbase.TemplateMethod:increArray:([I):V:1.json");
		File f = new File("./template/org.ejml.alg.dense.decomposition.bidiagonal.BidiagonalDecompositionRow_D64:_decompose:():Z:1.json");
		TypeToken<GraphTemplate> tt = new TypeToken<GraphTemplate>(){};
		GraphTemplate template = TemplateLoader.loadTemplateFile(f, tt);
		GraphUtil.removeReturnInst(template.getInstPool());
		System.out.println("Template name: " + f.getName());
		System.out.println("Inst node size: " + template.getInstPool().size());
		
		PageRankSelector templateSelect = new PageRankSelector(template.getInstPool(), false);
		List<InstWrapper> templateSorted = templateSelect.computePageRank();
		System.out.println("Template rank");
		int[] templatePGRep = SearchUtil.generatePageRankRep(templateSorted);
		
		int[] bytecodeFreq = SearchUtil.generateBytecodeFreq(template.getInstPool());
		System.out.println("Template bytecode freq: " + Arrays.toString(bytecodeFreq));
		
		double[] pgPrior = SearchUtil.generatePriorByPageRank(templateSorted);
		System.out.println("Template pg prior: " + Arrays.toString(pgPrior));
		
		//File f2 = new File("./test/cc.testbase.TemplateMethod:all3Methods:(II):I:1.json");
		File f2 = new File("./test/org.ejml.alg.dense.decomposition.bidiagonal.BidiagonalDecompositionRow_D64:decompose:(Lorg.ejml.data.DenseMatrix64F):Z:1.json");
		GraphTemplate test = TemplateLoader.loadTemplateFile(f2, tt);
		GraphUtil.removeReturnInst(test.getInstPool());
		System.out.println("Test name: " + f2.getName());
		System.out.println("Inst node size: " + test.getInstPool().size());
		
		PageRankSelector select = new PageRankSelector(test.getInstPool(), false);
		List<InstWrapper> prSorted = select.computePageRank();
		System.out.println("Show ranks");
		int count = 0;
		/*for (InstWrapper iw: prSorted) {
			System.out.println(iw.inst);
			System.out.println(count++ + " " + iw.pageRank);
		}*/
		
		/*PageRankSelector selectWithPrior = new PageRankSelector(test.getInstPool(), false);
		HashMap<InstNode, Double> priors = SearchUtil.redistribute(pgPrior, test.getInstPool());
		System.out.println("Priors: ");
		for (InstNode inst: priors.keySet()) {
			System.out.println("Inst: " + inst);
			System.out.println("Prior: " + priors.get(inst));
		}
		
		selectWithPrior.setPriors(priors);
		List<InstWrapper> prSortedWithPriors = selectWithPrior.computePageRank();
		System.out.println("Show ranks");
		count = 0;
		for (InstWrapper iw: prSortedWithPriors) {
			System.out.println(iw.inst);
			System.out.println(count++ + " " + iw.pageRank);
		}*/
		
		List<InstNode> sortedSub = GraphUtil.sortInstPool(template.getInstPool(), true);
		List<InstNode> sortedTest = GraphUtil.sortInstPool(test.getInstPool(), true);
		
		int segmentSize = sortedSub.size();
		HashSet<InstNode> possibleCands = SearchUtil.possibleSingleAssignment(sortedSub.get(0), test);
		System.out.println("Possible assignments: " + possibleCands);
		HashMap<InstNode, InstPool> candSegs = new HashMap<InstNode, InstPool>();
		for (InstNode cand: possibleCands) {
			InstPool seg = new InstPool();
			int remained = segmentSize;
			
			//Retrieve the segment
			boolean start = false;
			for (InstNode t: sortedTest) {
				if (t.equals(cand)) {
					start = true;
				}
				
				if (start) {
					seg.add(t);
					remained--;
					
					if (remained == 0)
						break ;
				}
			}
			start = false;
			candSegs.put(cand, seg);
		}
		
		//Generate pg rep
		for(InstNode cand: candSegs.keySet()) {
			PageRankSelector pgs = new PageRankSelector(candSegs.get(cand), true);
			List<InstWrapper> sortedResults = pgs.computePageRank();
			System.out.println("Cand rank: " + cand);
			int[] candPGRep = SearchUtil.generatePageRankRep(sortedResults);
			
			int dist = 0;
			if (candPGRep.length == 0) {
				dist = segmentSize;
			} else {
				dist = LevenshteinDistance.calculateSimilarity(templatePGRep, candPGRep);
			}
			
			double sim = levenSimilarity(dist, segmentSize);
			
			System.out.println("Distance: " + dist);
			System.out.println("Similarity: " + sim);
		}
	}
}
