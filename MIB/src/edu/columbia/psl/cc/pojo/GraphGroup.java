package edu.columbia.psl.cc.pojo;

import java.util.HashMap;

import org.apache.log4j.Logger;

public class GraphGroup extends HashMap<String, GraphTemplate>{
	
	private static Logger logger = Logger.getLogger(GraphGroup.class);
		
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static String groupKey(int linenumber, GraphTemplate graph) {
		return groupKey(linenumber, graph.getVertexNum(), graph.getEdgeNum());
	}
	
	public static String groupKey(int linenumber, int nodeNum, int depNum) {
		return String.valueOf(linenumber) + ":" + nodeNum + ":" + depNum;
	}
	
	public GraphTemplate getGraph(int linenumber, GraphTemplate graph) {
		if (graph.getLatestWriteFields().size() > 0) {
			logger.info("Graph writes fields. Choose not to replace: " + graph.getMethodKey() + " " + graph.getThreadId() + " " + graph.getThreadMethodId());
			return null;
		}
		
		String groupKey = groupKey(linenumber, graph.getVertexNum(), graph.getEdgeNum());
		GraphTemplate existGraph = this.get(groupKey);
		return existGraph;
		
		/*if (existGraph == null) {
			return null;
		} else {
			return existGraph;
		}*/
		
		/*if (existGraph == null) {
			return null;
		} else if (!existGraph.getLatestWriteFields().keySet().equals(graph.getLatestWriteFields().keySet())) {
			logger.info("Capture similar graph but write fields not matched");
			logger.info("Exist graph: " + existGraph.getLatestWriteFields().keySet());
			logger.info("Current graph: " + graph.getLatestWriteFields().keySet());
			return null;
		} else {
			return existGraph;
		}*/
	}

	public void addGraph(int linenumber, GraphTemplate graph) {
		String groupKey = groupKey(linenumber, graph.getVertexNum(), graph.getEdgeNum());
		this.put(groupKey, graph);
	}
}
