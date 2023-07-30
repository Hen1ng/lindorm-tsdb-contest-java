package com.alibaba.lindorm.contest.util;

public class Pair<L, R> {
    private L left;
    private R right;

    private Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

    public static <L, R> Pair of(L left, R right) {
        return new Pair(left, right);
    }
}
