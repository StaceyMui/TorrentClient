import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import GivenTools.Bencoder2;
import GivenTools.BencodingException;
import GivenTools.TorrentInfo;

/**
 * 
 */

/**
 * 
 * 
 * 
 * @author Michael Reid
 * @author Stacey Mui
 *
 */
public class RUBTClient extends Thread
{

	/**
	 * Logger for the local client.
	 */
	private final static Logger LOGGER = Logger.getLogger(RUBTClient.class.getName());

/*private final TorrentInfo tInfo;

private final int totalPieces;

private final int fileLength;

private final int pieceLength;

private int port = 6881;

private final int downloaded;

private final int uploaded;

private final int left;*/
	
	private static byte[] clientID;

	public static void main(String[] args) throws URISyntaxException, IOException, BencodingException, NoSuchAlgorithmException 
	{
		//Set level of logger to INFO or higher
		LOGGER.setLevel(Level.INFO);

		// Check number/type of arguments
		if (args.length != 2) 
		{
			LOGGER.log(Level.SEVERE, "Two arguments required. Exiting program");
			System.exit(1);
		}

		String torrentChecker = args[0].substring(args[0].lastIndexOf(".") + 1, args[0].length());
		
		if (!(torrentChecker.equals("torrent"))) 
		{
			LOGGER.log(Level.SEVERE, "Not a valid .torrent file. Exiting program.");
			System.exit(1);
		}

		// Open torrent file
		byte[] metaBytes = null;
		try 
		{
			LOGGER.info("Opening torrent file");
			File metaFile = new File(args[0]);
			DataInputStream metaFileIn = new DataInputStream(new FileInputStream(metaFile));
			metaBytes = new byte[(int)metaFile.length()];
			metaFileIn.readFully(metaBytes);
			LOGGER.info("Reading torrent file");
			metaFileIn.close();
			LOGGER.info("Closing torrent file");

		} 
		catch (FileNotFoundException fnfEx) 
		{
			LOGGER.log(Level.SEVERE, "File "  + args[0] + " not found.", fnfEx);
			System.exit(1);
		} 
		catch (IOException ioEx) 
		{
			LOGGER.log(Level.SEVERE, "I/O exception for file " + args[0], ioEx);
			System.exit(1);
		}

		// Null check on metaBytes
		if (metaBytes == null) 
		{
			LOGGER.log(Level.SEVERE, "Corrupt torrent file.");
			System.exit(1);
		}

		// Decode torrent file
		TorrentInfo tInfo = null;
		try 
		{
			LOGGER.info("Decoding file...");
			tInfo = new TorrentInfo(metaBytes);
		} 
		catch (BencodingException be) 
		{
			LOGGER.log(Level.WARNING, "Bencoding exception", be);
		}	
			LOGGER.info("Getting list of peers...");
			try {
				//generatePeerId();
				ArrayList<Peer> peers = getListOfPeersHttpUrl(tInfo); //This method has already been complete in my book
				
				LOGGER.info("Retrieved peers. Extracting peers with -RU prefix");
				
				Peer RUPeer = getRUPeer(peers); //This method has also been finished
				
				if (RUPeer == null) 
				{
					LOGGER.log(Level.SEVERE, "There is no eligible peer to connect with.");
					System.exit(1);
				}
				LOGGER.info("Extracted appropriate peers. Performing handshake" );
				peerHandshake(RUPeer, tInfo); //I'm working on this method at the bottom
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BencodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	
	// It contacts the tracker, receives a byte array from it and turns the byte array into a list of peers
	private static ArrayList<Peer> getListOfPeersHttpUrl(TorrentInfo tInfo) throws IOException, BencodingException, URISyntaxException
	{

		byte[] peerId=null, trackerResponse; 
		ByteBuffer bytebuffer;
		int port = 0, i; 
		String ip="", key;
		
		LOGGER.info("Sending Get Request from tracker");
		trackerResponse = getRequest(tInfo);
		
		LOGGER.info("Decoding tracker's request");
		@SuppressWarnings("unchecked") //decoding Tracker response
		HashMap<ByteBuffer, Object> response = (HashMap<ByteBuffer, Object>)Bencoder2.decode(trackerResponse);
		
		//More plagarized code below
		ArrayList<Peer> p = new ArrayList<Peer>();
		
		//extracting  interval
		int interval = (Integer)response.get(ByteBuffer.wrap(new byte[] {'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' }));
		LOGGER.info("Extracted Interval: " + interval + "\n Extracting peers from decoded diction from tracker");
		
		@SuppressWarnings("unchecked")
		List<Map<ByteBuffer, Object>> objectList = (List<Map<ByteBuffer, Object>>) response.get( ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', 's' }));
		for (Map<ByteBuffer, Object> object : objectList) {
			for( i = 0; i < object.size(); ++ i){
				bytebuffer = (ByteBuffer)object.keySet().toArray()[i];		
				key = get_string(bytebuffer);
				if(key.equals("port"))
					port = (Integer)object.get(bytebuffer);
				else if(key.equals("ip")) 
					ip = new String(((ByteBuffer) object.get(bytebuffer)).array());
				else 
					peerId = ((ByteBuffer)object.get(bytebuffer)).array();
			}
			p.add(new Peer(peerId, port, ip));
		}
		return p;
	}
	
	//This method returns the Peer(s) with the -RU prefix
	private static Peer getRUPeer(ArrayList<Peer> peerlist) {
		String peerIdString;
		for (Peer p: peerlist){
			peerIdString = p.getStringPeerId();
			if(peerIdString.charAt(0)=='-' && peerIdString.charAt(1)=='R' && peerIdString.charAt(2)=='U') {
				return p;
			}
		}
		return null;
	}

	public static byte[] getRequest(TorrentInfo tInfo) throws IOException, URISyntaxException {
		byte[] response;
		int contentLength; 
		URL key = makeKey(tInfo);
		HttpURLConnection connection = (HttpURLConnection)key.openConnection();
		connection.setRequestMethod("GET");
		//notetoself: change variable names to avoid plagarism
		DataInputStream inputStream = new DataInputStream(connection.getInputStream());
		contentLength =connection.getContentLength(); //variable changed here
		response = new byte[contentLength];
		inputStream.readFully(response); // Tracker returns object here
		return response;
	}
	
	public static String get_string(ByteBuffer b){ 
		String s = new String(b.array());
		return s;
	}
		
	private static URL makeKey(TorrentInfo ti) throws URISyntaxException, MalformedURLException {
		byte[] hash = ti.info_hash.array();
		clientID = generatePeerId();
		URI uri = ti.announce_url.toURI();
		String stringKey = uri.getPath() + "?info_hash=";
		for(byte b : hash)
			stringKey = stringKey + "%" + String.format("%02X", b);
		stringKey = stringKey + "&peer_id=" + clientID + "&port=6881&uploaded=0&downloaded=0&left=" + ti.file_length + "&event=started";
	    URI newUri = uri.resolve(stringKey);
	    return newUri.toURL();
	}
	
	private static byte[] generatePeerId() 
	{
		final byte[] peerId = new byte[20];
		LOGGER.info("Adding RU to ID" );
		peerId[0] = 'R';
		peerId[1] = 'U';
		
		final byte[] remainPeerId = new byte[18];
		LOGGER.info("Adding 18 Random bytes" );
		new Random().nextBytes(remainPeerId);
		System.arraycopy(remainPeerId, 0, peerId, 2, remainPeerId.length);
		return peerId;
	}
	
	//This method is used to handshake and communicate from the peer
	private static void peerHandshake(Peer p, TorrentInfo ti) throws IOException, BencodingException, NoSuchAlgorithmException {
		byte id;
		int message_length;
		Socket TCPSocket = new Socket(p.ip, p.port); //Created the TCP 
		byte[] reserved = new byte[8], peerHandshake = new byte[68];
		
		//BufferedReader br = new BufferedReader(new InputStreamReader(TCPSocket.getInputStream()));
		DataInputStream dis = new DataInputStream(TCPSocket.getInputStream());
		DataOutputStream os = new DataOutputStream(TCPSocket.getOutputStream());
		String protocolId = "BitTorrent Protocol", peerID = p.getStringPeerId();
		
		//This was my way of sending the handshake
		Arrays.fill( reserved, (byte) 0 );
		os.writeByte(19);
		os.writeBytes(protocolId);
		os.write(reserved, 0, reserved.length);
		byte[] hash = ti.info_hash.array();
		os.write(hash);
		os.write(clientID);
		
		//This is another's programmer's approach that I used for testing purposes
		//1+19+8+20+20 bytes in a handshake
		/*byte[] handshakeMessage = new byte[68];
		int index = 0;
		//first byte is 0
		handshakeMessage[index] = 19;
		index++;
		//'BitTorrent protocol' string
		
		for(int i = 0; i<19; i++){
			handshakeMessage[index] = (byte) "BitTorrent protocol".charAt(i);
			index++;
		}
		
		//8 '0's
		for(int i = 0; i<8; i++){
			handshakeMessage[index] = 0;
			index++;
		}
		
		//20-byte torrent info hash
		for(int i = 0; i<20; i++){
			handshakeMessage[index] = ti.info_hash.get(i);
			index++;
		}
		
		//20-byte peer ID
		for(int i = 0; i<20; i++){
			handshakeMessage[index] = clientID[i];	
			index++;
		}
		
		os.write(handshakeMessage);*/
		
		/*int index = 0;
		byte[] handshake = new byte[68];
		
		handshake[index] = 0x13;
		index++;
		
		byte[] BTChars = { 'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't', ' ',
				'p', 'r', 'o', 't', 'o', 'c', 'o', 'l' };
		System.arraycopy(BTChars, 0, handshake, index, BTChars.length);
		index += BTChars.length;
		
		byte[] zero = new byte[8];
		System.arraycopy(zero, 0, handshake, index, zero.length);
		index += zero.length;
		
		System.arraycopy(ti.info_hash.array(), 0, handshake, index, ti.info_hash.array().length);
		index += ti.info_hash.array().length;
		
		System.arraycopy(clientID, 0, handshake, index, clientID.length);
		System.out.println(clientID.length);
		
		
		os.write(handshake);*/
		
		//This is where we receive Peer's handshake. The TA said via forum that problems that we have communicating with the peer might be the 
		// result of sending a bad handshake or receiving a bad handshake the following code tests the latter.
		dis.read(peerHandshake, 0, 68);
		
		//Just to see what the returning handshake looks like
		/*System.out.println("Peer handshake:");
		/*for(int i = 0; i < 68; i++)
			System.out.println(peerHandshake[i]);
		System.out.println("Done reading peerHandshake");*/
		
		//This code checks that the peerIDs matched. I added print statements such that I could check that the two matched byte for byte. 
		String handshakePeerID = new String (Arrays.copyOfRange(peerHandshake, 48, 68));
		//System.out.println(handshakePeerID);
		//System.out.println(peerID);
		 for(int i = 0; i<20; i++){
			 //System.out.println(handshakePeerID.charAt(i));
			 //System.out.println(peerID.charAt(i));
		    	if(handshakePeerID.charAt(i) != peerID.charAt(i)){
		    		LOGGER.log(Level.SEVERE, "Connected with the wrong peer.");
		    		TCPSocket.close();
		    		return;
		    	}
		 }
		 
		 //Not sure what this chunk was for. 
		 /*MessageDigest digest = null;
		 digest = MessageDigest.getInstance("SHA-1");
		 digest.update(peerHandshake);
		 byte[] info_hash_of_response = digest.digest();*/
		 
		 //The commented out chunk was used in order to test that the hashes were matching byte for byte. According to my tests, this program
		 //was able to receive a valid handshake from the peer. The TA then said that if the problem's not the handshake, then it's the way 
		 //subsequent messages were sent.
		byte[] info_hash_of_response = Arrays.copyOfRange(peerHandshake, 28, 48);
		/*for(int i = 0; i<20; i++){
			System.out.println(info_hash_of_response[i]);
			System.out.println(ti.info_hash.array()[i]);
	    	if(info_hash_of_response[i] != ti.info_hash.array()[i]){
	    		LOGGER.log(Level.SEVERE, "Connected with the wrong peer.");
	    		TCPSocket.close();
	    		return;
	    	}
	 	}*/
		
		if (!Arrays.equals(info_hash_of_response, ti.info_hash.array())){
			LOGGER.log(Level.SEVERE, "Peer connection issue: info hashes do not match");
    		TCPSocket.close();
    		return;
	    }
		
		//Sending interested message
		LOGGER.info("Sending Interested message" );
		
		byte [] interested = {0, 0, 0, 1, 2};
		os.write(interested);
		
		// When I run the code, this is where the program chokes. After sending that interested message, it's suppose to wait for
		// the peer's unchoke message. However, if I run the code, I'll be getting an endless stream of "messages" of length -1.
		LOGGER.info("Awaiting peer response");
		
		int downloaded = 0, index = 0, length;
		boolean requestable = false;
		byte [] request = new byte[17],  requestStart= {0, 0, 0, 13, 6}, indexBytes, offset, lengthBytes;
		System.arraycopy(requestStart, 0, request, 0, requestStart.length);
		int left = ti.piece_length;
		
		while (downloaded != ti.file_length) {
			message_length = dis.readInt();
			System.out.println("Message length: " + message_length);
			if(message_length == 0){
				System.out.println("keep alive");
			}
			/*if (dis.available()<=0){
				continue;
			}*/
			else {
				id = dis.readByte();
				System.out.println("id: " + id);
				switch (id) {
				case 0:
					if(message_length != 1){
						continue;
					}
					System.out.println("peer choked");
					requestable = false;
					break;
				case 1:
					if(message_length != 1){
						continue;
					}
					System.out.println("peer unchoked");
					requestable = true;
					break;
				case 5:
					byte[] bitfieldBytes = new byte[message_length-1];
					for(int i = 0; i<bitfieldBytes.length; i++){
						bitfieldBytes[i] = dis.readByte();
					}
					
					boolean[] bitfield = convert(bitfieldBytes, ti.piece_hashes.length);
					System.out.println("Total number of pieces: " + ti.piece_hashes.length);
					int pieceCount = 0;
					//this.bitfield = bitfield;
					for(int i = 0; i<bitfield.length; i++){
						if(bitfield[i] == true){
							pieceCount++;
						}
					}
					System.out.println("This peer has " + pieceCount + " of them");
					System.out.println();
					
					//Here is where I was having trouble. The code I left uncommented was the code I was using before. This little chunk was suppose
					//to send an interested message. The commented out chunks were other attempt versions. I think the problem is here in which 
					//I need to find a way to send a message such that it doesn't cause problems for me in the future.
					
					//os.writeByte(1);
					//os.writeInt( 2 );
					
					//os.writeInt(1);
					//os.writeByte( 2 );
					
					os.write(interested);
					break;
				case 7:
					System.out.println("Download and verify piece");
				}
			}
			if(requestable) {
				indexBytes = ByteBuffer.allocate(4).putInt(index).array();
				System.arraycopy(indexBytes, 0, request, 5, 4);
				offset = ByteBuffer.allocate(4).putInt(ti.piece_length - left).array();
				System.arraycopy(offset, 0, request, 9, 4);
				
				if(left > 16384){
					//make a request for a piece of length 16384
					length = 16384;
				}else{
					//make a request for a piece of length however many bytes are left
					length = left;
				}
				
				lengthBytes = ByteBuffer.allocate(4).putInt(length).array();
				System.arraycopy(lengthBytes, 0, request, 13, 4);
				
				os.write(request);
			}
		}
		/*boolean chokingUs = true;
		boolean connected = true;
		System.out.println("reading messages");
		while(connected){			
				message_length = dis.readInt();
				System.out.println("Read int");
				if(message_length == 0){
					System.out.println("keep alive");
					return;
				}
				byte id = dis.readByte();
				if(message_length == 1){
					//choking message
					if(id == 0){
						System.out.println("choking us");
						chokingUs = true;
					}
					//unchoking message
					else if(id == 1){
						System.out.println("no longer choking us");
						chokingUs = false;
						//this.getPiece(0);
					}
					//interested message
					else if(id == 2){
						System.out.println("interested in us");
					}
					//not interested message
					else if(id == 3){
						System.out.println("not interested in us");
					}
				}
				//have message
				else if(message_length == 5 && id == 4){
					System.out.println("have = " + dis.readInt());
					
				}
				else{
					if(id == 5){
						System.out.println(dis.available());

						byte[] bitfieldBytes = new byte[message_length-1];
						for(int i = 0; i<bitfieldBytes.length; i++){
							bitfieldBytes[i] = dis.readByte();
						}
						
						boolean[] bitfield = convert(bitfieldBytes, ti.piece_hashes.length);
						System.out.println("Total number of pieces: " + ti.piece_hashes.length);
						int pieceCount = 0;
						//this.bitfield = bitfield;
						for(int i = 0; i<bitfield.length; i++){
							if(bitfield[i] == true){
								pieceCount++;
							}
						}
						System.out.println("This peer has " + pieceCount + " of them");
						System.out.println();
						os.write(interested);
					}
				}
			System.out.println(dis.available());
			if(pieceDesired != -1 && !chokingUs){
				int pieceNumber = 0;
				int requestLength = 16384;
				int left = ti.piece_length;
				byte[] request = new byte[17];
				
				request[0] = 0;
			    request[1] = 0;
			    request[2] = 0;
			    request[3] = 13;
			    request[4] = 6;
			    
			    byte[] pNBytes = ByteBuffer.allocate(4).putInt(pieceNumber).array();
			    //PieceNumberBytes
			    byte[] offsetBytes, lengthBytes;
			    request[5] = pNBytes[0]; request[6] = pNBytes[1]; request[7] = pNBytes[2]; request[8] = pNBytes[3];
			    
			    byte[] response = new byte[100]; //first subpiece
				byte[] finalPiece = new byte[ti.piece_length];
				
				while(left > 0){
					
					if(left > 16384){
						//make a request for a piece of length 16384
						requestLength = 16384;
					}else{
						//make a request for a piece of length however many bytes are left
						requestLength = left;
					}
					
					offsetBytes = ByteBuffer.allocate(4).putInt(ti.piece_length - left).array();
				    
				    request[9] = offsetBytes[0]; request[10] = offsetBytes[1]; request[11] = offsetBytes[2]; request[12] = offsetBytes[3];
				    
				    lengthBytes = ByteBuffer.allocate(4).putInt(requestLength).array();
				    
				    request[13] = lengthBytes[0];
				    request[14] = lengthBytes[1];
				    request[15] = lengthBytes[2];
				    request[16] = lengthBytes[3];
				    
				    os.write(request);
				    os.flush();
				  	
				  	dis.read(response, 0, 100); // TODO: Make sure it is not a request message!
				  	if (response == null){
						System.out.println("Message is null");
						return;
					}
					for(int i = 0; i<response.length; i++){
						if(0 != 0){
							if(i<0){
								System.out.print(String.format("%02x", response[i])+ " ");					
							}
							if(i == 0){
								System.out.print(".....");
							}
							if(i>response.length-0){
								System.out.print(String.format("%02x", response[i])+ " ");
							}
						} else{
							System.out.print(String.format("%02x", response[i])+ " ");
						}
					}
					System.out.println();
				  	
				  	for(int i = 0; i < requestLength; i++){
				  		finalPiece[ti.piece_length - left + i] = response[i + 13];
				  	}
				  	
				    
					left = left - 16384;
					response = null;
				}

			}
		}*/	
		TCPSocket.close();
	}
	
	public static boolean[] convert(byte[] bits, int significantBits) {
		boolean[] retVal = new boolean[significantBits];
		int boolIndex = 0;
		for (int byteIndex = 0; byteIndex < bits.length; ++byteIndex) {
			for (int bitIndex = 7; bitIndex >= 0; --bitIndex) {
				if (boolIndex >= significantBits) {
					// Bad to return within a loop, but it's the easiest way
					return retVal;
				}

				retVal[boolIndex++] = (bits[byteIndex] >> bitIndex & 0x01) == 1 ? true
						: false;
			}
		}
		return retVal;
	}
	
/*private int getDownloaded() 
{
	return this.downloaded;
}

synchronized void addDownloaded(int downloaded) {
	LOGGER.info("Amount downloaded = " + this.downloaded);
	this.downloaded += downloaded;		
}*/


}
