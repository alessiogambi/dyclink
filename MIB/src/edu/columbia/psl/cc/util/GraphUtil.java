package edu.columbia.psl.cc.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.math3.util.MathUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.log4j.Logger;
import org.objectweb.asm.Opcodes;

import com.google.gson.reflect.TypeToken;
import com.sun.xml.internal.ws.util.StringUtils;

import edu.columbia.psl.cc.config.MIBConfiguration;
import edu.columbia.psl.cc.datastruct.BytecodeCategory;
import edu.columbia.psl.cc.datastruct.InstPool;
import edu.columbia.psl.cc.datastruct.VarPairPool;
import edu.columbia.psl.cc.datastruct.VarPool;
import edu.columbia.psl.cc.pojo.CostObj;
import edu.columbia.psl.cc.pojo.ExtObj;
import edu.columbia.psl.cc.pojo.GraphTemplate;
import edu.columbia.psl.cc.pojo.InstNode;
import edu.columbia.psl.cc.pojo.OpcodeObj;
import edu.columbia.psl.cc.pojo.SurrogateInst;
import edu.columbia.psl.cc.pojo.Var;
import edu.columbia.psl.cc.pojo.VarPair;

public class GraphUtil {
	
	private static Logger logger = Logger.getLogger(GraphUtil.class);
	
	/*public static ArrayList<String> replaceMethodKeyInParentList(ArrayList<String> parents, String methodKey, int cMethodInvokeId) {
		ArrayList<String> ret = new ArrayList<String>();
		
		for (String p: parents) {
			String[] pKeys = StringUtil.parseIdxKey(p);
			
			if (pKeys[0].equals(methodKey) && Integer.valueOf(pKeys[1]) == 0) {
				String newP = StringUtil.genIdxKey(pKeys[0], cMethodInvokeId, Integer.valueOf(pKeys[2]));
				ret.add(newP);
			} else {
				ret.add(p);
			}
		}
		
		return ret;
	}*/
	
	/*public static void setStaticMethodIdx(GraphTemplate graph, int cMethodInvokeId) {
		InstPool pool = graph.getInstPool();
		String methodKey = graph.getMethodKey();
		
		for (InstNode inst: pool) {
			if (inst.getFromMethod().equals(methodKey) && inst.getMethodId() == 0) {
				inst.setMethodId(cMethodInvokeId);
			}
			
			if (inst.getInstDataParentList().size() > 0)
				inst.setInstDataParentList(replaceMethodKeyInParentList(inst.getInstDataParentList(), methodKey, cMethodInvokeId));
			if (inst.getWriteDataParentList().size() > 0)
				inst.setWriteDataParentList(replaceMethodKeyInParentList(inst.getWriteDataParentList(), methodKey, cMethodInvokeId));
			if (inst.getControlParentList().size() > 0)
				inst.setControlParentList(replaceMethodKeyInParentList(inst.getControlParentList(), methodKey, cMethodInvokeId));
			
			if (inst.getChildFreqMap().size() > 0) {
				TreeMap<String, Double> newChildMap = new TreeMap<String, Double>();
				for (String childKey: inst.getChildFreqMap().keySet()) {
					double freq = inst.getChildFreqMap().get(childKey);
					
					String[] cKeys = StringUtil.parseIdxKey(childKey);
					if (cKeys[0].equals(methodKey) && Integer.valueOf(cKeys[1]) == 0) {
						String newKey = StringUtil.genIdxKey(cKeys[0], cMethodInvokeId, Integer.valueOf(cKeys[2]));
						newChildMap.put(newKey, freq);
					} else {
						newChildMap.put(childKey, freq);
					}
				}
				inst.setChildFreqMap(newChildMap);
			}
		}
	}*/
	
	public static List<InstNode> sortInstPool(Collection<InstNode> instCollection, boolean byStartTime) {
		Comparator<InstNode> comp = null;
		
		if (byStartTime) {
			comp = new Comparator<InstNode>() {
				@Override
				public int compare(InstNode i1, InstNode i2) {
					if (i1.getStartDigit() > i2.getStartDigit()) {
						return 1;
					} else if (i1.getStartDigit() < i2.getStartDigit()) {
						return -1;
					} else {
						if (i1.getStartTime() > i2.getStartTime()) {
							return 1;
						} else if (i1.getStartTime() < i2.getStartTime()) {
							return - 1;
						} else {
							//Impossible
							return 0;
						}
					}
				}
			};
		} else {
			comp = new Comparator<InstNode>() {
				
				@Override
				public int compare(InstNode i1, InstNode i2) {
					if (i1.getUpdateDigit() > i2.getUpdateDigit()) {
						return 1;
					} else if (i1.getUpdateDigit() < i2.getUpdateDigit()) {
						return -1;
					} else {
						if (i1.getUpdateTime() > i2.getUpdateTime()) {
							return 1;
						} else if (i1.getUpdateTime() < i2.getUpdateTime()) {
							return -1;
						} else {
							return 0;
						}
					}
				}
			};
		}
		
		List<InstNode> sortedList = new ArrayList<InstNode>(instCollection);
		Collections.sort(sortedList, comp);
		return sortedList;
	}
	
