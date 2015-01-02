package edu.columbia.psl.cc.inst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import com.google.gson.reflect.TypeToken;

import edu.columbia.psl.cc.config.MIBConfiguration;
import edu.columbia.psl.cc.datastruct.BytecodeCategory;
import edu.columbia.psl.cc.datastruct.VarPool;
import edu.columbia.psl.cc.inst.BlockAnalyzer.Block;
import edu.columbia.psl.cc.inst.BlockAnalyzer.InstTuple;
import edu.columbia.psl.cc.pojo.BlockNode;
import edu.columbia.psl.cc.pojo.CondNode;
import edu.columbia.psl.cc.pojo.InstNode;
import edu.columbia.psl.cc.pojo.LabelInterval;
import edu.columbia.psl.cc.pojo.LocalVar;
import edu.columbia.psl.cc.pojo.MultiNewArrayNode;
import edu.columbia.psl.cc.pojo.OpcodeObj;
import edu.columbia.psl.cc.pojo.StaticMethodMiner;
import edu.columbia.psl.cc.pojo.SwitchNode;
import edu.columbia.psl.cc.pojo.Var;
import edu.columbia.psl.cc.util.GlobalRecorder;
import edu.columbia.psl.cc.util.GsonManager;
import edu.columbia.psl.cc.util.MethodStackRecorder;
import edu.columbia.psl.cc.util.ObjectIdAllocater;
import edu.columbia.psl.cc.util.ReplayMethodStackRecorder;
import edu.columbia.psl.cc.util.StringUtil;

public class DynamicMethodMiner extends MethodVisitor {
	
	private static Logger logger = Logger.getLogger(DynamicMethodMiner.class);
	
	private static String methodStackRecorder = Type.getInternalName(MethodStackRecorder.class);
	
	//private static String virtualRecorder = Type.getInternalName(ReplayMethodStackRecorder.class);
	
	private static String srHandleCommon = MIBConfiguration.getSrHandleCommon();
	
	private static String srHCDesc = MIBConfiguration.getSrHCDesc();
	
	private static String srHCDescString = MIBConfiguration.getSrHCDescString();
	
	private static String srHandleLdc = MIBConfiguration.getSrHandleLdc();
	
	private static String srHandleLdcDesc = MIBConfiguration.getSrHandleLdcDesc();
	
	private static String srHandleField = MIBConfiguration.getSrHandleField();
	
	private static String srHandleFieldDesc = MIBConfiguration.getSrHandleFieldDesc();
	
	private static String srHandleMultiArray = MIBConfiguration.getSrHandleMultiArray();
	
	private static String srHandleMultiArrayDesc = MIBConfiguration.getSrHandleMultiArrayDesc();
	
	private static String srHandleMethod = MIBConfiguration.getSrHandleMethod();
	
	private static String srHandleMethodDesc = MIBConfiguration.getSrHandleMethodDesc();
	
	private static String srLoadParent = MIBConfiguration.getSrLoadParent();
	
	private static String srLoadParentDesc = MIBConfiguration.getSrLoadParentDesc();
	
	private static String srCheckClInit = MIBConfiguration.getSrCheckClInit();
	
	private static String srCheckClInitDesc = MIBConfiguration.getSrCheckClInitDesc();
	
	private static String srUpdateCurLabel = MIBConfiguration.getSrUpdateCurLabel();
	
	private static String srUpdateCurLabelDesc = MIBConfiguration.getSrUpdateCurLabelDesc();
	
	private static String srGraphDump = MIBConfiguration.getSrGraphDump();
	
	private static String srGraphDumpDesc = MIBConfiguration.getSrGraphDumpDesc();
	
	private String className;
	
	private String superName;
	
	private String myName;
	
	private String desc;
	
	private String fullKey;
	
	private String shortKey;
	
	private String templateAnnot;
	
	private String testAnnot;
	
	private boolean isStatic;
	
	private boolean isTemplate = false;
	
	private boolean isTest = false;
	
	private boolean annotGuard;
	
	private LocalVariablesSorter lvs;
	//private AdviceAdapter lvs;
	
	private int localMsrId = -1;
	
	private int virtualMsrId = -1;
	
	private Label curLabel = null;
	
	private int curLineNum = -1;
	
