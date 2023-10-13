/*******************************************************************************
 * Copyright (c) 2013 Michael Kutschke.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Michael Kutschke - initial API and implementation
 ******************************************************************************/
package com.alibaba.lindorm.contest.compress.doublecompress;

import java.util.Arrays;

public class DfcmPredictor {

    private long[] table;
    private int dfcm_hash;
    private long lastValue;

    public void clear() {
        Arrays.fill(table, 0);
        dfcm_hash = 0;
        lastValue = 0;
    }

    public DfcmPredictor(int logOfTableSize) {
        table = new long[1024];
    }

    public long getPrediction() {
        return table[dfcm_hash] + lastValue;
    }

    public void update(long true_value) {
        table[dfcm_hash] = true_value - lastValue;
        dfcm_hash = (int) (((dfcm_hash << 2) ^ ((true_value - lastValue) >> 40)) &
                (table.length - 1));
        lastValue = true_value;
    }

}
