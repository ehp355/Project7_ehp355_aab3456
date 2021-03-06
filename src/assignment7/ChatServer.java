/* EE422C Project 7
 * Aaron Babber
 * aab3456
 * 16480
 * Enrique Perez-Osborne
 * ehp355
 * 16465
 * Slip days used: <1>
 * Fall 2016
 */

package assignment7;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends Observable {

	// A list of the users currently "online"
	private ArrayList<String> activeUsers;
	private ConcurrentHashMap<String, ArrayList<String>> chatHistory;
	private ConcurrentHashMap<String, ArrayList<String>> friendList;
	private ConcurrentHashMap<String, String> userAndPasswd;
	private ConcurrentHashMap<String, ClientObserver> userCO;
	private int port = 4343;	// TODO: Decide whether to have user defined port
	private BufferedReader loginReader;	// An input stream reader to determine a client's name

	/*
	@SuppressWarnings("unused")
	private ServerGUI serverGUI;

	public ChatServer(ServerGUI serverGUI) {
		this.serverGUI = serverGUI;

	}
	*/

	public void setUpNetworking() throws Exception {

		boolean userExists = false;
		boolean userMatchesPasswd =false;
		activeUsers = new ArrayList<String>();
		chatHistory = new ConcurrentHashMap<String, ArrayList<String>>();	// TODO: Double check this initialization
		userAndPasswd = new ConcurrentHashMap<String, String>();
		friendList = new ConcurrentHashMap<String,ArrayList<String>>();
		userCO = new ConcurrentHashMap<String,ClientObserver>();

		@SuppressWarnings("resource")
		ServerSocket serverSock = new ServerSocket(port);	// Set up the server socket

		// Serve clients indefinitely
		while(true){

				/* The accept() method will make the server wait until there's
				 * an incoming request from a client socket. After it receives such a request,
				 * it executes the code which follows.
				 */
				Socket clientSocket = serverSock.accept();

				loginReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

				// readLine() will wait until there's something in the input stream (i.e. it "blocks")
				String name = loginReader.readLine();
				String pass = loginReader.readLine();

				//TODO:implement check of name
				if(userAndPasswd.containsKey(name)){
					userExists = true;
					if(userAndPasswd.get(name).equals(pass)){
						activeUsers.add(name);
						userMatchesPasswd =true;
					}else{
						userMatchesPasswd = false;
					}
				}else{
					userExists =false;
				}

				// Print to console on successful connection
				System.out.println("Connection good for: " + name);	// TODO: Delete?

				// The output stream portion of a client socket is effectively the Observer
				ClientObserver writer = new ClientObserver(clientSocket.getOutputStream(), name);
				// TODO: Determine if order of this call matters

				userCO.put(name, writer);
				this.addObserver(writer);
				if(userExists==false){
					activeUsers.add(name);
					addUsers(name, pass);
				}

				/* This creates a new thread which constantly listens for input
				 * from the client connection which was just established.
				 */
				Thread t = new Thread(new ClientHandler(clientSocket, userExists, userMatchesPasswd, name));
				t.start();

		}
	}


	// ---------------------------------------- PRIVATE METHODS ---------------------------------------- //


	private void updateUsers(String name) {
		for (String userName : activeUsers) {
			String friendUpdate = "new:" + userName;
			setChanged();
			notifyObservers(friendUpdate);
		}
	}

	private void delUser(String name){
		deleteObserver(userCO.get(name));
	}

	private void addUsers(String name, String pass){
		userAndPasswd.put(name, pass);
	}

	// ----------------------------------------- INNER CLASSES ----------------------------------------- //


	/* We're going to use message metadata to identify senders and receivers.
	 *
	 * The message String will be of the form:
	 *
	 * from:sender [tab] to:receiver1, receiver2, receiver3, ... [tab] [Actual message]
	 *
	 * It doesn't matter if the user types from:sender or to:receiver, because we prepend
	 * it to the message and only process the first occurrence of each.
	 *
	 * There are several other tags we use for other client actions. This seemed like the
	 * most efficient way to communicate between client and server as Strings can be passed
	 * around very easily as opposed to Java collections objects.
	 */
	class ClientHandler implements Runnable {

		private BufferedReader reader;
		private PrintWriter historyWriter;
		private boolean userExists;
		private boolean userMatchesPasswd;
		private String name;
		private Socket sock;

		public ClientHandler(Socket clientSocket, boolean uE, boolean uMP, String n) throws IOException {
			sock = clientSocket;
			reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			historyWriter = new PrintWriter(sock.getOutputStream());
			userExists = uE;
			userMatchesPasswd = uMP;
			this.name=n;
		}

		public void run() {

			if(userExists==false){

				historyWriter.println("Done");
				historyWriter.flush();
				for(String s: activeUsers){
					historyWriter.println(s);
					historyWriter.flush();
				}
				historyWriter.println("Done");
				historyWriter.flush();
				updateUsers(name);

			}else if(userExists==true && userMatchesPasswd==true){
				ArrayList<String> friends = friendList.get(name);

				System.out.println(friends.toString() + " -- ClientHandler in server");

				if (friends != null) {
					for(String s: friends){
						historyWriter.println(s);
						historyWriter.flush();
					}
				}
				historyWriter.println("Done");
				historyWriter.flush();

				for(String s: activeUsers){
					historyWriter.println(s);
					historyWriter.flush();
				}
				historyWriter.println("Done");
				historyWriter.flush();
				updateUsers(name);

			}else if(userExists==true && userMatchesPasswd==false){

				historyWriter.println("Failed");
				historyWriter.flush();
				try {
					historyWriter.close();
					reader.close();
					sock.close();
				} catch (IOException e) {

				}

			}

			String message;		// The input from the client

			try{
				while((message = reader.readLine()) != null){

					// Determines what kind of action the client is performing
					String firstLetter = Character.toString(message.charAt(0));
					String tag = message.substring(0, 4);

					System.out.println(message + " -- ClientHandler");	// TODO: Comment this when not testing
					System.out.println(tag + " -- ClientHandler");	// TODO: Comment this when not testing

					// The client is sending a message
					if (firstLetter.equals("f")) {
						setChanged();
						// Calls update() for each Observer
						notifyObservers(message);
						updateHistory(message);
					}

					else if (firstLetter.equals("h")) {
						String userName = message.substring(5, message.length());
						if(!chatHistory.containsKey(userName)) {
							continue;
						}
						ArrayList<String> histToSend = chatHistory.get(userName);
						// TODO: Watch for possible lag due to for loop, if so,
						// threading could be possible solution
						for(String s : histToSend){
							historyWriter.println("hist:" + s);
							historyWriter.flush();
						}

					}else if(firstLetter.equals("c")){

						//NEED to check if parsing string correctly
						String userName = message.substring(3,message.indexOf("\t"));
						String newPasswd = message.substring(message.indexOf("\t")+1,message.length());
						System.out.println(newPasswd);
						userAndPasswd.put(userName,newPasswd);

					}else if(firstLetter.equals("d")){

						String userName = message.substring(4,message.length());
						//removes from list of Observers
						delUser(userName);
						activeUsers.remove(userName);
						setChanged();
						notifyObservers(message);
						historyWriter.close();
						reader.close();
						sock.close();
					}

					// A client is sending a friend request
					else if (tag.equals("req:")) {
						setChanged();
						notifyObservers(message);
					}

					// A client is receiving a reply to a friend request
					else if (tag.equals("rep:")) {
						updateFriendLists(message);	// TODO: Test update of friendLists
						setChanged();
						notifyObservers(message);
					}

				}
			}catch(IOException e){

			}
			/* If the thread finishes because of a GUI closing, does
			 * control come here?
			 */
			System.out.println("A client has left");
		}

		private void updateHistory(String message) {
			ArrayList<String> userHist = findName(message);
			for(String user : userHist) {
				if(chatHistory.containsKey(user)){
					ArrayList<String> temp = chatHistory.get(user);
					temp.add(message);
				}else{
					ArrayList<String> newHist = new ArrayList<String>();
					chatHistory.put(user, newHist);
					newHist.add(message);
				}
			}
		}

		private ArrayList<String> findName(String arg){
			// It's not safe to use a regex as the user message might have tabs and whatnot
			// TODO: Prevent user from having tabs, commas, or spaces in their username
			String message = arg;
			ArrayList<String> users = new ArrayList<String>();

			/* The first parameter is the beginning index, inclusive, and the
			 * second parameter is the ending index, exclusive.
			 */
			int fromEnd = message.indexOf('\t');
			String fromString = message.substring(0, fromEnd);

			// Update message as a sender could have the string "to" in their name
			message = arg.substring(fromEnd + 1, arg.length());
			int receiveEnd = message.indexOf('\t');
			String receiverString = message.substring(0, receiveEnd);

			users.add(fromString.substring(5, fromString.length()));

			// Strip the "to:" from the String of receivers
			receiverString = receiverString.substring(3, receiverString.length());

			// A String is a CharSequence - the parameter needed for contains()
			String comma = ",";
			if (!receiverString.contains(comma)) {
				users.add(receiverString);
			}
			else {
				String[] receivers = receiverString.split(", ");
				for (String r : receivers) {
					users.add(r);
				}
			}

			Collections.sort(users);
			return users;
		}

		// Message form: "rep:recipient [tab] to:sender [tab] [reply]"
		private void updateFriendLists(String message) {

			String cutMessage;
			int replyEnd = message.indexOf('\t');

			// replyString = "rep:recipient"
			String replyString = message.substring(0, replyEnd);
			int colonIndex = replyString.indexOf(':');
			String replier = replyString.substring(colonIndex + 1, replyString.length());

			// cutMessage = "to:sender [tab] [reply]"
			cutMessage = message.substring(replyEnd + 1, message.length());

			System.out.println(cutMessage + " -- ClientHandler/updateFriendLists");	// TODO: Comment this when not testing

			int senderEnd = cutMessage.indexOf('\t');
			// senderString = "to:sender"
			String senderString = cutMessage.substring(0, senderEnd);
			colonIndex = senderString.indexOf(':');
			String originalSender = senderString.substring(colonIndex + 1, senderString.length());

			// reply = [reply]
			String reply = cutMessage.substring(senderEnd + 1, cutMessage.length());

			if (reply.equals("Y")) {
				ArrayList<String> replierList = friendList.get(replier);
				ArrayList<String> senderList = friendList.get(originalSender);
				if (replierList == null) {
					replierList = new ArrayList<String>();
					friendList.put(replier, replierList);
				}
				if (senderList == null) {
					senderList = new ArrayList<String>();
					friendList.put(originalSender, senderList);
				}
				replierList.add(originalSender);
				senderList.add(replier);
			}
		}

	}

}
