/**
 * This code is released under the
 * Apache License Version 2.0 http://www.apache.org/licenses/.
 *
 * (c) Daniel Lemire, http://lemire.me/en/
 */
package com.alibaba.lindorm.contest.compress.intcodec2.integercompression.differential;

import com.alibaba.lindorm.contest.compress.intcodec2.integercompression.IntWrapper;

/**
 * Helper class to compose schemes.
 * 
 * @author Daniel Lemire
 */
public class SkippableIntegratedComposition implements
        SkippableIntegratedIntegerCODEC {
    SkippableIntegratedIntegerCODEC F1, F2;

    /**
     * Compose a scheme from a first one (f1) and a second one (f2). The first
     * one is called first and then the second one tries to compress whatever
     * remains from the first run.
     * 
     * By convention, the first scheme should be such that if, during decoding,
     * a 32-bit zero is first encountered, then there is no output.
     * 
     * @param f1
     *            first codec
     * @param f2
     *            second codec
     */
    public SkippableIntegratedComposition(SkippableIntegratedIntegerCODEC f1,
            SkippableIntegratedIntegerCODEC f2) {
        F1 = f1;
        F2 = f2;
    }



    @Override
    public String toString() {
        return F1.toString() + " + " + F2.toString();
    }

    @Override
    public void headlessCompress(int[] in, IntWrapper inpos, int inlength,
            int[] out, IntWrapper outpos, IntWrapper initvalue) {
        if (inlength == 0)
            return;
        final int init = inpos.get();
        int outposInit = outpos.get();

        F1.headlessCompress(in, inpos, inlength, out, outpos, initvalue);
        if (outpos.get() == outposInit) {
            out[outposInit] = 0;
            outpos.increment();
        }
        inlength -= inpos.get() - init;
        F2.headlessCompress(in, inpos, inlength, out, outpos, initvalue);
    }

    @Override
    public void headlessUncompress(int[] in, IntWrapper inpos, int inlength,
            int[] out, IntWrapper outpos, int num, IntWrapper initvalue) {
        if (inlength == 0)
            return;
        int init = inpos.get();
        F1.headlessUncompress(in, inpos, inlength, out, outpos,num,initvalue);
        if (inpos.get() == init) {
      	  inpos.increment();
        }
        inlength -= inpos.get() - init;

        num -= outpos.get();
        F2.headlessUncompress(in, inpos, inlength, out, outpos,num,initvalue);
    }

}
