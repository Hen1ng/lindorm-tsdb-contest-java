package com.alibaba.lindorm.contest.util;

import java.io.File;

public class RestartUtil {

    public static boolean IS_FIRST_START = false;

    public static void setFirstStart(File file) {
        IS_FIRST_START = !file.exists();
    }
}
