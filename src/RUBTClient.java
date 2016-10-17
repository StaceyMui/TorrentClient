import java.io.DataOutputStream;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
		URL key = makeKey(tInfo, 0);
		HttpURLConnection connection = (HttpURLConnection)key.openConnection();
		connection.setRequestMethod("GET");
		//notetoself: change variable names to avoid plagarism
		DataInputStream inputStream = new DataInputStream(connection.getInputStream());
		contentLength =connection.getContentLength(); //variable changed here
		response = new byte[contentLength];
		inputStream.readFully(response); // Tracker returns object here
		return response;
	}
	
	public static void getRequest(TorrentInfo tInfo, int flag) throws IOException, URISyntaxException {
		URL key = makeKey(tInfo, flag);
		HttpURLConnection connection = (HttpURLConnection)key.openConnection();
		connection.setRequestMethod("GET");
	}
	
	public static String get_string(ByteBuffer b){ 
		String s = new String(b.array());
		return s;
	}
		
	private static URL makeKey(TorrentInfo ti, int flag) throws URISyntaxException, MalformedURLException {
		byte[] hash = ti.info_hash.array();
		if (flag == 0)
			clientID = generatePeerId();
		URI uri = ti.announce_url.toURI();
		String stringKey = uri.getPath() + "?info_hash=";
		for(byte b : hash)
			stringKey = stringKey + "%" + String.format("%02X", b);
		if(flag == 0 || flag == 1) {
			stringKey = stringKey + "&peer_id=" + clientID + "&port=6881&uploaded=0&downloaded=0&left=" + ti.file_length;
			if (flag == 1)
				stringKey = stringKey + "+ &event=started";
		}
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
	private static void peerHandshake(Peer p, TorrentInfo ti) throws IOException, BencodingException, NoSuchAlgorithmException, URISyntaxException {
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
		
		//This is where we receive Peer's handshake. The TA said via forum that problems that we have communicating with the peer might be the 
		// result of sending a bad handshake or receiving a bad handshake the following code tests the latter.
		dis.read(peerHandshake, 0, 68);
		
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
		 
		byte[] info_hash_of_response = Arrays.copyOfRange(peerHandshake, 28, 48);
		
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
		
		int downloaded = 0, index = 0, length = 16384, offset;
		boolean requestable = false;
		byte [] request = new byte[17],  requestStart= {0, 0, 0, 13, 6}, indexBytes, offsetBytes, lengthBytes, response = new byte[16400], piece = new byte[ti.piece_length], file = new byte[ti.file_length];
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
					
					//os.writeByte(1);
					//os.writeInt( 2 );
					
					//os.writeInt(1);
					//os.writeByte( 2 );
					
					os.write(interested);
					break;
				case 7:		
					getRequest(ti, 1);
					
					if(index == 0 && left == ti.piece_length) {
						
					}
					dis.read(response, 0, response.length); 
	
					System.arraycopy(response, 8, piece, ti.piece_length - left, length);
					/*message_length = dis.readInt();
					System.out.println("Index: " + message_length);
					message_length = dis.readInt();
					System.out.println("Begin: " + message_length);*/
					
					//dis.read(response);
					
					//for(int i = 0; i < length; i++)
						//piece[ti.piece_length - left + i] = response[i + 8];
					
					/*ti.piece_hashes[piece].duplicate().get(torrentFileHash);
					
					 MessageDigest digest = null;
					 digest = MessageDigest.getInstance("SHA-1");
					 digest.update(peerHandshake);
					 byte[] info_hash_of_response = digest.digest();*/
					 
					/*byte[] info_hash_of_response = Arrays.copyOfRange(peerHandshake, 28, 48);
					
					if (!Arrays.equals(info_hash_of_response, ti.info_hash.array())){
					
					System.out.println("\n Saved something");
					left -= length;
					
					if(left == 0){
						System.arraycopy(piece, 0, file, downloaded, piece.length);
						downloaded += length;
						index++;
						left = ti.piece_length;
					}*/
					/*System.out.println("\n requestable: " + requestable);
					System.out.println("index: " + index);
					System.out.println("left: " + left + "\n");*/
				}
			}
			if(requestable) {
				offset = ti.piece_length-left;
				System.out.println("\nSending request");
				System.out.println("index: " + index);
				System.out.println("offset: " + offset + "\n");
				indexBytes = ByteBuffer.allocate(4).putInt(index).array();
				System.arraycopy(indexBytes, 0, request, 5, 4);
				offsetBytes = ByteBuffer.allocate(4).putInt(ti.piece_length - left).array();
				System.arraycopy(offsetBytes, 0, request, 9, 4);
				
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
		System.out.println("Loop has ended");
		/*
				  	
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
