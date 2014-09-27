package edu.columbia.psl.cc.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import edu.columbia.psl.cc.datastruct.BytecodeCategory;
import edu.columbia.psl.cc.pojo.OpcodeObj;

public class MethodStackRecorder {
	
	private String curLabel = null;
	
	private AtomicInteger instCounter = null;

	private Stack<String> stackSimulator = new Stack<String>();
	
	//private List<String> controlVars = new ArrayList<String>();
	
	private String curControlVar = null;
	
	private Map<Integer, String> localVarRecorder = new HashMap<Integer, String>();
	
	private Map<String, List<String>> dataDep = new HashMap<String, List<String>>();
	
	private Map<String, List<String>> controlDep = new HashMap<String, List<String>>();
	
	private synchronized int getInstIdx(String label) {
		if (curLabel == null || !curLabel.equals(label)) {
			this.curLabel = label;
			this.instCounter = new AtomicInteger();
			return this.instCounter.getAndIncrement();
		}
		
		return this.instCounter.getAndIncrement();
	}
	
	private void updateCachedMap(String parent, String child, boolean isControl) {
		if (isControl) {
			if (this.controlDep.containsKey(parent)) {
				this.controlDep.get(parent).add(child);
			} else {
				List<String> children = new ArrayList<String>();
				children.add(child);
				this.controlDep.put(parent, children);
			}
		} else {
			if (this.dataDep.containsKey(parent)) {
				this.dataDep.get(parent).add(child);
			} else {
				List<String> children = new ArrayList<String>();
				children.add(child);
				this.dataDep.put(parent, children);
			}
		}
		System.out.println("Update map: " + this.dataDep);
	}
	
	private synchronized String safePop() {
		if (this.stackSimulator.size() > 0) {
			return this.stackSimulator.pop();
		}
		return null;
	}
	
	private synchronized void updateControlVar(String fullInst) {
		this.curControlVar = fullInst;
		
		/*if (this.controlVars.size() == 0) {
			this.controlVars.add(fullInst);
		} else {
			this.controlVars.remove(this.controlVars.size() - 1);
			this.controlVars.add(fullInst);
		}*/
	}
	
	public void handleOpcode(int opcode, String label, String addInfo) {
		OpcodeObj oo = BytecodeCategory.getOpcodeObj(opcode);
		int opcat = oo.getCatId();
		String fullInst = this.getInstIdx(label) + " " + oo.getInstruction() + " " + addInfo;
		
		this.updateControlRelation(fullInst);
		
		if (BytecodeCategory.controlCategory().contains(opcat)) {
			this.updateControlVar(fullInst);
		}
		
		int inputSize = oo.getInList().size();
		if (inputSize > 0) {
			for (int i = 0; i < inputSize; i++) {
				//Should not return null here
				String tmpInst = this.safePop();
				this.updateCachedMap(tmpInst, fullInst, false);
			}
		}
		this.updateStackSimulator(oo, fullInst);
	}
	
	/**
	 * localVarIdx is not necessarily a local var
	 * @param opcode
	 * @param localVarIdx
	 */
	public void handleOpcode(int opcode, String label, int localVarIdx) {
		System.out.println("Handling now: " + opcode + " " + localVarIdx);
		OpcodeObj oo =BytecodeCategory.getOpcodeObj(opcode);
		int opcat = oo.getCatId();
		
		String fullInst = this.getInstIdx(label) + " " + oo.getInstruction() + " ";
		if (localVarIdx >= 0) {
			fullInst += localVarIdx;
		}
		
		String lastInst = "";
		if (!stackSimulator.isEmpty()) {
			lastInst = stackSimulator.peek();
		}
		
		this.updateControlRelation(fullInst);
		
		System.out.println("Check lastInst: " + lastInst);
		//The store instruction will be the sink. The inst on the stack will be source
		boolean hasUpdate = false;
		if (BytecodeCategory.writeCategory().contains(opcat)) {
			if (lastInst.length() > 0) {
				System.out.println("Update data dep");
				if (localVarIdx >= 0)
					this.localVarRecorder.put(localVarIdx, fullInst);
				
				this.updateCachedMap(lastInst, fullInst, false);
				this.safePop();
			}
		} else if (opcode == Opcodes.IINC) {
			this.localVarRecorder.put(localVarIdx, fullInst);
		}else if (BytecodeCategory.readCategory().contains(opcat)) {
			//Search local var recorder;
			String parentInst = this.localVarRecorder.get(localVarIdx);
			if (parentInst != null)
				this.updateCachedMap(parentInst, fullInst, false);
		} else if (BytecodeCategory.dupCategory().contains(opcat)) {
			this.handleDup(opcode);
			hasUpdate = true;
		} else {
			if (BytecodeCategory.controlCategory().contains(opcat))
				this.updateControlVar(fullInst);
			
			int inputSize = oo.getInList().size();
			String lastTmp = "";
			if (inputSize > 0) {
				for (int i = 0; i < inputSize; i++) {
					//Should not return null here
					String tmpInst = this.safePop();
					if (!tmpInst.equals(lastTmp))
						this.updateCachedMap(tmpInst, fullInst, false);
					
					lastTmp = tmpInst;
				}
			}
		}
		
		if (!hasUpdate) 
			this.updateStackSimulator(oo, fullInst);
	}
	
