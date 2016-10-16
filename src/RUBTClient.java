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
				ArrayList<Peer> peers = getListOfPeersHttpUrl(tInfo);
				
				LOGGER.info("Retrieved peers. Extracting peers with -RU prefix");
				
				Peer RUPeer = getRUPeer(peers);
				
				if (RUPeer == null) 
				{
					LOGGER.log(Level.SEVERE, "There is no eligible peer to connect with.");
					System.exit(1);
				}
				LOGGER.info("Extracted appropriate peers. Performing handshake" );
				peerHandshake(RUPeer, tInfo);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BencodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
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
	
	private static void peerHandshake(Peer p, TorrentInfo ti) throws IOException, BencodingException, NoSuchAlgorithmException {
		//byte id;
		int message_length, pieceDesired=0;
		Socket TCPSocket = new Socket(p.ip, p.port);
		byte[] reserved = new byte[8], peerHandshake = new byte[68], length = new byte[4];
		
		//BufferedReader br = new BufferedReader(new InputStreamReader(TCPSocket.getInputStream()));
		DataInputStream dis = new DataInputStream(TCPSocket.getInputStream());
		DataOutputStream os = new DataOutputStream(TCPSocket.getOutputStream());
		String protocolId = "BitTorrent Protocol", peerID = p.getStringPeerId(), peerResponse;
		
		Arrays.fill( reserved, (byte) 0 );
		os.writeByte(19);
		os.writeBytes(protocolId);
		os.write(reserved, 0, reserved.length);
		byte[] hash = ti.info_hash.array();
		os.write(Bencoder2.encode((ByteBuffer.wrap(hash))));
		System.out.println(new String(clientID));
		os.write(clientID);
		

		//1+19+8+20+20 bytes in a handshake
		/*byte[] handshakeMessage = new byte[68];
		int index = 0;
		//first byte is 0
		handshakeMessage[index] = 0x13;
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

		/*byte[] handshake = new byte[68];
		
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
		*/
		
		//os.write(handshakeMessage);
		
		dis.read(peerHandshake, 0, 68);
		/*System.out.println("Peer handshake:");
		for (int i = 0; i < peerHandshake.length; i++)
		{
			System.out.println(peerHandshake[i]);
		}*/
		
		/*for(int i = 0; i < 68; i++)
			System.out.println(peerHandshake[i]);
		System.out.println("Done reading peerHandshake");
		for (int i = 0; i < 68; i++ )
			System.out.println(ti.info_hash.array()[i]);
		System.out.println(new String(peerHandshake));*/
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
		 /*MessageDigest digest = null;
		 digest = MessageDigest.getInstance("SHA-1");
		 
		digest.update(peerHandshake);*/
		//byte[] info_hash_of_response = digest.digest();
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
		System.out.println("Info hashes match.");
		//Sending interested message
		LOGGER.info("Sending Interested message" );
		
		/*os.writeByte(1);
		os.writeInt( 2 );*/
		byte [] interested = {0, 0, 0, 1, 2};
		os.write(interested);
		/*ByteBuffer buf = ByteBuffer.allocate(8); // two 4-byte integers
		buf.putInt( 1 ).putInt( 2 );
		buf.flip();
		byte[] b = buf.array();
		os.write(b);*/
		LOGGER.info("Awaiting peer response");
		/*boolean requestable = false;
		do {
			message_length = dis.read(length);
			System.out.println(message_length);
			/*if(message_length != 0 && message_length != 1 && message_length != 5 && message_length != 13 ) { 
				System.out.println("Junk response");
				continue;
			}*/
			/*System.out.println("Message length: " + message_length);
			if(message_length == 0){
				System.out.println("keep alive");
			}
			id = dis.readByte();
			System.out.println("id: " + id);
			else if(message_length == 1){
				//choking message
				if(id == 0){
					System.out.println("peer choked");
				}
				//unchoking message
				else if(id == 1){
					System.out.println("peer unchoked");
					requestable = true;
				}
			}
			else{
				if(id == 5){
					System.out.println(in.available());

					byte[] bitfieldBytes = new byte[length-1];
					for(int i = 0; i<bitfieldBytes.length; i++){
						bitfieldBytes[i] = this.in.readByte();
					}
					
					boolean[] bitfield = convert(bitfieldBytes, Download.torrent.piece_hashes.length);
					System.out.println("Total number of pieces: " + Download.torrent.piece_hashes.length);
					int pieceCount = 0;
					this.bitfield = bitfield;
					for(int i = 0; i<this.bitfield.length; i++){
						if(bitfield[i] == true){
							pieceCount++;
						}
					}
					System.out.println("This peer has " + pieceCount + " of them");
					System.out.println();
					out.write(interested);
				}
		} while(!requestable);*/
		
		
		boolean chokingUs = true;
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
		}	
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
