import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.ArrayList;
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

	public static void main(String[] args) throws URISyntaxException, IOException, BencodingException 
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
		String ip="", stringid, key;
		
		trackerResponse = getRequest(tInfo);
		
		@SuppressWarnings("unchecked") //decoding Tracker response
		HashMap<ByteBuffer, Object> response = (HashMap<ByteBuffer, Object>)Bencoder2.decode(trackerResponse);
		
		
		//More plagarized code below
		ArrayList<Peer> p = new ArrayList<Peer>();
		
		//extracting  interval
		int interval = (Integer)response.get(ByteBuffer.wrap(new byte[] {'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' }));
		System.out.println(interval);
		
		@SuppressWarnings("unchecked")
		List<Map<ByteBuffer, Object>> objectList = (List<Map<ByteBuffer, Object>>) response.get( ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', 's' }));
		for (Map<ByteBuffer, Object> object : objectList) {
			System.out.println("NEW PEER");
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
			stringid = new String(peerId);
			System.out.println("PeerId -> " + stringid);
			System.out.println("Port -> "+ port);
	        System.out.println("IP-> " + ip);
		}
		return p;
	}
	
	private static ArrayList<Peer> getRUPeer(ArrayList<Peer> peerlist) {
		ArrayList<Peer> RUPeer = new ArrayList<Peer>();
		String peerIdString;
		for (Peer p: peerlist){
			peerIdString = p.getStringPeerId();
			if(peerIdString.charAt(0)=='R' && peerIdString.charAt(1)=='U')
				RUPeer.add(p);
		}
		return RUPeer;		
	}

	public static byte[] getRequest(TorrentInfo tInfo) throws IOException, URISyntaxException {
		byte[] response;
		int contentLength; 
		URL key = makeKey(tInfo);
		HttpURLConnection connection = (HttpURLConnection)key.openConnection();
		connection.setRequestMethod("GET");
		//notetoself: change variable names to avoid plagarism
		DataInputStream dis = new DataInputStream(connection.getInputStream());
		contentLength =connection.getContentLength(); //variable changed here
		response = new byte[contentLength];
		dis.readFully(response); // Tracker returns object here
		return response;
	}
	
	public static String get_string(ByteBuffer b){ 
		String s = new String(b.array());
		return s;
	}
		
	private static URL makeKey(TorrentInfo ti) throws URISyntaxException, MalformedURLException {
		byte[] hash = ti.info_hash.array();
		URI uri = ti.announce_url.toURI();
		String stringKey = uri.getPath() + "?info_hash=";
		for(byte b : hash)
			stringKey = stringKey + "%" + String.format("%02X", b);
		stringKey = stringKey + "&peer_id=" + generatePeerId() + "&port=6881&uploaded=0&downloaded=0&left=" + ti.file_length + "&event=started";
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
/*private int getDownloaded() 
{
	return this.downloaded;
}

synchronized void addDownloaded(int downloaded) {
	LOGGER.info("Amount downloaded = " + this.downloaded);
	this.downloaded += downloaded;		
}

private static void peerHandshake() {
	BufferedReader br = new BufferedReader(new InputStreamReader(TCPSocket.getInputStream()));
	byte[] reserved = new byte[8];
	DataOutputStream os = new DataOutputStream(TCPSocket.getOutputStream());	
	Socket TCPSocket = new Socket();
	String protocolId = "BitTorrent Protocol", peerHandshake, peerResponse;
	
	Arrays.fill( reserved, (byte) 0 );
	os.writeByte(1);
	os.writeBytes(protocolId);
	os.write(reserved, 0, reserved.length());
	// write SHA1 hash and peerId
	peerHandshake = br.readLine();
	//Sending interested message
	out.writeByte( 1 );
	out.writeInt( 2 );
	peerResponse = br.readLine();
}*/
}
