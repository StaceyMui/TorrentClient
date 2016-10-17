public class Peer {
	/** The peer id. */
	protected byte[] peerId;
	
	/** The port. */
	protected int port;
	
	/** The ip. */
	protected String ip;
	public Peer(byte[] peerId, int port, String ip) {
		this.peerId = peerId;
		this.port = port;
		this.ip = ip;
	}
	
	public String getStringPeerId() {
        return new String(peerId);
    }
}


	