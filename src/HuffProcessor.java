
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 * @author Nhu Do and Olivia Ratliff --> compsci 201 fall 2018
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if (bits!= HUFF_TREE) {  //wrong
			throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	
	/**
	 * helper method
	 * reading the tree to decompress
	 * same as what is used to compress (written during compression)
	 * preoder traversal
	 * @param in
	 * @return tree with decompressed bits
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		//read a single bit
		int bit = in.readBits(1);
		
		if (bit == -1) {
			throw new HuffException("reading bits failed");
		}
		
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0, left, right );
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0);
		}
	}
	/**
	 * helper method
	 * reads bits from compressed file
	 * uses them to traverse root-to-leaf paths
	 * writes lead into the output file
	 * stop when PSEUDO_EOF
	 * 
	 * @param root
	 * @param in
	 * @param out
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		
		HuffNode current = root; 
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits ==0) current = current.myLeft;
				else current = current.myRight;
				
				if (current.myLeft  == null && current.myRight == null) {
					if(current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);//write bits for current.value;
						current = root; //starts back after leaf
					}
				}
			}
		}
		
	}
	
	
}