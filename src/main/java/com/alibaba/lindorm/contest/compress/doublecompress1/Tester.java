package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Tester {
	
	static void datasetReport (String dataset) throws IOException {
		String[] names = Util.allFileNames(dataset);
		int namesLen = names.length;
		String[][] ret = new String[namesLen][5];
		for(int i = 0; i < namesLen; i++) {
			double runTotal = 0;
			double runCount = 0;
			double intCount = 0;
			double valCount = 0;
			double diffSum = 0;
			double diffSumErr = 0;
			double[] data = Reader.readRaw(Util.rawPathify(names[i], dataset));
			int dataLen = data.length;
			double prev = 0;
			double currRun = 1;
			for(int j = 0; j < dataLen; j++) {
				double d = data[j];
				double remainder = d - Math.round(d);
				if(remainder == 0) {
					intCount++;
				}
				if(j > 0) {
					diffSum += d - prev;
					if(d == prev) {
						currRun++;
					} else {
						runTotal += currRun;
						runCount++;
						currRun = 1;
					}
				}
				valCount++;
				prev = d;
			}
			double diffAvg = diffSum/dataLen;
			for(int j = 0; j < dataLen; j++) {
				double d = data[j];
				if(j > 0) {
					diffSumErr += Math.pow(d - prev - diffAvg, 2);
				}
				prev = d;
			}
			double diffVar = diffSumErr/(dataLen-1);
			double smoothness = Math.sqrt(diffVar)/Math.abs(diffAvg);
			double intPercentage = intCount/valCount;
			double runAvg = runTotal/runCount;
//			System.out.println("Smoothness: " + smoothness);
//			System.out.println("Average Run Length: " + runAvg);
//			System.out.println("Integer Percentage: " + intPercentage);
			ret[i] = new String[] {dataset,
					names[i],
					String.valueOf(smoothness),
					String.valueOf(intPercentage),
					String.valueOf(runAvg)};
			System.out.println((i+1) + "/" + namesLen + " done");
		}
		makeCSV(ret, "Analyses/Dataset Reports/" + dataset + ".csv");
	}

	
	//Calculates and returns the compression ratio of the given file
	static String compressionReport (String name, String dataset) {
		double rawSize = Util.fileSize(Util.rawPathify(name,dataset));
		double compressedSize = Util.fileSize(Util.compressedPathify(name));
		double ratio = Math.round((compressedSize / rawSize) * 100.0) / 100.0;
		return String.valueOf(ratio);
	}

	/*Calculates and returns report statistics (file name, raw file size, 
	 * compression throughput, decompression throughput, CR, and compression 
	 * method) of the given file using the given compression method
	 */
	static String[] testReport (String name, String method, String dataset) throws IOException {
		String[] ret = new String[5];
		double[] data = Reader.readRaw(Util.rawPathify(name, dataset));
		long compressionStartTime = System.nanoTime();
		ByteBuffer compressed = Compressor.directCompress(data, method);
		long compressionEndTime = System.nanoTime();
		double[] decompressed = Compressor.directDecompress(compressed, method);
		long decompressionEndTime = System.nanoTime();
		Writer.writeCompressedToFile(compressed, name);
		String ratio = compressionReport(name,dataset);
		
		double fileSize = Util.fileSize(Util.rawPathify(name,dataset));
		double compressionTime = (compressionEndTime - compressionStartTime);
		double compressionThroughput = fileSize / compressionTime;
		double decompressionTime = (decompressionEndTime - compressionEndTime);
		double decompressionThroughput = fileSize / decompressionTime;
		
		compressionThroughput = Math.round(1000000000.0 * compressionThroughput * 100.0) / 100.0;
		decompressionThroughput = Math.round(1000000000.0 * decompressionThroughput * 100.0) / 100.0;
		
		ret[0] = name;
//		ret[1] = String.valueOf(Math.round(fileSize * 1024.0 * 100.0) / 100.0);
		ret[1] = String.valueOf(compressionThroughput);
		ret[2] = String.valueOf(decompressionThroughput);
		ret[3] = ratio;
		ret[4] = method;
		return ret;
	}
	
	static String[][] fullTestReport (String method, boolean verbose) throws IOException{
		return fullTestReport(method, verbose, "UCR");
	}

	/*Calculates and returns the test report for all files using the given 
	 * compression method.
	 */
	static String[][] fullTestReport (String method, boolean verbose, String dataset) throws IOException{
		
			String[] names = Util.allFileNames(dataset);
			
//			This block of code can be swapped with the first line to 
//			test only the first numToTest files:
			
//			String[] namesFull = Util.allFileNames();
//			int numToTest = 1000;
//			String[] names = new String[numToTest];
//			for(int i = 0; i < numToTest; i++) {
//				names[i] = namesFull[i];
//			}
			
			int len = names.length;
			double compressionThroughputTotal = 0;
			double decompressionThroughputTotal = 0;
			double CRTotal = 0;
			String[][] ret = new String[verbose ? len+2 : 2][6];
			System.out.println("Beginning testing!");
			ret[0] = new String[] {verbose ? "File Name" : "",  
					"Compression Throughput (mb/s)", 
					"Decompression Throughput (mb/s)",
					"Compression Ratio", 
					"Method"};
			for(int i = 0; i < len; i++) {
				String[] line = testReport(names[i],method,dataset);
				if(verbose) {
					ret[i+1] = line;
				}
				compressionThroughputTotal += Double.parseDouble(line[1]);
				decompressionThroughputTotal += Double.parseDouble(line[2]);
				CRTotal += Double.parseDouble(line[3]);
//				System.out.println(names[i]);
				System.out.println(method + Compressor.sprintzBlockSize + ": " + (i+1) + " / " + len + " tested");
			}
			int averageCompressionThroughput = (int) Math.round(compressionThroughputTotal / len);
			int averageDecompressionThroughput = (int) Math.round(decompressionThroughputTotal / len);
			double averageCR = Math.round((CRTotal / len) * 100.0) / 100.0;
			String[] averages = new String[] {"Average", 
					String.valueOf(averageCompressionThroughput),
					String.valueOf(averageDecompressionThroughput), 
					String.valueOf(averageCR),
					method};
			ret[verbose ? len+1 : 1] = averages;
			addToFullReport(averages, dataset);
			System.out.println("Done!");
			return ret;
			
		}
	
	static void addToFullReport(String[] averages, String dataset) throws IOException {
		String[] formattedAverage = new String[] {averages[4], 
				averages[1], 
				averages[2], 
				averages[3]};
		BufferedReader reader = new BufferedReader(new FileReader(new File("Analyses/Reports/" + dataset + "/Full Report.csv")));
		String row;
		String[][] currentReport = new String[12][4];
		int i = 0;
		while ((row = reader.readLine()) != null) {
		    currentReport[i] = row.split(",");
		    if(currentReport[i][0].equals(formattedAverage[0])) {
		    	currentReport[i] = formattedAverage;
		    }
		    i++;
		}
		reader.close();
		makeCSV(currentReport, "Analyses/Reports/" + dataset + "/Full Report.csv");
	}

//	Tests the correctness of the compression and decompression of all files 
//	using the given compression method.
	public static void testCorrectnessFull (String method) {
		String[] names = Util.allFileNames();
		int len = names.length;
		for(int i = 0; i < len; i++) {
			//System.out.println("Decompressing " + names[i]);
			testCorrectnessOneFile(names[i], method);
			System.out.println(method + ": " + (i+1) + " / " + len + " tested");
		}
	}
	
//	Tests the correctness of the compression and decompression of the given 
//	file using the given compression method. In the case that the expected 
//	result is not attained for a given double, the expected and actual double 
//	will be output.
	public static void testCorrectnessOneFile (String name, String method) {
		try {
			double[] raw = Reader.readRaw(Util.rawPathify(name));
			double[] decompressed = Compressor.directDecompress(Compressor.compressFile(name, method),method);
			int len = raw.length;
			boolean correct = true;
			for(int i = 0; i < len; i++) {
				if(Math.abs(raw[i] - decompressed[i]) > 0.0001) {
					System.out.println(name + " " + i + ": " + raw[i] + " != " + decompressed[i]);
					correct = false;
				}
			}
			System.out.println(correct);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

//	Turns a String[] into a comma separated CSV line.
	static String toCSVLine (String[] input) {
		int len = input.length;
		String ret = "";
		for(int i = 0; i < len; i++) {
			ret += input[i];
			ret += (i == len-1 ? "\n" : ",");
		}
		return ret;
	}

//	Writes the given String[][] to the file outputName as a CSV.
	static void makeCSV (String[][] input, String outputName) throws IOException {
		new File(outputName).delete();
		FileWriter writer = new FileWriter(outputName);
		for(String[] line : input) {
		  writer.write(toCSVLine(line));
		}
		writer.close();
	}
	
//	Generates a full test report for the given compression method.
	static void generateReport (String method, boolean verbose, String dataset) throws IOException {
		if(verbose) {
			makeCSV(fullTestReport(method, verbose, dataset), "Analyses/Reports/" + dataset + "/" + method + " Report.csv");
		}
		else {
			fullTestReport(method, verbose, dataset);
		}
	}

//	Generates a full test report for the given compression method, with the
//	(D)FCM level and Sprintz blocksize specified.
	static void generateReport (String method, boolean verbose, int level, int blockSize) throws IOException {
		Compressor.FCMLevel = level;
		Compressor.sprintzBlockSize = blockSize;
		if(method == "FCM" || method == "DFCM") {
			makeCSV(fullTestReport(method, verbose), "Analyses/Reports/" + method + level + " Report.csv");
		} else if (method == "Sprintz") {
			makeCSV(fullTestReport(method, verbose), "Analyses/Reports/" + method + blockSize + " Report.csv");
		} else {
			makeCSV(fullTestReport(method, verbose), "Analyses/Reports/" + method + " Report.csv");
		}
	}

}
