package com.alibaba.lindorm.contest.compress.doublecompress1;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Writer {

//	Writes the given compressed ByteBuffer to the file located at output.
	static void writeCompressedToFile(ByteBuffer toWrite, String output) 
			throws IOException {
		
		output = Util.compressedPathify(output);
	    OutputStream outstream = Files.newOutputStream(Paths.get(output));
	    toWrite.flip();
		int len = toWrite.limit();
		byte[] outputArray = new byte[len];
		toWrite.get(outputArray);
		
		outstream.write(outputArray);
		outstream.flush();
		outstream.close();
		
		
	}

//	Writes the given uncompressed double[] to the file located at output.
	static void writeUncompressedToFile(double[] toWrite, String output) throws FileNotFoundException {
	
		PrintWriter out = new PrintWriter(output);
		out.println(Util.seriesToString(toWrite));
		out.close();
		
	}

}