	public static InstNode lastSecondInst(InstPool instPool) {
		if (instPool.size() == 0)
			return null;
		
		List<InstNode> sortedList = sortInstPool(instPool, false);
		return sortedList.get(sortedList.size() - 1);
	}
	
	public static void upgradeTime(InstNode inst, 
			long baseDigit, 
			long baseTime, 
			boolean start) {
		long residue = Long.MAX_VALUE - inst.getStartTime();
		long val = baseTime - residue;
		long ten = baseDigit + inst.getStartDigit() + 1;
		if (start) {
			inst.setStartTime(val);
			inst.setStartDigit(ten);
		} else {
			inst.setUpdateTime(val);
			inst.setUpdateDigit(ten);
		}
	}
	
	public static long[] reindexInstPool(long[] base, InstPool instPool, boolean updateAll) {
		long maxUpdateDigit = base[1];
		long maxUpdateTime = base[0];
		
		long baseDigit = base[1];
		long baseTime = base[0];
		for (InstNode inst: instPool) {
			if (updateAll) {
				if ((inst.getStartTime() + baseTime) < 0) {
					//Means that long is not enough
					upgradeTime(inst, baseDigit, baseTime, true);
				} else {
					inst.setStartTime(baseTime + inst.getStartTime());
					inst.setStartDigit(baseDigit + inst.getStartDigit());
				}
				
				if ((inst.getUpdateTime() + baseTime) < 0) {
					upgradeTime(inst, baseDigit, baseTime, false);
				} else {
					inst.setUpdateTime(baseTime + inst.getUpdateTime());
					inst.setUpdateDigit(baseDigit + inst.getUpdateDigit());
				}
			} else {
				//Just increment by one
				inst.setUpdateTime(baseTime);
				inst.setUpdateDigit(baseDigit);
				
				if (baseTime + 1 < 0) {
					baseTime = 0;
					baseDigit++;
				} else {
					baseTime++;
				}
			}
			
			if (inst.getUpdateDigit() > maxUpdateDigit) {
				maxUpdateDigit = inst.getUpdateDigit();
				maxUpdateTime = inst.getUpdateTime();
			} else if (inst.getUpdateTime() > maxUpdateTime) {
				maxUpdateTime = inst.getUpdateTime();
			}
		}
		
		long[] ret = new long[2];
		if (maxUpdateTime + 1 < 0) {
			ret[0] = 0;
			ret[1] = maxUpdateDigit + 1;
		} else {
			ret[0] = maxUpdateTime + 1;
			ret[1] = maxUpdateDigit;
		}
		return ret;
	}
	/**
	 * 0 fromMethod, 1 threadId, 2 threadMethodId, 3 instId
	 * @param parentKey
	 * @param pool
	 * @param removeKey
	 */
	private static void _parentRemove(String parentKey, InstPool pool, String removeKey) {
		try {
			String[] parentParsed = StringUtil.parseIdxKey(parentKey);
			InstNode inst = pool.searchAndGet(parentParsed[0], 
					Long.valueOf(parentParsed[1]), 
					Integer.valueOf(parentParsed[2]), 
					Integer.valueOf(parentParsed[3]));
			
			inst.getChildFreqMap().remove(removeKey);
		} catch (Exception ex) {
			logger.error(ex);
			logger.error("Parent: " + parentKey);
			logger.error("Child:" + removeKey);
		}
	}
	
	public static InstNode _retrieveRealInst(String instKey, InstPool pool) {
		String[] instKeys = StringUtil.parseIdxKey(instKey);
		InstNode instNode = pool.searchAndGet(instKeys[0], 
				Long.valueOf(instKeys[1]), 
				Integer.valueOf(instKeys[2]), 
				Integer.valueOf(instKeys[3]));
		
		return instNode;
	}
	
