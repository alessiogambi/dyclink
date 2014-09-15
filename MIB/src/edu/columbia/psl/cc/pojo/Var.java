package edu.columbia.psl.cc.pojo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

//For serialization purpose, cannot set Var as an abstract class
public class Var {
	
	//The class that this var is used
	private String className;
	
	//The method this var is used
	private String methodName;
	
	//static, instance or local
	protected int silId;
	
	private int opcode;
	

	//private HashSet<Var> children = new HashSet<Var>();	
	private HashMap<String, Set<Var>> children = new HashMap<String, Set<Var>>();
	
	public void setOpcode(int opcode) {
		this.opcode = opcode;
	}
	
	public int getOpcode() {
		return this.opcode;
	}
	
	public void setClassName(String className) {
		this.className = className;
	}
	
	public String getClassName() {
		return this.className;
	}
	
	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}
	
	public String getMethodName() {
		return this.methodName;
	}
	
	public void setSilId(int silId) {
		this.silId = silId;
	}
	
	public int getSilId() {
		return this.silId;
	};
	
	public String getSil() {
		if (this.silId == 0) {
			return "static";
		} else if (this.silId == 1) {
			return "instance";
		} else {
			return "local";
		}
	}
	
	public void addChildren(Var child) {
		String edge = this.getSil() + "-" + child.getSil();
		//this.children.put(child, edge);
		if (this.children.keySet().contains(edge)) {
			this.children.get(edge).add(child);
		} else {
			Set<Var> edgeSet = new HashSet<Var>();
			edgeSet.add(child);
			this.children.put(edge, edgeSet);
		}
	}
	
	public Set<Var> getChildrenWithLabel(String label) {
		return this.children.get(label);
	}
	
	public void setChildren(HashMap<String, Set<Var>>children) {
		this.children = children;
	}
	
	public HashMap<String, Set<Var>> getChildren() {
		return this.children;
	}
		
	public String getVarInfo() {
		if (this.silId < 2) {
			ObjVar ov = (ObjVar)this;
			return ov.getNativeClassName() + ":" + ov.getVarName();
		} else {
			LocalVar lv = (LocalVar)this;
			return String.valueOf(lv.getLocalVarId());
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Var))
			return false;
		
		Var tmpV = (Var)o;
		if (tmpV.getOpcode() != this.opcode)
			return false;
		
		if (!tmpV.getClassName().equals(this.className))
			return false;
		
		if (!tmpV.getMethodName().equals(this.methodName))
			return false;
		
		if (tmpV.getSilId() != this.silId)
			return false;
		
		if (!tmpV.getVarInfo().equals(this.getVarInfo()))
			return false;
		
		//Consider children?
		
		return true;
	}
	
	@Override
	public String toString() {
		return this.opcode + ":" + this.className + ":" + this.methodName + ":" + this.getSilId() + ":" + this.getVarInfo();
	}
	
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

}