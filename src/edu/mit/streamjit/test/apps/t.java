package edu.mit.streamjit.test.apps;

import java.util.Random;

public class t {
	public static void main(String[] args){
		Random a=new Random();
		float t;
		for(int i=0;i<100;i++){
			t= a.nextFloat();
			if(t<0.5) 
				System.out.println("yes"+t);
			else
				System.out.println("no"+t);
		}
	}
}