	public static void parentRemove(InstNode inst, InstPool pool, String instKey) {
		//Remove from inst data parent if any
		for (String dp: inst.getInstDataParentList()) {
			_parentRemove(dp, pool, instKey);
		}
		
		//Remove from write data parent if any
		for (String dp: inst.getWriteDataParentList()) {
			_parentRemove(dp, pool, instKey);
		}
		
		//Remove from control parent if any
		for (String cp: inst.getControlParentList()) {
			_parentRemove(cp, pool, instKey);
		}
	}
	
	public static void transplantCalleeDepToCaller(InstNode parentNode, 
			InstNode childNode, 
			InstPool childPool) {
		String fInstKey = StringUtil.genIdxKey(childNode.getFromMethod(), childNode.getThreadId(), childNode.getThreadMethodIdx(), childNode.getIdx());
		
		for (String c: childNode.getChildFreqMap().keySet()) {
			double freq = childNode.getChildFreqMap().get(c);
			String[] keySet = StringUtil.parseIdxKey(c);
			long cThreadId = Long.valueOf(keySet[1]);
			int cThreadMethodId = Integer.valueOf(keySet[2]);
			int cIdx= Integer.valueOf(keySet[3]);
			parentNode.increChild(keySet[0], cThreadId, cThreadMethodId, cIdx, freq);
			
			InstNode cNode = childPool.searchAndGet(keySet[0], cThreadId, cThreadMethodId, cIdx);
			//Try to remove in either inst data or write data parent
			boolean fromInstData = cNode.getInstDataParentList().remove(fInstKey);
			boolean fromWriteData = false;
			if (!fromInstData)
				fromWriteData = cNode.getWriteDataParentList().remove(fInstKey);
			
			if (fromInstData && fromWriteData)
				System.out.println("Warning: double data deps: " + fInstKey);
			
			if (parentNode != null) {
				if (fromInstData) {
					cNode.registerParent(parentNode.getFromMethod(), 
							parentNode.getThreadId(), 
							parentNode.getThreadMethodIdx(), 
							parentNode.getIdx(), 
							MIBConfiguration.INST_DATA_DEP);
				} else {
					cNode.registerParent(parentNode.getFromMethod(), 
							parentNode.getThreadId(), 
							parentNode.getThreadMethodIdx(), 
							parentNode.getIdx(), 
							MIBConfiguration.WRITE_DATA_DEP);
				}
			}
		}
		
		System.out.println("Child node: " + childNode);
		System.out.println("Child node control parent: " + childNode.getControlParentList());
		for (String cont: childNode.getControlParentList()) {
			String[] keySet = StringUtil.parseIdxKey(cont);
			long cThreadId = Long.valueOf(keySet[1]);
			int cThreadMethodId = Integer.valueOf(keySet[2]);
			int cIdx = Integer.valueOf(keySet[3]);
			InstNode contNode = childPool.searchAndGet(keySet[0], cThreadId, cThreadMethodId, cIdx);
			System.out.println("Cont node: " + contNode);
			System.out.println("Cont node child: " + contNode.getChildFreqMap());
			double freq = contNode.getChildFreqMap().get(fInstKey);
			
			if (parentNode != null) {
				contNode.increChild(parentNode.getFromMethod(), parentNode.getThreadId(), parentNode.getThreadMethodIdx(), parentNode.getIdx(), freq);
				parentNode.registerParent(contNode.getFromMethod(), contNode.getThreadId(), contNode.getThreadMethodIdx(), contNode.getIdx(), MIBConfiguration.CONTR_DEP);
			}
		}
		
		//Remove these first read local vars from child pool, 
		//if there is corresponding parent in parent pool
		parentRemove(childNode, childPool, StringUtil.genIdxKey(childNode.getFromMethod(), childNode.getThreadId(), childNode.getThreadMethodIdx(), childNode.getIdx()));
		childPool.remove(childNode);
	}
	
	public static HashSet<InstNode> retrieveChildInsts(InstNode inst, InstPool pool) {
		HashSet<InstNode> allChildren = new HashSet<InstNode>();
		for (String cKey: inst.getChildFreqMap().keySet()) {
			InstNode cNode = _retrieveRealInst(cKey, pool);
			
			if (cNode != null)
				allChildren.add(cNode);
		}
		return allChildren;
	}
	
