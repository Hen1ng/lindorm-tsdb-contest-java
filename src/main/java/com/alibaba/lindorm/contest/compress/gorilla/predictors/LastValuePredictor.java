package com.alibaba.lindorm.contest.compress.gorilla.predictors;


import com.alibaba.lindorm.contest.compress.gorilla.Predictor;

/**
 * Last-Value predictor, a computational predictor using previous value as a prediction for the next one
 *
 * @author Michael Burman
 */
public class LastValuePredictor implements Predictor {
    private long storedVal = 0;

    public LastValuePredictor() {}

    public void update(long value) {
        this.storedVal = value;
    }

    public long predict() {
        return storedVal;
    }
}
