package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

	public static void main(String[] args) throws FileNotFoundException, IOException {
				
		try {
			String dataset = "UCR";
			Tester.generateReport("DFCM", false, dataset);
			Tester.generateReport("DFCMGorilla", false, dataset);
			Tester.generateReport("FCM", false, dataset);
			Tester.generateReport("FCMGorilla", false, dataset);
			Tester.generateReport("Gorilla", false, dataset);
			Tester.generateReport("GorillaRef", false, dataset);
			Tester.generateReport("Sprintz", false, dataset);
			Tester.generateReport("SprintzRef", false, dataset);
			Tester.generateReport("ToBinary", false, dataset);
			Tester.generateReport("ToInt", false, dataset);
			dataset = "UCI";
			Tester.generateReport("DFCM", false, dataset);
			Tester.generateReport("DFCMGorilla", false, dataset);
			Tester.generateReport("FCM", false, dataset);
			Tester.generateReport("FCMGorilla", false, dataset);
			Tester.generateReport("Gorilla", false, dataset);
			Tester.generateReport("GorillaRef", false, dataset);
			Tester.generateReport("Sprintz", false, dataset);
			Tester.generateReport("SprintzRef", false, dataset);
			Tester.generateReport("ToBinary", false, dataset);
			Tester.generateReport("ToInt", false, dataset);
			dataset = "argonne";
			Tester.generateReport("DFCM", false, dataset);
			Tester.generateReport("DFCMGorilla", false, dataset);
			Tester.generateReport("FCM", false, dataset);
			Tester.generateReport("FCMGorilla", false, dataset);
			Tester.generateReport("Gorilla", false, dataset);
			Tester.generateReport("GorillaRef", false, dataset);
			Tester.generateReport("Sprintz", false, dataset);
			Tester.generateReport("SprintzRef", false, dataset);
			Tester.generateReport("ToBinary", false, dataset);
			Tester.generateReport("ToInt", false, dataset);
			
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