	private List<Label> allLabels = new ArrayList<Label>();
	
	private int[] repVector = new int[BytecodeCategory.getOpcodeCategory().size()];
	
	private HashMap<Integer, ArrayList<OpcodeObj>> records = genRecordTemplate();
	
	private ArrayList<OpcodeObj> sequence = new ArrayList<OpcodeObj>();
	
	private AtomicInteger indexer = new AtomicInteger();
	
	//Enable all instrumentation, if this is a constructor
	private boolean constructor = false;
	
	//Invoke the change of object id
	private boolean superVisited = false;
	
	//Control if the constructor should start passing object to recorder
	private boolean aload0Lock = false;
	
	//private BlockAnalyzer blockAnalyzer = new BlockAnalyzer();
	
	private boolean visitMethod = false;
	 
	public DynamicMethodMiner(MethodVisitor mv, 
			String className, 
			String superName, 
			int access, 
			String myName, 
			String desc, 
			String templateAnnot, 
			String testAnnot, 
			boolean annotGuard) {
		//super(Opcodes.ASM4, mv, access, myName, desc);
		super(Opcodes.ASM4, mv);
		this.className = className;
		this.superName = superName;
		this.myName = myName;
		this.desc = desc;
		this.fullKey = StringUtil.genKey(className, myName, desc);
		this.shortKey = GlobalRecorder.registerGlobalName(className, myName, fullKey);
		this.templateAnnot = templateAnnot;
		this.testAnnot = testAnnot;
		this.annotGuard = annotGuard;
		this.isStatic = ((access & Opcodes.ACC_STATIC) != 0);
		this.constructor = myName.equals("<init>");
		if (this.constructor)
			this.aload0Lock = true;
	}
	
	public synchronized int getIndex() {
		return indexer.getAndIncrement();
	}
	
	private static HashMap<Integer, ArrayList<OpcodeObj>> genRecordTemplate() {
		HashMap<Integer, ArrayList<OpcodeObj>> template = new HashMap<Integer, ArrayList<OpcodeObj>>();
		//Now we have 12 categories
		for (int i = 0; i < BytecodeCategory.getOpcodeCategory().size(); i++) {
			ArrayList<OpcodeObj> opjList = new ArrayList<OpcodeObj>();
			template.put(i, opjList);
		}
		return template;
	}
	
	public void setLocalVariablesSorter(LocalVariablesSorter lvs) {
		this.lvs = lvs;
	}
	
	public LocalVariablesSorter getLocalVariablesSorter() {
		return this.lvs;
	}
	
	public boolean shouldInstrument() {
		return !this.annotGuard || this.isTemplate || this.isTest;	
	}
	
	private boolean isReturn(int opcode) {
		if (opcode >= 172 && opcode <= 177)
			return true;
		else 
			return false;
	}
	
	private void recordOps(int category, int opcode) {
		OpcodeObj obj = BytecodeCategory.getOpcodeObj(opcode);
		this.records.get(category).add(obj);
		this.sequence.add(obj);
	}
	
	private void updateSingleCat(int catId, int opcode) {
		repVector[catId]++;
		recordOps(catId, opcode);
	}
	
