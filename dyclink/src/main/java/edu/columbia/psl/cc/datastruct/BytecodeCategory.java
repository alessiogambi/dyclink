package edu.columbia.psl.cc.datastruct;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import edu.columbia.psl.cc.config.MIBConfiguration;
import edu.columbia.psl.cc.pojo.OpcodeObj;
import edu.columbia.psl.cc.premain.MIBDriver;

public class BytecodeCategory {
	
	private static final Logger logger = LogManager.getLogger(BytecodeCategory.class);
	
	//private static String opTablePath = "opcodes/opcode_table.txt";
		
	private static final HashMap<Integer, String> opcodeCategory = new HashMap<Integer, String>();
	
	private static final HashMap<Integer, OpcodeObj> opcodeTable = new HashMap<Integer, OpcodeObj>();
	
	private static final HashMap<Integer, HashSet<Integer>> catMap = new HashMap<Integer, HashSet<Integer>>();
	
	private static final HashSet<Integer> replacedOps = new HashSet<Integer>();
	
	private static final HashSet<Integer> writeCategory = new HashSet<Integer>();
	
	private static final HashSet<Integer> readCategory = new HashSet<Integer>();
	
	private static final HashSet<Integer> writeFieldCategory = new HashSet<Integer>();
	
	private static final HashSet<Integer> readFieldCategory = new HashSet<Integer>();
	
	private static final HashSet<Integer> controlCategory = new HashSet<Integer>();
	
	private static final HashSet<Integer> dupCategory = new HashSet<Integer>();
	
	private static final HashSet<Integer> staticMethodOps = new HashSet<Integer>();
	
	private static final HashSet<Integer> objMethodOps = new HashSet<Integer>();
	
	private static final HashSet<Integer> methodOps = new HashSet<Integer>();
	
	private static final HashSet<Integer> returnOps = new HashSet<Integer>();
	
	static {
		loadOpcodeTable();
		loadOpcodeCategory();
		
		replacedOps.add(Opcodes.GETFIELD);
		replacedOps.add(Opcodes.GETSTATIC);
		replacedOps.add(Opcodes.AALOAD);
		replacedOps.add(Opcodes.PUTFIELD);
		replacedOps.add(Opcodes.PUTSTATIC);
		replacedOps.add(Opcodes.AASTORE);
		
		writeCategory.add(3);
		
		readCategory.add(1);
		
		writeFieldCategory.add(15);
		
		readFieldCategory.add(14);
		
		controlCategory.add(13);
		
		dupCategory.add(6);
		dupCategory.add(7);
		
		staticMethodOps.add(Opcodes.INVOKESTATIC);
		
		objMethodOps.add(Opcodes.INVOKEVIRTUAL);
		objMethodOps.add(Opcodes.INVOKEINTERFACE);
		objMethodOps.add(Opcodes.INVOKESPECIAL);
		
		methodOps.addAll(staticMethodOps);
		methodOps.addAll(objMethodOps);
		methodOps.add(Opcodes.INVOKEDYNAMIC);
		
		for (int i = 172; i <= 177; i++) {
			returnOps.add(i);
		}
	}
	
	private static void loadOpcodeCategory() {		
		File f = new File(MIBConfiguration.getInstance().getOpCodeCatId());
		
		if (!f.exists()) {
			logger.error("Opcode category ID table does not exist");
			return ;
		}
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(f));
			
