/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.stega.bpcs;

import java.util.BitSet;
import java.util.Vector;

/**
 * Representation of an ARGB image in a collection of BitPlaneBlock.
 * If the img size (width or length) is not divisible by eight, only parse
 * the top-left subimage which size (width and length) is divisible by eight
 *
 * For example, if an image has a size of 9x19, only the top-left 8x16 byte
 * will be converted into BitPlanes. The rest is ignored and won't have it
 * values changed.
 * 
 * @author USER
 */
public class imgBPs {

    public static final int BIT_IN_BYTE = 8;
    public static final int BYTE_IN_BP = 8;
    public static final int BIT_IN_BP = BIT_IN_BYTE * BYTE_IN_BP;
    public static final int BP_BIT_SIDE_LEN = 8;
    
    /**
     * 2D MATRIX INDEXING (The number are printed 2d array)
     *         0---j---4    0---Y---4
     *      0 [1 0 0 0 0]    0 [1 0 0 0 0]
     *      | [0 1 0 0 0]    | [0 1 0 0 0]
     *      i [0 0 1 0 0]    X [0 0 1 0 0]
     *      | [0 0 0 1 0]    | [0 0 0 1 0]
     *      5 [0 0 0 0 1]    5 [0 0 0 0 1]
     * Accessing a 2D array = arr[x][y] / arr[i][j]
     * i ~ X ~ width
     * j ~ Y ~ height
     */
    public static final int BP_DEPTH = 32;
    public static final int BP_LENGTH = 8;
    
    /**
     * An 8x8x32 bit block represented in an array of 8 BitSet.
     * Each BitSet is a representation of an 8x8 BitPlane.
     * The 32 BitSet layer correspond to every BitPlane in an ARGB image
     * Layer 0-7 = B, 8-15 = G, 16-23 = R, 24-31 = A
     */
    private class BPBlocks {
        
        private final BitSet[] block;

        public BPBlocks() {
            this.block = new BitSet[BP_DEPTH];
            for(int i=0; i<block.length; i++) {
                block[i] = new BitSet(BIT_IN_BP);
            }
        }

        public void setColor(int x, int y, int color) {
            assert x<BP_LENGTH && x>=0 && y<BP_LENGTH && y>=0;
            for(int i=0; i<BP_DEPTH; i++) {
                if((color & 1) == 1) {
                    block[i].set(x*BP_LENGTH + y);
                }
                color >>>= 1;
            }
            assert color == 0;
        }
        public int getColor(int x, int y) {
            int color = 0;
            for(int i=BP_DEPTH-1; i>=0; i--) {
                color <<= 1;
                if(block[i].get(x*BP_LENGTH + y)) {
                    color |= 1;
                }
            }
            return color;
        }

        public BitSet getBitPlane(int depth) {
            assert depth>=0 && depth<BP_DEPTH;
            return block[depth];
        }
        public void setBitPlane(BitSet bs, int depth) {
            assert depth>=0 && depth<BP_DEPTH;
            block[depth] = bs;
        }
    }

    private final Vector<Vector<BPBlocks>> data;
    private final int xmax, ymax;

    public imgBPs(int xmax, int ymax) {
        data = new Vector<>();
        assert xmax > BP_LENGTH && ymax > BP_LENGTH;
        int i, j = 0;
        for(i=BP_LENGTH; i<xmax; i+=BP_LENGTH) {
            data.add(new Vector<>());
            for(j=BP_LENGTH; j<ymax; j+=BP_LENGTH) {
                data.lastElement().add(new BPBlocks());
            }
        }
        this.xmax = i-BP_LENGTH;
        this.ymax = j-BP_LENGTH;
    }

    public boolean inRange(int x, int y) {
        return x>=0 && x<xmax && y>=0 && y<ymax;
    }

    public int getBlockWidth() {
        return xmax / (int)BP_LENGTH;
    }
    public int getBlockHeight() {
        return ymax / (int)BP_LENGTH;
    }

    public void setColor(int absX, int absY, int color) {
        assert absX<xmax && absX>=0 && absY<ymax && absY>=0;
        int blockX = absX / (int)BP_LENGTH;
        int blockY = absY / (int)BP_LENGTH;
        assert blockX<getBlockWidth() && blockY<getBlockHeight();
        int relX = absX % BP_LENGTH;
        int relY = absY % BP_LENGTH;
        data.get(blockX).get(blockY).setColor(relX, relY, color);
    }
    public int getColor(int absX, int absY) {
        assert absX<xmax && absX>=0 && absY<ymax && absY>=0;
        int blockX = absX / (int)BP_LENGTH;
        int blockY = absY / (int)BP_LENGTH;
        assert blockX<getBlockWidth() && blockY<getBlockHeight();
        int relX = absX % BP_LENGTH;
        int relY = absY % BP_LENGTH;
        return data.get(blockX).get(blockY).getColor(relX, relY);
    }

    public BitSet getBitPlane(int blockX, int blockY, int depth) {
        assert blockX>=0 && blockX<getBlockWidth();
        assert blockY>=0 && blockY<getBlockHeight();
        assert depth>=0 && depth<BP_DEPTH;
        return data.get(blockX).get(blockY).getBitPlane(depth);
    }
    public void setBitPlane(BitSet bs, int blockX, int blockY, int depth) {
        assert blockX>=0 && blockX<getBlockWidth();
        assert blockY>=0 && blockY<getBlockHeight();
        assert depth>=0 && depth<BP_DEPTH;
        data.get(blockX).get(blockY).setBitPlane(bs, depth);
    }

    /**
     * Convert bit encoding in imgBPs into Canonical Gray Code.
     */
    public void toCGC() {
        for (Vector<BPBlocks> vec : data) {
            for (BPBlocks p_block : vec) {
                for (int i=BP_DEPTH-1; i>0; i--) {
                    BitSet cur_block = p_block.block[i];
                    BitSet prev = p_block.block[i-1];
                    cur_block.xor(prev);
                }
            }
        }
    }

    /**
     * Convert bit encoding in imgBPs into Pure Byte Code.
     */
    public void toPBC() {
        for (Vector<BPBlocks> vec : data) {
            for (BPBlocks p_block : vec) {
                for (int i=1; i<BP_DEPTH; i++) {
                    BitSet cur_block = p_block.block[i];
                    BitSet prev = p_block.block[i-1];
                    cur_block.xor(prev);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder();
        for(int i=0; i<getBlockWidth(); i++) {
            for(int j=0; j<getBlockHeight(); j++) {
                for (int k=0; k<BP_DEPTH; k++) {
                    res.append(String.format("%d, %d, %d: ", i, j, k));
                    res.append(getBitPlane(i, j, k).toString());
                    res.append("\n");
                }
            }
        }
        return res.toString();
    }
}

