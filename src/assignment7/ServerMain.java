package assignment7;

/* 
 * Aaron Babber
 * aab3456
 * 16480
 * Enrique Perez-Osborne
 * ehp355
 * 16465
 * Slip days used: <0>
 * Fall 2016
 */


public class ServerMain {
	
	public static void main(String[] args){
		try{
			new ChatServer().setUpNetworking();
		} catch(Exception e){
			e.printStackTrace();
		}
	}

}