			String line;
			while ((line = br.readLine()) != null) {
				String[] info = line.split(",");
				opcodeCategory.put(Integer.valueOf(info[0]), info[1]);
			}
			br.close();
		} catch (Exception ex) {
			//ex.printStackTrace();
			logger.error("Error: ", ex);
		}
	}
	
	public static String getOpcodeCategoryName(int catId) {
		return opcodeCategory.get(catId);
	}
	
	public static List<String> processOpTableElement(String rawContent) {
		List<String> ret = new ArrayList<String>();
		
		if (rawContent.equals("no")) {
			return ret;
		}
		
		String[] contentArray = rawContent.split(":");
		for (String s: contentArray) {
			ret.add(s);
		}
		return ret;
	}
	
	private static void loadOpcodeTable() {
		File f = new File(MIBConfiguration.getInstance().getOpTablePath());
		if (!f.exists()) {
			logger.error("Find no opcode table information");
			return ;
		}
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] info = line.split(",");
				//info[2] is hex, we can have it by opcode
				
				int catId = Integer.valueOf(info[0]);
				int subCatId = Integer.valueOf(info[1]);
				int subSubCatId = Integer.valueOf(info[2]);
				int opcode = Integer.valueOf(info[3]);
				
				OpcodeObj oo = new OpcodeObj();
				oo.setCatId(catId);
				oo.setSubCatId(subCatId);
				oo.setSubSubCatId(subSubCatId);
				oo.setOpcode(opcode);
				oo.setInstruction(info[5]);
				
				//Process input
				List<String> inList = processOpTableElement(info[6]);
				oo.setInList(inList);
				
				//Process output
				List<String> outList = processOpTableElement(info[7]);
				oo.setOutList(outList);
				
				opcodeTable.put(opcode, oo);
				
				if (catMap.keySet().contains(catId)) {
					catMap.get(catId).add(opcode);
				} else {
					HashSet<Integer> catSet = new HashSet<Integer>();
					catSet.add(opcode);
					catMap.put(catId, catSet);
				}
			}
		} catch (Exception ex) {
			logger.error("Error: ", ex);
		}
	}
	
	public static HashMap<Integer, OpcodeObj> getOpcodeTable() {
		return opcodeTable; 
	}
	
	public static OpcodeObj getOpcodeObj(int opcode) {
		return opcodeTable.get(opcode);
	}
	
	public static HashSet<Integer> getOpcodeSetByCat(int catId) {
		return catMap.get(catId);
	}
	
	public static int getSetIdByOpcode(int opcode) {
		for (Integer i: catMap.keySet()) {
			HashSet<Integer> ops = catMap.get(i);
			if (ops.contains(opcode)) {
				return i;
			}
		}
		return -1;
	}
	
	public static HashMap<Integer, String> getOpcodeCategory() {
		return opcodeCategory;
	}
	
	public static HashSet<Integer> writeCategory() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(3);
		return ret;*/
		return writeCategory;
	}
	
	public static HashSet<Integer> readCategory() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(1);
		return ret;*/
		return readCategory;
	}
	
	public static HashSet<Integer> writeFieldCategory() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(15);
		return ret;*/
		return writeFieldCategory;
	}
	
	public static HashSet<Integer> readFieldCategory() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(14);
		return ret;*/
		return readFieldCategory;
	}
	
	public static HashSet<Integer> controlCategory() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(13);
		return ret;*/
		return controlCategory;
	}
	
	/**
	 * Include dup and swap
	 * @return
	 */
	public static HashSet<Integer> dupCategory() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(6);
		ret.add(7);
		return ret;*/
		return dupCategory;
	}
	
	public static HashSet<Integer> staticMethodOps() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(184);
		return ret;*/
		return staticMethodOps;
	}
	
	public static HashSet<Integer> returnOps() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		for (int i = 172; i <= 177; i++) {
			ret.add(i);
		}
		return ret;*/
		return returnOps;
	}
	
	public static HashSet<Integer> objMethodOps() {
		/*HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(182);
		ret.add(183);
		ret.add(185);
		return ret;*/
		return objMethodOps;
	}
	
	public static HashSet<Integer> methodOps() {
		/*HashSet<Integer> allMethods = new HashSet<Integer>();
		allMethods.addAll(objMethodOps());
		allMethods.addAll(staticMethodOps());
		allMethods.add(Opcodes.INVOKEDYNAMIC);
		return allMethods;*/
		return methodOps;
	}
	
	public static HashSet<Integer> replacedOps() {
		return replacedOps;
	}
	
	public static HashSet<Integer> asmPrimitiveSort() {
		HashSet<Integer> ret = new HashSet<Integer>();
		ret.add(Type.BOOLEAN);
		ret.add(Type.BYTE);
		ret.add(Type.CHAR);
		ret.add(Type.DOUBLE);
		ret.add(Type.FLOAT);
		ret.add(Type.INT);
		ret.add(Type.LONG);
		ret.add(Type.SHORT);
		ret.add(Type.VOID);
		return ret;
	}
	
	public static Integer asmObjSort() {
		return Type.OBJECT;
	}
	
	public static void main(String[] args) {
		/*loadOpcodeTable();
		for (int opcode: opcodeTable.keySet()) {
			OpcodeObj oo = opcodeTable.get(opcode);
			System.out.println(oo.getCatId() + " " + oo.getSubCatId() + " " + oo.getSubSubCatId() + " " + " " + oo.getOpcode() + " " + oo.getInstruction());
		}*/
		System.out.println("Read cat: " + readCategory);
		System.out.println("Write cat: " + writeCategory);
		System.out.println("Read field cat: " + readFieldCategory);
		System.out.println("Write field cat: " + writeFieldCategory);
		System.out.println("Control cat: " + controlCategory);
		System.out.println("Dup cat: " + dupCategory);
		System.out.println("Static method ops: " + staticMethodOps);
		System.out.println("Obj method ops: " + objMethodOps);
		System.out.println("Total method ops: " + methodOps);
		System.out.println("Return ops: " + returnOps);
	}
	
}
