package com.alibaba.lindorm.contest.util;

import java.io.File;

public class RestartUtil {

    public static boolean isFirstStart(File file) {
        return !file.exists();
    }
}
