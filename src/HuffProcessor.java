import java.util.PriorityQueue;

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
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	//step 1: Determine the frequency of every eight-bit 
	//character/chunk in the file being compressed 
	public int[] readForCounts(BitInputStream in) {
		int[] ret = new int[ALPH_SIZE + 1];
		
		while(true) {
			int index = in.readBits(BITS_PER_WORD);
			if (index == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (index == PSEUDO_EOF) {
					ret[PSEUDO_EOF] = 1;
					break;
				}
				ret[index]++;
			}
		}
		return ret;
	}
	//step 2: From the frequencies, create the Huffman trie/tree 
	//used to create encodings
	public HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] != 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode newBoi = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(newBoi);
		}
		HuffNode root = pq.remove();
		
		return root;
	}
	//step 3: From the trie/tree, create the encodings for each 
	//eight-bit character chunk
	public String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		String initialPath = "";
		codingHelper(encodings, root, initialPath);

		return encodings;
	}
	
	public void codingHelper(String[] encodings, HuffNode root, String path) {
		
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		else {
			if (root.myLeft != null) codingHelper(encodings, root, path + "0");
			if (root.myRight != null) codingHelper(encodings, root, path + "1");
		}
		return;
	}
	//step 4: Write the magic number and the tree to the 
	//beginning/header of the compressed file
	public void writeHeader(HuffNode root, BitOutputStream out) {
		
		if (root.myLeft != null || root.myRight != null) {
			out.writeBits(1,  0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		
		if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1,  1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}
	//step 5:Read the file again and write the encoding for each 
	//eight-bit chunk, followed by the encoding for PSEUDO_EOF, then 
	//close the file being written
	public void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int current = in.readBits(8);
			String code = codings[current];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		
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
	 * helper method reading the tree to decompress
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
				if (bits == 0) current = current.myLeft;
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