	private void updateObjOnVStack() {
		//Store it in MethodStackRecorder
		this.mv.visitInsn(Opcodes.DUP);
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.mv.visitInsn(Opcodes.SWAP);
		this.mv.visitInsn(Opcodes.ICONST_0);
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
				methodStackRecorder, 
				MIBConfiguration.getObjOnStack(), 
				MIBConfiguration.getObjOnStackDesc());
	}
	
	private int updateMethodRep(int opcode) {
		int catId = BytecodeCategory.getSetIdByOpcode(opcode);
		if (catId >= 0 ) {
			updateSingleCat(catId, opcode);
		} else {
			logger.error("Cannot find category for: " + opcode);
		}
		return catId;
	}
	
	public void convertConst(int cons) {
		if (cons >= 0) {
			if (cons > 5 && cons < 128) {
				this.mv.visitIntInsn(Opcodes.BIPUSH, cons);
			} else if (cons >=128 && cons < 32768) {
				this.mv.visitIntInsn(Opcodes.SIPUSH, cons);
			} else if (cons >= 32768) {
				this.mv.visitLdcInsn(cons);
			}else if (cons == 0) {
				this.mv.visitInsn(Opcodes.ICONST_0);
			} else if (cons == 1) {
				this.mv.visitInsn(Opcodes.ICONST_1);
			} else if (cons == 2) {
				this.mv.visitInsn(Opcodes.ICONST_2);
			} else if (cons == 3) {
				this.mv.visitInsn(Opcodes.ICONST_3);
			} else if (cons == 4) {
				this.mv.visitInsn(Opcodes.ICONST_4);
			} else if (cons == 5) {
				this.mv.visitInsn(Opcodes.ICONST_5);
			}
		} else {
			if (cons == -1) {
				this.mv.visitInsn(Opcodes.ICONST_M1);
			} else if (cons <= -2 && cons > -129) {
				this.mv.visitIntInsn(Opcodes.BIPUSH, cons);
			} else if (cons <= -129 && cons > -32769) {
				this.mv.visitIntInsn(Opcodes.SIPUSH, cons);
			} else {
				this.mv.visitLdcInsn(cons);
			}
		}
		
	}
	
	private void handleLinenumber(int linenumber) {
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.convertConst(linenumber);
		this.mv.visitFieldInsn(Opcodes.PUTFIELD, methodStackRecorder, "linenumber", Type.INT_TYPE.getDescriptor());
	}
	
	private void handleLabel(Label label) {
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.mv.visitLdcInsn(label.toString());
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srUpdateCurLabel, srUpdateCurLabelDesc);
		//this.mv.visitFieldInsn(Opcodes.PUTFIELD, methodStackRecorder, "curLabel", Type.getDescriptor(String.class));
	}
		
	private int handleOpcode(int opcode, int...addInfo) {		
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.convertConst(opcode);
		int idx = this.getIndex();
		this.convertConst(idx);
		if (addInfo.length == 0) {
			this.mv.visitInsn(Opcodes.ICONST_M1);
		} else {
			this.convertConst(addInfo[0]);
		}
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srHandleCommon, srHCDesc);
		
		return idx;
	}
		
	private int handleOpcode(int opcode, String addInfo) {		
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.convertConst(opcode);
		int idx = this.getIndex();
		this.convertConst(idx);
		this.mv.visitLdcInsn(addInfo);
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srHandleCommon, srHCDescString);
		
		return idx;
	}
	
	private int handleField(int opcode, String owner, String name, String desc) {
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.convertConst(opcode);
		int idx = this.getIndex();
		this.convertConst(idx);
		this.mv.visitLdcInsn(owner);
		this.mv.visitLdcInsn(name);
		this.mv.visitLdcInsn(desc);
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srHandleField, srHandleFieldDesc);
		
		return idx;
	}
		
	private int handleLdc(int opcode, int times, String addInfo) {		
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.convertConst(opcode);
		int idx = this.getIndex();
		this.convertConst(idx);
		this.convertConst(times);
		this.mv.visitLdcInsn(addInfo);
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srHandleLdc, srHandleLdcDesc);
		
		return idx;
	}
	
	private int handleMultiNewArray(String desc, int dim) {		
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.mv.visitLdcInsn(desc);
		this.convertConst(dim);
		int idx = this.getIndex();
		this.convertConst(idx);
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srHandleMultiArray, srHandleMultiArrayDesc);
		
		return idx;
	}
	
	public int handleMethod(int opcode, String owner, String name, String desc) {		
		this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
		this.convertConst(opcode);
		int idx = this.getIndex();
		this.convertConst(idx);
		this.convertConst(this.curLineNum);
		this.mv.visitLdcInsn(owner);
		this.mv.visitLdcInsn(name);
		this.mv.visitLdcInsn(desc);
		this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srHandleMethod, srHandleMethodDesc);
		
		return idx;
	}
	
	public void initNoneIdRecorder() {
		if (this.shouldInstrument()) {
			//Create the method stack recorder
			this.localMsrId = this.lvs.newLocal(Type.getType(MethodStackRecorder.class));
			this.mv.visitTypeInsn(Opcodes.NEW, methodStackRecorder);
			this.mv.visitInsn(Opcodes.DUP);
			this.mv.visitLdcInsn(this.className);
			this.mv.visitLdcInsn(this.myName);
			this.mv.visitLdcInsn(this.desc);
			
			if (this.isStatic) {
				this.mv.visitInsn(Opcodes.ICONST_0);
			} else {
				this.convertConst(MethodStackRecorder.CONSTRUCTOR_DEFAULT);
			}
			
			this.mv.visitMethodInsn(Opcodes.INVOKESPECIAL, 
					methodStackRecorder, 
					"<init>", 
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
			this.mv.visitVarInsn(Opcodes.ASTORE, this.localMsrId);
		}
	}
	
	public void initConstructor() {
		logger.info("Visit constructor: " + this.className + " " + this.myName + " " + this.shouldInstrument());
		
		if (this.shouldInstrument()) {
			String superReplace = this.superName.replace("/", ".");
			if (StringUtil.shouldInclude(superReplace)) {
				this.mv.visitVarInsn(Opcodes.ALOAD, 0);
				this.mv.visitVarInsn(Opcodes.ALOAD, 0);
				this.mv.visitFieldInsn(Opcodes.GETFIELD, this.superName, MIBConfiguration.getMibId(), "I");
				this.mv.visitFieldInsn(Opcodes.PUTFIELD, this.className, MIBConfiguration.getMibId(), "I");
			} else {
				this.mv.visitVarInsn(Opcodes.ALOAD, 0);
				this.mv.visitVarInsn(Opcodes.ALOAD, 0);
				this.mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ObjectIdAllocater.class), 
						"getIndex", 
						"(Ljava/lang/Object;)I");
				this.mv.visitFieldInsn(Opcodes.PUTFIELD, this.className, MIBConfiguration.getMibId(), "I");
			}
			
			//Change the obj id of MethodStackRecorder
			this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
			this.mv.visitVarInsn(Opcodes.ALOAD, 0);
			this.mv.visitFieldInsn(Opcodes.GETFIELD, this.className, MIBConfiguration.getMibId(), "I");
			this.mv.visitFieldInsn(Opcodes.PUTFIELD, methodStackRecorder, "objId", "I");
			
			//Create the method stack recorder
			/*this.localMsrId = this.lvs.newLocal(Type.getType(MethodStackRecorder.class));
			this.mv.visitTypeInsn(Opcodes.NEW, methodStackRecorder);
			this.mv.visitInsn(Opcodes.DUP);
			this.mv.visitLdcInsn(this.className);
			this.mv.visitLdcInsn(this.myName);
			this.mv.visitLdcInsn(this.desc);
			
			if (this.isStatic) {
				this.mv.visitInsn(Opcodes.ICONST_0);
			} else {
				this.mv.visitVarInsn(Opcodes.ALOAD, 0);
				this.mv.visitFieldInsn(Opcodes.GETFIELD, this.className, MIBConfiguration.getMibId(), "I");
			}
			
			this.mv.visitMethodInsn(Opcodes.INVOKESPECIAL, 
					methodStackRecorder, 
					"<init>", 
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
			this.mv.visitVarInsn(Opcodes.ASTORE, this.localMsrId);*/
		}
	}
	
	@Override
	public void visitCode() {
		this.mv.visitCode();
		if (this.constructor && !this.superVisited) {
			this.initNoneIdRecorder();
			return ; 
		}
		
		if (this.shouldInstrument() && this.localMsrId < 0) {
			logger.info("Visit method: " + this.myName + " " + this.shouldInstrument());
			
			//Create the method stack recorder
			this.localMsrId = this.lvs.newLocal(Type.getType(MethodStackRecorder.class));
			this.mv.visitTypeInsn(Opcodes.NEW, methodStackRecorder);
			this.mv.visitInsn(Opcodes.DUP);
			this.mv.visitLdcInsn(this.className);
			this.mv.visitLdcInsn(this.myName);
			this.mv.visitLdcInsn(this.desc);
			
			if (this.isStatic) {
				this.mv.visitInsn(Opcodes.ICONST_0);
			} else {
				this.mv.visitVarInsn(Opcodes.ALOAD, 0);
				this.mv.visitFieldInsn(Opcodes.GETFIELD, this.className, MIBConfiguration.getMibId(), "I");
			}
			
			this.mv.visitMethodInsn(Opcodes.INVOKESPECIAL, 
					methodStackRecorder, 
					"<init>", 
					"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
			this.mv.visitVarInsn(Opcodes.ASTORE, this.localMsrId);
			
			if (this.myName.equals("<clinit>")) {
				this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
				this.mv.visitLdcInsn(this.superName);
				this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
						methodStackRecorder, 
						srCheckClInit, 
						srCheckClInitDesc);
			}
		}
	}
	
	@Override
	public void visitLabel(Label label) {
		this.curLabel = label;
		this.allLabels.add(label);
		this.mv.visitLabel(label);
		
		if (this.shouldInstrument()) {
			this.handleLabel(label);
		}
		//this.blockAnalyzer.setCurLabel(label);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		if (desc.equals(this.templateAnnot)) {
			this.isTemplate = true;
		} else if (desc.equals(this.testAnnot)) {
			this.isTest = true;
		}
		return this.mv.visitAnnotation(desc, visible);
	}
	
	@Override
	public void visitLineNumber(int line, Label label) {
		this.curLineNum = line;
		this.mv.visitLineNumber(line, label);
		
		if (this.shouldInstrument())
			this.handleLinenumber(line);
	}
	
	@Override
	public void visitInsn(int opcode) {
		if (this.shouldInstrument()) {
			int instIdx = -1;
			if (!isReturn(opcode)) {
				instIdx = this.handleOpcode(opcode);
				this.mv.visitInsn(opcode);
			} else {
				instIdx = this.handleOpcode(opcode);
				this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
				this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srGraphDump, srGraphDumpDesc);
				this.mv.visitInsn(opcode);
			}
			
			if (opcode == Opcodes.AALOAD) {
				this.updateObjOnVStack();
			}
			
			//For static analysis
			this.updateMethodRep(opcode);
			//this.blockAnalyzer.registerInst(instIdx, opcode);
		} else {
			this.mv.visitInsn(opcode);
		}
	}
	
	@Override
	public void visitIntInsn(int opcode, int operand) {
		if (this.shouldInstrument()) {
			int instIdx = this.handleOpcode(opcode, operand);
			this.updateMethodRep(opcode);
			//this.blockAnalyzer.registerInst(instIdx, opcode);
		}
		this.mv.visitIntInsn(opcode, operand);
	}
	
	@Override
	public void visitVarInsn(int opcode, int var) {
		if (this.shouldInstrument()) {
			int instIdx = this.handleOpcode(opcode, var);
			this.updateMethodRep(opcode);
			//this.blockAnalyzer.registerInst(instIdx, opcode);
		}
		this.mv.visitVarInsn(opcode, var);
		
		if (this.shouldInstrument()) {
			if (opcode == Opcodes.ALOAD && var == 0 && this.aload0Lock) {
				this.mv.visitInsn(Opcodes.DUP);
			} else if (opcode == Opcodes.ALOAD) {
				this.updateObjOnVStack();
			}
		}
	}
	
	@Override
	public void visitTypeInsn(int opcode, String type) {
		if (this.shouldInstrument()) {
			int instIdx = this.handleOpcode(opcode, type);
			this.updateMethodRep(opcode);
			//this.blockAnalyzer.registerInst(instIdx, opcode);
		}
		this.mv.visitTypeInsn(opcode, type);
		
		if (this.shouldInstrument()) {
			if (opcode == Opcodes.NEW) {
				this.mv.visitInsn(Opcodes.DUP);
			} else if (opcode == Opcodes.CHECKCAST) {
				this.updateObjOnVStack();
			}
		}
	}
	
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if (this.shouldInstrument()) {
			int instIdx = this.handleField(opcode, owner, name, desc);
			this.updateMethodRep(opcode);
			//this.blockAnalyzer.registerInst(instIdx, opcode);
		}
		this.mv.visitFieldInsn(opcode, owner, name, desc);
		
		if (this.shouldInstrument()) {
			if ((opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC)) {
				int sort = Type.getType(desc).getSort();
				if (sort == Type.OBJECT || sort == Type.ARRAY)
					this.updateObjOnVStack();
			}
		}
	}
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		//For merging the graph on the fly, need to visit method before recording them
		this.mv.visitMethodInsn(opcode, owner, name, desc);
		if (this.shouldInstrument()) {
			if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
				Type[] argTypes = Type.getMethodType(desc).getArgumentTypes();
				int traceBack = 0;
				for (Type t: argTypes) {
					if (t.getDescriptor().equals("D") || t.getDescriptor().equals("J")) {
						traceBack += 2;
					} else {
						traceBack += 1;
					}
				}
				
				this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
				this.mv.visitInsn(Opcodes.SWAP);
				this.convertConst(traceBack);
				this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
						methodStackRecorder, 
						MIBConfiguration.getObjOnStack(), 
						MIBConfiguration.getObjOnStackDesc());
				
			}
			
			int instIdx = this.handleMethod(opcode, owner, name, desc);
			this.updateMethodRep(opcode);
			//this.blockAnalyzer.registerInst(instIdx, opcode);
			
			int returnSort = Type.getMethodType(desc).getReturnType().getSort();
			if (returnSort == Type.OBJECT || returnSort == Type.ARRAY)
				this.updateObjOnVStack();
			
			this.visitMethod = true;
		}
		
		//If the INVOKESPECIAL is visited, start instrument constructor
		if (this.constructor 
				&& opcode == Opcodes.INVOKESPECIAL 
				&& (owner.equals(this.superName) || owner.equals(this.className))
				&& name.equals("<init>")
				&& !this.superVisited) {
			logger.info("Super class is visited: " + owner + " " + name);
			logger.info("Start constructor recording: " + this.className + " " + this.myName);
			this.initConstructor();
			this.superVisited = true;
			this.aload0Lock = false;
			this.constructor = false;
			
			/*this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
			this.mv.visitLdcInsn(owner);
			this.mv.visitLdcInsn(name);
			this.mv.visitLdcInsn(desc);
			this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
					methodStackRecorder, 
					srLoadParent, 
					srLoadParentDesc);*/
		}
	}
	
	@Override
	public void visitJumpInsn(int opcode, Label label) {
		String labelString = label.toString();
		if (this.shouldInstrument()) {
			int instIdx = this.handleOpcode(opcode, labelString);
			this.updateMethodRep(opcode);
			//this.blockAnalyzer.registerInst(instIdx, opcode, label);
		}
		this.mv.visitJumpInsn(opcode, label);
		
		/*if (this.shouldInstrument() && !this.constructor) {
			if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR) {
				Label breakLabel = new Label();
				this.mv.visitLabel(breakLabel);
				this.blockAnalyzer.setCurLabel(breakLabel);
				this.handleLabel(breakLabel);
				logger.info("Break label: " + breakLabel);
			}
		}*/
	}
	
	@Override
	public void visitLdcInsn(Object cst) {
		if (this.shouldInstrument()) {
			int instIdx = -1;
			if (cst instanceof Double || cst instanceof Long) {
				instIdx = this.handleLdc(Opcodes.LDC, 2, cst.toString());
			} else {
				instIdx = this.handleLdc(Opcodes.LDC, 1, cst.toString());
			}
			
			this.updateMethodRep(Opcodes.LDC);
			//this.blockAnalyzer.registerInst(instIdx, Opcodes.LDC);
		}
		this.mv.visitLdcInsn(cst);
		
		if (this.shouldInstrument()) {
			if (cst instanceof String) {
				this.updateObjOnVStack();
			} else if (cst instanceof Type) {
				int sort = ((Type)cst).getSort();
				if (sort == Type.OBJECT || sort == Type.ARRAY) {
					this.updateObjOnVStack();
				}
			}
		}
	}
	
	@Override
	public void visitIincInsn(int var, int increment) {
		if (this.shouldInstrument()) {
			int instIdx = this.handleOpcode(Opcodes.IINC, var);
			this.updateMethodRep(Opcodes.IINC);
			//this.blockAnalyzer.registerInst(instIdx, Opcodes.IINC);
		}
		this.mv.visitIincInsn(var, increment);
	}
	
	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label...labels) {
		if (this.shouldInstrument()) {
			StringBuilder sb = new StringBuilder();
			String defaultLabel = dflt.toString();
			
			for (Label l: labels) {
				sb.append(l.toString() + ",");
			}
			
			//System.out.println("min max: " + min + " " + max);
			//System.out.println("default: " + defaultLabel);
			//System.out.println("all labels: " + sb.toString());
			int instIdx = this.handleOpcode(Opcodes.TABLESWITCH, sb.substring(0, sb.length() - 1));
			this.updateMethodRep(Opcodes.TABLESWITCH);
			//this.blockAnalyzer.registerInst(instIdx, Opcodes.TABLESWITCH, labels);
		}
		this.mv.visitTableSwitchInsn(min, max, dflt, labels);
	}
	
	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		if (this.shouldInstrument()) {
			//String labelString = dflt.toString();
			StringBuilder sb = new StringBuilder();
			for (Label l: labels) {
				sb.append(l.toString() + ",");
			}
			int instIdx = this.handleOpcode(Opcodes.LOOKUPSWITCH, sb.substring(0, sb.length() - 1));
			this.updateMethodRep(Opcodes.LOOKUPSWITCH);
			//this.blockAnalyzer.registerInst(instIdx, Opcodes.LOOKUPSWITCH, labels);
		}
		this.mv.visitLookupSwitchInsn(dflt, keys, labels);
	}
	
	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		if (this.shouldInstrument()) {
			int instIdx = this.handleMultiNewArray(desc, dims);
			this.updateMethodRep(Opcodes.MULTIANEWARRAY);
			//this.blockAnalyzer.registerInst(instIdx, Opcodes.MULTIANEWARRAY);
		}
		this.mv.visitMultiANewArrayInsn(desc, dims);
	}
	
	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		//Temporarily ignore. Error handling should not affect program similarity?
		this.mv.visitTryCatchBlock(start, end, handler, type);
	}
	
	@Override
	public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int indes) {
		this.mv.visitLocalVariable(name, desc, signature, start, end, indes);
	}
	
	/*@Override
	public void onMethodExit(int opcode) {
		System.out.println("On Method Exit: " + opcode);
		if (this.annotGuard()) {
			System.out.println("On Method Exit: " + opcode);
			//this.mv.visitVarInsn(Opcodes.ALOAD, this.localMsrId);
			//this.mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodStackRecorder, srGraphDump, srGraphDumpDesc);
		}
	}*/
	
	@Override
	public void visitEnd() {
		/*if (this.shouldInstrument()) {
			StringBuilder sb = new StringBuilder();
			for (OpcodeObj oo: this.sequence) {
				sb.append((char)(oo.getCatId() + 97));
			}
			
			HashMap<String, Integer> labelMap = new HashMap<String, Integer>();
			for (Label l: this.allLabels) {
				labelMap.put(l.toString(), l.getOffset());
			}
			
			StaticMethodMiner sr = new StaticMethodMiner();
			sr.setOpCatString(sb.toString());
			sr.setOpCatFreq(this.repVector);
			sr.setLabelMap(labelMap);
			
			String key = StringUtil.appendMap(shortKey);
			
			logger.info("Start block analysis: " + key);
			this.blockAnalyzer.analyzeBlocks();
			List<Block> blockList = this.blockAnalyzer.getBlockList();
			HashMap<String, Block> blockMap = new HashMap<String, Block>();
			sr.setBlockMap(blockMap);
			for (Block b: blockList) {
				logger.info("Block " + b.startLabel);
				logger.info("Child block: " + b.childBlocks);
				logger.info("Cond map: " + b.condMap.keySet());
				
				for (String labelKey: b.condMap.keySet()) {
					logger.info("Cond label: " + labelKey);
					logger.info("Tag: " + Arrays.toString(b.condMap.get(labelKey)));
				}
				
				for (InstTuple it: b.instList) {
					System.out.println(it.instIdx + " " + it.opcode + BytecodeCategory.getOpcodeObj(it.opcode).getInstruction());
				}
				
				for (String label: b.labels) {
					blockMap.put(label, b);
				}
			}
			
			TypeToken<StaticMethodMiner> typeToken  = new TypeToken<StaticMethodMiner>(){};
			GsonManager.writeJsonGeneric(sr, key, typeToken, 2);
		}*/
		
		if (this.indexer.get() < MIBConfiguration.getInstance().getInstThreshold() && !this.visitMethod) {
			GlobalRecorder.registerUndersizedMethod(this.shortKey);
		}
		
		this.mv.visitEnd();
	}
}