	/**
	 * For constructing surrogate branch, only inst data parents are required
	 * @param inst
	 * @param pool
	 * @param forSurrogate
	 * @return
	 */
	public static HashSet<InstNode> retrieveRequiredParentInsts(InstNode inst, InstPool pool, int depType) {
		HashSet<InstNode> allParents = new HashSet<InstNode>();
		if (depType == MIBConfiguration.CONTR_DEP) {
			for (String cPParent: inst.getControlParentList()) {
				InstNode cpNode = _retrieveRealInst(cPParent, pool);
				if (cpNode != null)
					allParents.add(cpNode);
			}
		} else if (depType == MIBConfiguration.INST_DATA_DEP) {
			for (String dPParent: inst.getInstDataParentList()) {
				InstNode ppNode = _retrieveRealInst(dPParent, pool);
				if (ppNode != null)
					allParents.add(ppNode);
			}
		} else if (depType == MIBConfiguration.WRITE_DATA_DEP) {
			for (String dPParent: inst.getWriteDataParentList()) {
				InstNode ppNode = _retrieveRealInst(dPParent, pool);
				if (ppNode != null)
					allParents.add(ppNode);
			}
		}
		
		return allParents;
	}
	
	public static HashSet<InstNode> retrieveAllParentInsts(InstNode inst, InstPool pool) {
		HashSet<InstNode> ret = new HashSet<InstNode>();
		ret.addAll(retrieveRequiredParentInsts(inst, pool, MIBConfiguration.CONTR_DEP));
		ret.addAll(retrieveRequiredParentInsts(inst, pool, MIBConfiguration.INST_DATA_DEP));
		ret.addAll(retrieveRequiredParentInsts(inst, pool, MIBConfiguration.WRITE_DATA_DEP));
		return ret;
	}
	
	/*private static SurrogateInst generateSurrogate(InstNode inst, InstPool pool) {
		//possible read: xload, getfield, getstatic, all method calls from jvm
		String instKey = StringUtil.genIdxKey(inst.getFromMethod(), inst.getThreadId(), inst.getThreadMethodIdx(), inst.getIdx());
		
		//For the first surrogate, don't copy parent for it. It's for storing children in method
		boolean shouldCopyParent = true;
		SurrogateInst newSur = new SurrogateInst(inst);
		if (inst.probeSurrogate() == 0) {
			inst.setMaxSurrogate((inst.getIdx() + 1 ) * MIBConfiguration.getInstance().getIdxExpandFactor());
			shouldCopyParent = false;
			
			newSur.getInstDataParentList().clear();
			newSur.getWriteDataParentList().clear();
			newSur.getControlParentList().clear();
			newSur.getChildFreqMap().clear();
			newSur.setIdx(inst.getIdx());
		} else {
			newSur.setIdx(inst.getMaxSurrogate());
			newSur.getInstDataParentList().clear();
			pool.add(newSur);
		}
		
		inst.addSurrogateInst(newSur);
		
		if (shouldCopyParent) {
			HashSet<InstNode> controlSet = retrieveRequiredParentInsts(inst, pool, MIBConfiguration.CONTR_DEP);
			for (InstNode instNode: controlSet) {
				double freq = instNode.getChildFreqMap().get(instKey);
				instNode.increChild(newSur.getFromMethod(), newSur.getThreadId(), newSur.getThreadMethodIdx(), newSur.getIdx(), freq);
				newSur.registerParent(instNode.getFromMethod(), instNode.getThreadId(), instNode.getThreadMethodIdx(), instNode.getIdx(), MIBConfiguration.CONTR_DEP);
			}
			
			HashSet<InstNode> writeSet = retrieveRequiredParentInsts(inst, pool, MIBConfiguration.WRITE_DATA_DEP);
			for (InstNode instNode: writeSet) {
				double freq = instNode.getChildFreqMap().get(instKey);
				instNode.increChild(newSur.getFromMethod(), newSur.getThreadId(), newSur.getThreadMethodIdx(), newSur.getIdx(), freq);
				newSur.registerParent(instNode.getFromMethod(), instNode.getThreadId(), inst.getThreadMethodIdx(), instNode.getIdx(), MIBConfiguration.WRITE_DATA_DEP);
			}
			
			HashSet<InstNode> instParents = retrieveRequiredParentInsts(inst, pool, MIBConfiguration.INST_DATA_DEP);
			for (InstNode instParent: instParents) {
				SurrogateInst surParent = generateSurrogate(instParent, pool);
				
				surParent.increChild(newSur.getFromMethod(), newSur.getThreadId(), newSur.getThreadMethodIdx(), newSur.getIdx(), MIBConfiguration.getInstance().getInstDataWeight());
				newSur.registerParent(surParent.getFromMethod(), surParent.getThreadId(), surParent.getThreadMethodIdx(), surParent.getIdx(), MIBConfiguration.INST_DATA_DEP);
			}
		}
		
		return newSur;
	}*/
	
