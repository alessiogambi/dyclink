package cc.expbase;

import java.io.File;

import com.google.gson.reflect.TypeToken;

import edu.columbia.psl.cc.pojo.GraphTemplate;
import edu.columbia.psl.cc.pojo.InstNode;
import edu.columbia.psl.cc.util.GraphConstructor;
import edu.columbia.psl.cc.util.GsonManager;
import edu.columbia.psl.cc.util.TemplateLoader;

public class MyObject2 {
	
	private int[] myArray;
	
	private int myI;
	
	private int myJ;
	
	public int add(int i, int j) {
		int ret = 0;
		if (i > 5) {
			ret = i + j;
		} else {
			ret = i - 5;
			ret = j + ret;
			ret = i * ret;
		}
		return ret;
	}
	
	public int addObj(MyObject2 mo) {
		if (mo.myI <= 5) {
			int ret = mo.myI - 5 + mo.myJ;
			ret *= mo.myI;
			return ret;
		} else {
			int ret = mo.myI + mo.myJ;
			return ret;
		}
	}
	
	public int setAndSum(int[] arr) {
		this.myArray = arr;
		
		int ret = 0;
		for (int i = 0; i < arr.length; i++) {
			ret += arr[i];
		}
		
		return ret;
	}
	
	public int directAdd(int i, int j) {
		return i + j;
	}
	
	public int fieldAdd() {
		return this.myI + this.myJ;
	}
	
	public static void main(String[] args) {
		MyObject2 mo = new MyObject2();
		//int[] arr = {1, 2};
		//mo.setAndSum(arr);
		mo.myI = 2;
		mo.myJ = 1;
		//System.out.println(mo.addObj(mo));
		//System.out.println(mo.add(2, 1));
		//System.out.println(mo.directAdd(2, 1));
		//System.out.println(mo.fieldAdd());
		
		TypeToken<GraphTemplate> token = new TypeToken<GraphTemplate>(){};
		File gFile = new File("graphs/cc.expbase/cc.expbase.MyObject2:fieldAdd:0:0:3.json");
		GraphTemplate g = GsonManager.readJsonGeneric(gFile, token);
		System.out.println(g.getVertexNum());
		GraphConstructor gc = new GraphConstructor();
		gc.reconstructGraph(g, false);
		
		for (InstNode i: g.getInstPool()) {
			System.out.println(i);
			System.out.println(i.getChildFreqMap());
		}		
	}

}
