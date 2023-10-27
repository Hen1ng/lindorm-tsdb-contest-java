package com.alibaba.lindorm.contest.util;

public class Context {
    private int accessTimes;
    private int hitTimes;

    public Context(int accessTimes, int hitTimes) {
        this.accessTimes = accessTimes;
        this.hitTimes = hitTimes;
    }

    public int getAccessTimes() {
        return accessTimes;
    }

    public void setAccessTimes(int accessTimes) {
        this.accessTimes = accessTimes;
    }

    public int getHitTimes() {
        return hitTimes;
    }

    public void setHitTimes(int hitTimes) {
        this.hitTimes = hitTimes;
    }
    public void addAccessTime(){
        this.accessTimes++;
    }
    public void addHitTime(){
        this.hitTimes++;
    }
}