	public static void multiplyGraph(GraphTemplate g, int times) {
		for (InstNode inst: g.getInstPool()) {
			TreeMap<String, Double> childMap = inst.getChildFreqMap();
			
			for (String cKey: childMap.keySet()) {
				double val = childMap.get(cKey) * times;
				childMap.put(cKey, val);
			}
		}
	}
	
	public static InstNode searchSimilarInst(InstNode inst, InstPool toSearch) {
		for (InstNode i: toSearch) {
			if (i.getFromMethod().equals(inst.getFromMethod()) 
					&& i.getIdx() == inst.getIdx()) {
				return i;
			}
		}
		
		return null;
	}
	
	public static void mapVertices(HashSet<InstNode> toMap, 
			InstPool targetPool, 
			HashMap<InstNode, InstNode> instMapping) {
		for (InstNode i: toMap) {
			InstNode mapped = searchSimilarInst(i, targetPool);
			
			if (mapped != null) {
				instMapping.put(i, mapped);
			} else {
				instMapping.put(i, i);
			}
		}
	}
	
	public static void doMerge(InstNode app, InstPool targetPool, InstPool appendPool, int depType) {
		List<String> parentList = null;
		
		if (depType == MIBConfiguration.CONTR_DEP) {
			parentList = app.getControlParentList();
		} else if (depType == MIBConfiguration.INST_DATA_DEP) {
			parentList = app.getInstDataParentList();
		} else if (depType == MIBConfiguration.WRITE_DATA_DEP) {
			parentList = app.getWriteDataParentList();
		}
		
		if (parentList == null || parentList.size() == 0)
			return ;
		
		//Map self
		InstNode mapApp = searchSimilarInst(app, targetPool);
				
		String appKey = StringUtil.genIdxKey(app.getFromMethod(), 
				app.getThreadId(), 
				app.getThreadMethodIdx(), 
				app.getIdx());
		
		List<String> toRemove = new ArrayList<String>();
		HashSet<InstNode> toAdd = new HashSet<InstNode>();
		if (mapApp == null) {
			toAdd.add(app);
		}
		
		for (String cpKey: parentList) {
			InstNode parentNode = _retrieveRealInst(cpKey, appendPool);
			if (mapApp != null) {
				//Null parent is from parent (data dep interface, control dep, field written by parent)
				if (parentNode != null) {
					double amount = parentNode.getChildFreqMap().get(appKey);
					InstNode mapParent = searchSimilarInst(parentNode, targetPool);
					
					if (mapParent != null) {
						//Parent=>Mapped parent, append=> mapped append
						mapParent.increChild(mapApp.getFromMethod(), 
								mapApp.getThreadId(), 
								mapApp.getThreadMethodIdx(), 
								mapApp.getIdx(), amount);
						mapApp.registerParent(mapParent.getFromMethod(), 
								mapParent.getThreadId(), 
								mapParent.getThreadMethodIdx(), 
								mapParent.getIdx(), depType);
					} else {
						//Parent no matched in target, append=> mapped append
						parentNode.increChild(mapApp.getFromMethod(), 
								mapApp.getThreadId(), 
								mapApp.getThreadMethodIdx(), 
								mapApp.getIdx(), amount);
						mapApp.registerParent(parentNode.getFromMethod(), 
								parentNode.getThreadId(), 
								parentNode.getThreadMethodIdx(), 
								parentNode.getIdx(), depType);
						parentNode.getChildFreqMap().remove(appKey);
						
						//Add parent into target pool
						toAdd.add(parentNode);
					}
				}
			} else {
				if (parentNode != null) {
					double amount = parentNode.getChildFreqMap().get(appKey);
					InstNode mapParent = searchSimilarInst(parentNode, targetPool);
					
					if (mapParent != null) {
						//Parent=> Mapped parnet, append not matched
						mapParent.increChild(app.getFromMethod(), 
								app.getThreadId(), 
								app.getThreadMethodIdx(), 
								app.getIdx(), amount);
						app.registerParent(mapParent.getFromMethod(), 
								mapParent.getThreadId(), 
								mapParent.getThreadMethodIdx(), 
								mapParent.getIdx(), depType);
						
						//Remove the original parent here
						toRemove.add(cpKey);
					} else {
						//Parent no matched, append no matched
						//Add parent into target pool
						toAdd.add(parentNode);
					}
				}
			}	
		}
		
		for (String p: toRemove) {
			parentList.remove(p);
		}
		
		for (InstNode inst: toAdd) {
			targetPool.add(inst);
		}
		
	}
	
