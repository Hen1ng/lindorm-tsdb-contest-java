package com.alibaba.lindorm.contest.compress;

import java.util.*;
import java.io.*;

class HuffmanNode implements Comparable<HuffmanNode> {
    byte data;
    int frequency;
    HuffmanNode left, right;

    public HuffmanNode(byte data, int frequency) {
        this.data = data;
        this.frequency = frequency;
    }

    @Override
    public int compareTo(HuffmanNode other) {
        return this.frequency - other.frequency;
    }
}

public class HuffmanCoding {

    // Build the Huffman tree and return the root node
    private static HuffmanNode buildHuffmanTree(Map<Byte, Integer> byteFrequency) {
        PriorityQueue<HuffmanNode> minHeap = new PriorityQueue<>();

        for (Map.Entry<Byte, Integer> entry : byteFrequency.entrySet()) {
            minHeap.add(new HuffmanNode(entry.getKey(), entry.getValue()));
        }

        while (minHeap.size() > 1) {
            HuffmanNode left = minHeap.poll();
            HuffmanNode right = minHeap.poll();

            HuffmanNode parent = new HuffmanNode((byte) 0, left.frequency + right.frequency);
            parent.left = left;
            parent.right = right;

            minHeap.add(parent);
        }

        return minHeap.poll();
    }

    // Build the Huffman codes for bytes
    private static void buildHuffmanCodes(HuffmanNode root, String code, Map<Byte, String> huffmanCodes) {
        if (root == null) {
            return;
        }

        if (root.data != 0) {
            huffmanCodes.put(root.data, code);
        }

        buildHuffmanCodes(root.left, code + "0", huffmanCodes);
        buildHuffmanCodes(root.right, code + "1", huffmanCodes);
    }

    // Encode a byte array using Huffman coding
    public static byte[] encoder(byte[] input) throws IOException {
        Map<Byte, Integer> byteFrequency = new HashMap<>();
        for (byte b : input) {
            byteFrequency.put(b, byteFrequency.getOrDefault(b, 0) + 1);
        }

        HuffmanNode root = buildHuffmanTree(byteFrequency);

        Map<Byte, String> huffmanCodes = new HashMap<>();
        buildHuffmanCodes(root, "", huffmanCodes);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StringBuilder encodedData = new StringBuilder();

        for (byte b : input) {
            encodedData.append(huffmanCodes.get(b));
            while (encodedData.length() >= 8) {
                int byteValue = Integer.parseInt(encodedData.substring(0, 8), 2);
                output.write((byte) byteValue);
                encodedData.delete(0, 8);
            }
        }

        if (encodedData.length() > 0) {
            int padding = 8 - encodedData.length();
            for (int i = 0; i < padding; i++) {
                encodedData.append('0');
            }
            int byteValue = Integer.parseInt(encodedData.toString(), 2);
            output.write((byte) byteValue);
        }

        return output.toByteArray();
    }

    // Decode a byte array using Huffman coding
    public static byte[] decoder(byte[] input, HuffmanNode root) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HuffmanNode currentNode = root;

        int bit;
        while ((bit = inputStream.read()) != -1) {
            for (int i = 7; i >= 0; i--) {
                int mask = 1 << i;
                int bitValue = (bit & mask) >> i;

                if (bitValue == 0) {
                    currentNode = currentNode.left;
                } else {
                    currentNode = currentNode.right;
                }

                if (currentNode.data != 0) {
                    outputStream.write(currentNode.data);
                    currentNode = root;
                }
            }
        }

        return outputStream.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        String data = "this is an example for huffman encoding";

        // Encode the data
        byte[] encodedData = encoder(data.getBytes());

        // Decode the data
        byte[] decodedData = decoder(encodedData, buildHuffmanTree(getByteFrequency(data.getBytes())));

        String decodedString = new String(decodedData);
        System.out.println("Original Data: " + data);
        System.out.println("Decoded Data: " + decodedString);
    }

    // Helper function to calculate byte frequencies
    private static Map<Byte, Integer> getByteFrequency(byte[] data) {
        Map<Byte, Integer> byteFrequency = new HashMap<>();
        for (byte b : data) {
            byteFrequency.put(b, byteFrequency.getOrDefault(b, 0) + 1);
        }
        return byteFrequency;
    }
}
