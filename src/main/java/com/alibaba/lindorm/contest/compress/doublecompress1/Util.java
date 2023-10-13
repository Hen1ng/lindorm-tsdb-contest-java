package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

public class Util {

//	Converts a double to a byte[].
	public static byte[] toByteArray(double value) {
	    byte[] bytes = new byte[8];
	    ByteBuffer.wrap(bytes).putDouble(value);
	    return bytes;
	}
	
//	Converts an int to a byte[].
	public static byte[] toByteArray(int value) {
	    byte[] bytes = new byte[4];
	    ByteBuffer.wrap(bytes).putInt(value);
	    return bytes;
	}
	
//	Converts a short to a byte[].
	public static byte[] toByteArray(short value) {
	    byte[] bytes = new byte[2];
	    ByteBuffer.wrap(bytes).putShort(value);
	    return bytes;
	}

//	Converts a byte[] to a double.
	public static double toDouble(byte[] bytes) {
		long bits = 0L;
		for (int i = 0; i < 8; i++) {
			bits = (bits << 8) + (bytes[i] & 0xff);
		}
		return Double.longBitsToDouble(bits);
	}
	
//	Xor's two byte arrays.
	public static byte[] xorByteArrays(byte[] b1, byte[] b2) {
		int len = b1.length;
		byte[] ret = new byte[len];
		for(int i = 0; i < len; i++) {
			ret[i] = (byte) (b1[i] ^ b2[i]);
		}
		return ret;
	}
	
//	Or's two byte arrays.
	public static byte[] orByteArrays(byte[] b1, byte[] b2) {
		int len = b1.length;
		byte[] ret = new byte[len];
		for(int i = 0; i < len; i++) {
			ret[i] = (byte) (b1[i] | b2[i]);
		}
		return ret;
	}
	
//	Xor's two doubles.
	public static double xorDoubles(double d1, double d2) {
		return Double.longBitsToDouble(Double.doubleToRawLongBits(d1) ^ Double.doubleToRawLongBits(d2));
	}
	
//	Adds the given double to the given bit report.
	public static double[] addToBitReport(double d, double[] arr) {
		String base = Long.toBinaryString(Double.doubleToRawLongBits(d));
		String zeroes = "";
		int numZeroes = 64 - base.length();
		for(int i = 0; i < numZeroes; i++) {
			zeroes += "0";
		}
		base = zeroes + base;
		for(int i = 0; i < 64; i++) {
			if(base.charAt(i) == '1') {
				arr[i] += 1;
			}
		}
		return arr;
	}
	
//	Normalizes the given bit report.
	public static double[] normalizedBitReport(int len, double[] arr) {
		for(int i = 0; i < 64; i++) {
			arr[i] /= len;
		}
		return arr;
	}
	
//	Prints the given bit report.
	public static void printBitReport(double[] arr) {
		String arrayString = Arrays.toString(arr);
		arrayString = arrayString.substring(1, arrayString.length()-1);
		System.out.println(arrayString);
	}
	
//	Returns the number of leading zero bits in the given byte[], 
//	ignoring the sign bit.
	public static int bitLeadingZeroesIgnoringSign (byte[] bs) {
		boolean first = true;
		int ret = 1;
		for(byte b : bs) {
			int n = 7;
			if(first == true) {
				n = 6;
				first = false;
			}
			for(int i = n; i >= 0; i--) {
				int bit = ((b & (1 << i)) >> i);
				if(bit == 0) {
					ret++;
				} else {
					return ret;
				}
			}
		}
		return ret;
	}

//	Returns the size in bytes of the file at the given path.
	static double fileSize (String path) {
		return new File(path).length() / (1024.0 * 1024.0);
	}

//	Returns the path of the compression version of the given file.
	static String compressedPathify (String f) {
		return System.getProperty("user.dir") + "/output/" + Util.getFolderName(f) + "/" + f + "_COMPRESSED";
	}
	
	static String rawPathify (String f) {
		return rawPathify(f, "UCR");
	}

//	Returns the path of the raw version of the given file.
	static String rawPathify (String f, String dataset) {
		if(dataset == "argonne") {
			return System.getProperty("user.dir") + "/data/argonne/argonne/" + f;			
		}
		return System.getProperty("user.dir") + "/data/" + dataset + "/" + Util.getFolderName(f) + "/" + f;
	}

//	Returns the name of the folder that the given raw or compressed file is in.
	static String getFolderName (String f) {
		return f.split("_")[0];
	}
	
	static String[] allFileNames() {
		return allFileNames("UCR");
	}

//	Returns a String[] of all of the raw file names.
	static String[] allFileNames (String dataset) {
		String[] folderNames = Util.allFolderNames(dataset);
		ArrayList<File> files = new ArrayList<File>();
		for(String name : folderNames) {
			files.addAll(Arrays.asList(new File(System.getProperty("user.dir") + "/data/" + dataset + "/" + name + "/").listFiles(Util.filter())));
		}
		int len = files.size();
		String[] ret = new String[len];
		for(int i = 0; i < len; i++) {
			ret[i] = files.get(i).getName();
		}
		return ret;
	}
	
	static String[] allFolderNames() {
		return allFolderNames("UCR");
	}

//	Returns a String[] of all of the names of the folders containing raw files.
	static String[] allFolderNames (String dataset) {
		File[] fileList = new File(System.getProperty("user.dir") + "/data/" + dataset + "/").listFiles(Util.filter());
		int len = fileList.length;
		String[] ret = new String[len];
		for(int i = 0; i < len; i++) {
			ret[i] = fileList[i].getName();
		}
		return ret;
		
	}

//	Returns a FilenameFilter that excludes all files starting with ".".
	static FilenameFilter filter () {
		return new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				if (name.startsWith(".")) {
					return false;
				} else {
					return true;
				}
			}
		};
	}

//	Returns a String representation of the given double[].
	static String seriesToString(double[] series) {
		int len = series.length;
		String ret = "";
		for(int i = 0; i < len; i++) {
			System.out.println(i + "/" + len + " datapoints copied");
			ret += String.valueOf(series[i]);
			if(i != len-1) {
				ret += ",";
			}
		}
		return ret;
	}

}