	public static void mergeGraph(GraphTemplate target, GraphTemplate toAppend) {		
		InstPool targetPool = toAppend.getInstPool();
		InstPool appendPool = toAppend.getInstPool();
		
		for (InstNode app: appendPool) {
			doMerge(app, targetPool, appendPool, MIBConfiguration.CONTR_DEP);
			doMerge(app, targetPool, appendPool, MIBConfiguration.INST_DATA_DEP);
			doMerge(app, targetPool, appendPool, MIBConfiguration.WRITE_DATA_DEP);
		}
	}
		
	public static void dataDepFromParentToChild(Map<Integer, InstNode> parentMap, 
			InstPool parentPool,
			GraphTemplate childGraph) {
		InstPool childPool = childGraph.getInstPool();
		HashSet<InstNode> firstReadLocalVars = childGraph.getFirstReadLocalVars();
		HashMap<Integer, List<InstNode>> childSummary = new HashMap<Integer, List<InstNode>>();
		for (InstNode f: firstReadLocalVars) {
			//InstNode fInst = childPool.searchAndGet(f.getFromMethod(), f.getMethodId(), f.getIdx());
			//It's possible that fInst is null, probably an aload for method node, which should be removed
			if (f == null)
				continue ;
			
			int idx = Integer.valueOf(f.getAddInfo());
			
			if (parentMap.containsKey(idx)) {
				if (childSummary.containsKey(idx)) {
					childSummary.get(idx).add(f);
				} else {
					List<InstNode> insts = new ArrayList<InstNode>();
					insts.add(f);
					childSummary.put(idx, insts);
				}
			}
		}
		
		for (Integer varKey: childSummary.keySet()) {
			List<InstNode> childInsts = childSummary.get(varKey);
			//childInsts = sortInstPool(childInsts, true);
			InstNode parentNode = parentMap.get(varKey);
			//InstNode childNode = childInsts.get(0);
			//transplantCalleeDepToCaller(parentNode, childNode, childPool);
			
			//Only connect input node from parent method to child method
			for (InstNode childInst: childInsts) {
				parentNode.increChild(childInst.getFromMethod(), 
						childInst.getThreadId(), 
						childInst.getThreadMethodIdx(), 
						childInst.getIdx(), 
						MIBConfiguration.getInstance().getInstDataWeight());
				childInst.registerParent(parentNode.getFromMethod(), 
						parentNode.getThreadId(), 
						parentNode.getThreadMethodIdx(), 
						parentNode.getIdx(), 
						MIBConfiguration.INST_DATA_DEP);
			}
		}
	}
	
	/**
	 * This step is done right before dumping graph
	 * Transplant the surrogate to the real inst in pool
	 * @param pool
	 */
	/*public static void transplantFirstSurrogate(InstPool pool) {
		for (InstNode inst: pool) {
			if (inst.getSurrogateInsts().size() == 0)
				continue ;
			
			for (SurrogateInst si: inst.getSurrogateInsts()) {
				if (si.getIdx() == inst.getIdx()) {					
					//This si is only possible have control parent and data child (inst)
					for (String childInst: si.getChildFreqMap().keySet()) {
						double freq = si.getChildFreqMap().get(childInst);
						String[] cDecomp = StringUtil.parseIdxKey(childInst);
						inst.increChild(cDecomp[0], Long.valueOf(cDecomp[1]), Integer.valueOf(cDecomp[2]), Integer.valueOf(cDecomp[3]), freq);
					}
					
					for (String controlP: si.getControlParentList()) {
						String[] conDecomp = StringUtil.parseIdxKey(controlP);
						inst.registerParent(conDecomp[0], Long.valueOf(conDecomp[1]), Integer.valueOf(conDecomp[2]),Integer.valueOf(conDecomp[3]), MIBConfiguration.CONTR_DEP);
					}
					
					//Almost impossible to have write data dep
					for (String writeP: si.getWriteDataParentList()) {
						String[] writeDecomp = StringUtil.parseIdxKey(writeP);
						inst.registerParent(writeDecomp[0], Long.valueOf(writeDecomp[1]), Integer.valueOf(writeDecomp[2]), Integer.valueOf(writeDecomp[3]), MIBConfiguration.WRITE_DATA_DEP);
					}
				}
			}
		}
	}*/
	