	public void handleMultiNewArray(String desc, int dim, String label) {
		OpcodeObj oo = BytecodeCategory.getOpcodeObj(Opcodes.MULTIANEWARRAY);
		String fullInst = this.getInstIdx(label) + " " + oo.getInstruction() + " " + desc + " " + dim;
		
		this.updateControlRelation(fullInst);
		
		//Parse method type
		for (int i = 0; i < dim; i++) {
			String tmpInst = this.safePop();
			this.updateCachedMap(tmpInst, fullInst, false);
		}
		this.updateStackSimulator(oo, fullInst);
	}
	
	public void handleMethod(int opcode, String label, String owner, String name, String desc) {
		OpcodeObj oo = BytecodeCategory.getOpcodeObj(opcode);
		String fullInst = this.getInstIdx(label) + " " + oo.getInstruction() + " " + owner + " " + name + " " + desc;
		
		this.updateControlRelation(fullInst);
		
		Type methodType = Type.getMethodType(desc);
		int argSize = methodType.getArgumentTypes().length;
		String returnType = methodType.getReturnType().getDescriptor();
		for (int i = 0; i < argSize; i++) {
			String tmpInst = this.safePop();
			this.updateCachedMap(tmpInst, fullInst, false);
		}
		
		if (!returnType.equals("V")) {
			this.updateStackSimulator(1, fullInst);
		}
	}
	
	public void handleDup(int opcode) {
		String dupString = "";
		String dupString2 = "";
		Stack<String> stackBuf;
		switch (opcode) {
			case 89:
				dupString = this.stackSimulator.peek();
				this.stackSimulator.push(dupString);
				break ;
			case 90:
				dupString = this.stackSimulator.peek();
				stackBuf = new Stack<String>();
				for (int i = 0; i < 2; i++) {
					stackBuf.push(this.safePop());
				}
				
				this.stackSimulator.push(dupString);
				while(!stackBuf.isEmpty()) {
					this.stackSimulator.push(stackBuf.pop());
				}
				break ;
			case 91:
				dupString = this.stackSimulator.peek();
				stackBuf = new Stack<String>();
				for (int i = 0; i < 3; i++) {
					stackBuf.push(this.safePop());
				}
				
				this.stackSimulator.push(dupString);
				//Should only push three times
				while (!stackBuf.isEmpty()) {
					this.stackSimulator.push(stackBuf.pop());
				}
				break ;
			case 92:
				dupString = this.stackSimulator.get(this.stackSimulator.size() - 1);
	 			dupString2 = this.stackSimulator.get(this.stackSimulator.size() - 2);
	 			
	 			this.stackSimulator.push(dupString2);
	 			this.stackSimulator.push(dupString);
	 			break ;
			case 93:
				dupString = this.stackSimulator.get(this.stackSimulator.size() - 1);
	 			dupString2 = this.stackSimulator.get(this.stackSimulator.size() - 2);
	 			stackBuf = new Stack<String>();
	 			for (int i = 0; i < 3; i++) {
	 				stackBuf.push(this.safePop());
	 			}
	 			
	 			this.stackSimulator.push(dupString2);
	 			this.stackSimulator.push(dupString);
	 			while (!stackBuf.isEmpty()) {
	 				this.stackSimulator.push(stackBuf.pop());
	 			}
	 			break ;
			case 94:
				dupString = this.stackSimulator.get(this.stackSimulator.size() - 1);
	 			dupString2 = this.stackSimulator.get(this.stackSimulator.size() - 2);
	 			stackBuf = new Stack<String>();
	 			for (int i =0 ; i < 4; i++) {
	 				stackBuf.push(this.safePop());
	 			}
	 			
	 			this.stackSimulator.push(dupString2);
	 			this.stackSimulator.push(dupString);
	 			while (!stackBuf.isEmpty()) {
	 				this.stackSimulator.push(stackBuf.pop());
	 			}
	 			break ;
		}
	}
	
	private void updateStackSimulator(OpcodeObj oo, String fullInst) {
		int outputSize = oo.getOutList().size();
		this.updateStackSimulator(outputSize, fullInst);
	}
	
	private void updateStackSimulator(int times, String fullInst) {
		System.out.println("Stack push: " + fullInst);
		for (int i = 0; i < times; i++) {
			this.stackSimulator.push(fullInst);
		}
		System.out.println("Check data dep: " + this.dataDep);
		System.out.println("Check stack simulator: " + this.stackSimulator);
	}
	
	private void updateControlRelation(String fullInst) {
		//Construct control relations
		if (this.curControlVar != null)
			this.updateCachedMap(this.curControlVar, fullInst, true);
		/*for (String control: this.controlVars) {
			this.updateCachedMap(control, fullInst, true);
		}*/
	}
	
	public void dumpGraph() {
		System.out.println("Data dependency:");
		for (String parent: this.dataDep.keySet()) {
			System.out.println("Source: " + parent);
			for (String childInst: this.dataDep.get(parent)) {
				System.out.println("	Sink: " + childInst);
			}
		}
		
		System.out.println("Control dependency:");
		for (String parent: this.controlDep.keySet()) {
			System.out.println("Source: " + parent);
			for (String childInst: this.controlDep.get(parent)) {
				System.out.println("	Sinke: " + childInst);
			}
		}
	}

}