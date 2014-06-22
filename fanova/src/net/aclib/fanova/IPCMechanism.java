package net.aclib.fanova;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class IPCMechanism {
	private Socket clientSocket;

	public IPCMechanism(String remoteHost, int remotePort){
			try {
				clientSocket = new Socket(remoteHost, remotePort);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		
	}
	
	public void send(String msg){
		PrintWriter bwrite;
		try {
			bwrite = new PrintWriter(clientSocket.getOutputStream());
			bwrite.append(msg);
			bwrite.flush();
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String receive(){
		
		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			
			return in.readLine();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
}