	/*public static void fieldDataDepFromParentToChild(Map<String, InstNode> parentMap, 
			GraphTemplate childGraph) {
		
		Map<String, HashSet<InstNode>> firstReadFields = childGraph.getFirstReadFields();
		HashSet<String> shouldRemove = new HashSet<String>();
		for (String childKey: firstReadFields.keySet()) {
			if (parentMap.containsKey(childKey)) {
				HashSet<InstNode> childSet = childGraph.getFirstReadFields().get(childKey);
				InstNode parentNode = parentMap.get(childKey);
				for (InstNode fInst: childSet) {
					parentNode.increChild(fInst.getFromMethod(),
							fInst.getThreadId(), 
							fInst.getThreadMethodIdx(), 
							fInst.getIdx(), 
							MIBConfiguration.getInstance().getWriteDataWeight());
					fInst.registerParent(parentNode.getFromMethod(), 
							parentNode.getThreadId(), 
							parentNode.getThreadMethodIdx(), 
							parentNode.getIdx(), 
							MIBConfiguration.WRITE_DATA_DEP);
				}
				shouldRemove.add(childKey);
			}
		}
		
		for (String s: shouldRemove) {
			firstReadFields.remove(s);
		}
	}*/
	
	/*public static void fieldDataDepFromParentInstToChildGraph(Map<String, InstNode> parentMap, 
			InstNode childMethodInst, 
			GraphTemplate childGraph) {		
		Map<String, HashSet<InstNode>> firstReadFields = childGraph.getFirstReadFields();
		HashSet<String> shouldRemove = new HashSet<String>();
		for (String childKey: firstReadFields.keySet()) {
			if (parentMap.containsKey(childKey)) {
				InstNode parentNode = parentMap.get(childKey);
				
				//Don't use inst, use the chidlMethodInst
				parentNode.increChild(childMethodInst.getFromMethod(), 
						childMethodInst.getThreadId(), 
						childMethodInst.getThreadMethodIdx(), 
						childMethodInst.getIdx(), 
						MIBConfiguration.getInstance().getWriteDataWeight());
				childMethodInst.registerParent(parentNode.getFromMethod(), 
						parentNode.getThreadId(), 
						parentNode.getThreadMethodIdx(), 
						parentNode.getIdx(), 
						MIBConfiguration.WRITE_DATA_DEP);
				
				shouldRemove.add(childKey);
			}
		}
		
		for (String s: shouldRemove) {
			firstReadFields.remove(s);
		}
	}*/
	
	public static void controlDepFromParentToChild(HashSet<InstNode> controlFromParent, InstPool childPool) {
		for (InstNode condFromParent: controlFromParent) {
			controlDepFromParentToChild(condFromParent, childPool);
		}
	}
	
	public static void controlDepFromParentToChild(InstNode condFromParent, Collection<InstNode> childSet) {
		for (InstNode childNode: childSet) {
			condFromParent.increChild(childNode.getFromMethod(), 
					childNode.getThreadId(), 
					childNode.getThreadMethodIdx(), 
					childNode.getIdx(), 
					MIBConfiguration.getInstance().getControlWeight());
			childNode.registerParent(condFromParent.getFromMethod(), 
					condFromParent.getThreadId(), 
					condFromParent.getThreadMethodIdx(), 
					condFromParent.getIdx(), 
					MIBConfiguration.CONTR_DEP);
		}
	}
	
	public static void removeReturnInst(InstPool pool) {
		Iterator<InstNode> poolIt = pool.iterator();
		InstNode returnInst = null;
		while (poolIt.hasNext()) {
			InstNode inst = poolIt.next();
			if (BytecodeCategory.returnOps().contains(inst.getOp().getOpcode())) {
				returnInst = inst;
			}
		}
		String returnInstKey = StringUtil.genIdxKey(returnInst.getFromMethod(), returnInst.getThreadId(), returnInst.getThreadMethodIdx(), returnInst.getIdx());
		parentRemove(returnInst, pool, returnInstKey);
		pool.remove(returnInst);
	}
	
	public static <T> ArrayList<T> unionList(ArrayList<T> c1, ArrayList<T> c2) {
		ArrayList<T> ret = new ArrayList<T>();
		
		for (T t: c1) {
			if (!ret.contains(t)) {
				ret.add(t);
			}
		}
		
		for (T t: c2) {
			if (!ret.contains(t)) {
				ret.add(t);
			}
		}
		
		return ret;
	}
	
