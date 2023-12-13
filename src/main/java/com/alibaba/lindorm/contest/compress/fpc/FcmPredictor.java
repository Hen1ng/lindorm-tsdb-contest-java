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
package com.alibaba.lindorm.contest.compress.fpc;

import java.util.Arrays;

public class FcmPredictor {

    public static final ThreadLocal<long[]> LONG_ARRAY_THREAD_LOCAL = ThreadLocal.withInitial(() -> new long[1024]);
    private long[] table;
    private int fcm_hash;

    public void clear() {
        Arrays.fill(table, 0);
        fcm_hash = 0;
    }

    public FcmPredictor(int logOfTableSize) {
        table = LONG_ARRAY_THREAD_LOCAL.get();
        Arrays.fill(table, 0);
    }

    public long getPrediction() {
        return table[fcm_hash];
    }

    public void update(long true_value) {
        table[fcm_hash] = true_value;
        fcm_hash = (int) (((fcm_hash << 6) ^ (true_value >> 48)) & (table.length - 1));
    }

}
