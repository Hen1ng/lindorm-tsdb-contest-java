package com.alibaba.lindorm.contest.util;

import java.math.RoundingMode;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

public class Lists {

    public static <T> List<List<T>> partition(List<T> list, int size) {
        return (List)(new Partition(list, size));
    }

    private static class Partition<T> extends AbstractList<List<T>> {
        final List<T> list;
        final int size;

        Partition(List<T> list, int size) {
            this.list = list;
            this.size = size;
        }

        public List<T> get(int index) {
            int start = index * this.size;
            int end = Math.min(start + this.size, this.list.size());
            return this.list.subList(start, end);
        }

        public int size() {
            return divide(this.list.size(), this.size, RoundingMode.CEILING);
        }

        public boolean isEmpty() {
            return this.list.isEmpty();
        }
    }


    public static int divide(int p, int q, RoundingMode mode) {
        if (q == 0) {
            throw new ArithmeticException("/ by zero");
        } else {
            int div = p / q;
            int rem = p - q * div;
            if (rem == 0) {
                return div;
            } else {
                int signum = 1 | (p ^ q) >> 31;
                boolean increment;
                switch (mode) {
                    case UNNECESSARY:
                        checkRoundingUnnecessary(rem == 0);
                    case DOWN:
                        increment = false;
                        break;
                    case FLOOR:
                        increment = signum < 0;
                        break;
                    case UP:
                        increment = true;
                        break;
                    case CEILING:
                        increment = signum > 0;
                        break;
                    case HALF_DOWN:
                    case HALF_UP:
                    case HALF_EVEN:
                        int absRem = Math.abs(rem);
                        int cmpRemToHalfDivisor = absRem - (Math.abs(q) - absRem);
                        if (cmpRemToHalfDivisor == 0) {
                            increment = mode == RoundingMode.HALF_UP || mode == RoundingMode.HALF_EVEN & (div & 1) != 0;
                        } else {
                            increment = cmpRemToHalfDivisor > 0;
                        }
                        break;
                    default:
                        throw new AssertionError();
                }

                return increment ? div + signum : div;
            }
        }
    }
    static void checkRoundingUnnecessary(boolean condition) {
        if (!condition) {
            throw new ArithmeticException("mode was UNNECESSARY, but rounding was necessary");
        }
    }
}