	public static void unionInst(InstNode parentNode, InstNode childNode) {		
		//Union their parents first;
		ArrayList<String> unionInstParent = unionList(parentNode.getInstDataParentList(), 
				childNode.getInstDataParentList());
		
		ArrayList<String> unionWriteParent = unionList(parentNode.getWriteDataParentList(), 
				childNode.getWriteDataParentList());
		
		ArrayList<String> unionControlParent = unionList(parentNode.getControlParentList(), 
				childNode.getControlParentList());
		
		//Union their child, don't accumulate. Use the latest from child
		TreeMap<String, Double> unionChildren = new TreeMap<String, Double>(parentNode.getChildFreqMap());
		unionChildren.putAll(childNode.getChildFreqMap());
		
		parentNode.setInstDataParentList(unionInstParent);
		parentNode.setWriteDataParentList(unionWriteParent);
		parentNode.setControlParentList(unionControlParent);
		parentNode.setChildFreqMap(unionChildren);
		
		childNode.setInstDataParentList(unionInstParent);
		childNode.setWriteDataParentList(unionWriteParent);
		childNode.setControlParentList(unionControlParent);
		childNode.setChildFreqMap(unionChildren);
	}
	
	public static void synchronizeInstPools(InstPool parentPool, InstPool childPool) {
		Iterator<InstNode> poolIt = childPool.iterator();
		while (poolIt.hasNext()) {
			InstNode childInst = poolIt.next();
			
			if (parentPool.contains(childInst)) {
				InstNode sameNode = parentPool.searchAndGet(childInst.getFromMethod(), 
						childInst.getThreadId(), 
						childInst.getThreadMethodIdx(), 
						childInst.getIdx());
				
				unionInst(sameNode, childInst);
			}
		}
	}
	
	public static void unionInstPools(InstPool parentPool, InstPool childPool) {
		Iterator<InstNode> poolIt = childPool.iterator();
		while (poolIt.hasNext()) {
			InstNode childInst = poolIt.next();
			parentPool.add(childInst);
		}
	}
	
	public static double roundValue(double value) {
		return Precision.round(value, MIBConfiguration.getInstance().getPrecisionDigit());
	}
		
	private static void summarizeVarPairChildren(VarPairPool vpp, VarPair vp) {
		HashMap<String, Set<Var>> v1Map = vp.getVar1().getChildren();
		HashMap<String, Set<Var>> v2Map = vp.getVar2().getChildren();
		
		//First pass to construct all relationship for this vp
		for (String s1: v1Map.keySet()) {
			Set<Var> v1Set = v1Map.get(s1);
			Set<Var> v2Set = v2Map.get(s1);
			
			if (v2Set == null || v2Set.size() == 0)
				continue;
			
			for (Var v1: v1Set) {
				for (Var v2: v2Set) {
					if (v1.equals(v2))
						continue;
					
					VarPair childVp = vpp.searchVarPairPool(v1, v2, true);
					System.out.println("Child vp: " + childVp);
					//Add childVp as child of vp, add vp as parent of childVp
					VarPairPool.addVarPair(s1, vp, childVp, true);
					VarPairPool.addVarPair(s1, vp, childVp, false);
				}
			}
		}
	}
	
	public static void constructVarPairPool(VarPairPool vpp, VarPool vPool1, VarPool vPool2) {
		//First pass to generate all var pair in the pool
		for (Var v1: vPool1) {
			for (Var v2: vPool2) {
				vpp.searchVarPairPool(v1, v2, true);
			}
		}
		
		//Second pass, create relationship between VarPair
		//Avoid cycle
		for (VarPair vp: vpp) {
			System.out.println("Current vp: " + vp);
			summarizeVarPairChildren(vpp, vp);
		}
		
		//Update child and parent coefficient map
		for (VarPair vp: vpp) {
			VarPairPool.updateCoefficientMap(vp);
		}
	}
	
	public static void main (String[] args) {
		BigDecimal bd = new BigDecimal(0.11111);
		System.out.println(bd.setScale(3, RoundingMode.HALF_UP));
		System.out.println(Precision.round(0.11111, 3));
		System.out.println(Precision.round(0.1115,3));
		Double d = new Double(5);
		int times = 4;
		
		double val = d * times;
		System.out.println("Check val: " + val);
		
	}

}
