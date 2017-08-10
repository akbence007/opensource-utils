package hu.qgears.images.tests;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import hu.qgears.commons.mem.DefaultJavaNativeMemoryAllocator;
import hu.qgears.commons.mem.INativeMemory;
import hu.qgears.commons.mem.INativeMemoryAllocator;
import hu.qgears.images.ENativeImageComponentOrder;
import hu.qgears.images.NativeImage;
import hu.qgears.images.UtilNativeImageIo;

public class CustomRLE {

	// singleton pattern
	private static CustomRLE instance = null;
	private static int QIMGHEADER_SIZE = 24;

	protected CustomRLE() {
	}

	public static CustomRLE getInstance() {
		if (instance == null) {
			instance = new CustomRLE();
		}
		return instance;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println("testing RLE");
		String cmd = "ffmpeg -i /tmp/test.png -pix_fmt rgba /tmp/test.tiff";
		// Runtime.getRuntime().exec(cmd);
		Runtime.getRuntime().exec("rm /tmp/test.rle").waitFor();

		Runtime.getRuntime().exec("rm /tmp/fin.qimg").waitFor();
		DefaultJavaNativeMemoryAllocator all = DefaultJavaNativeMemoryAllocator.getInstance();

		int[] testArray = new int[20];
		testArray[0] = (int) 0xFE;

		Runtime.getRuntime().exec("rm /tmp/testvector.tst").waitFor();
		File img2 = new File("/tmp/testvector.tst");
		RandomAccessFile testvector = new RandomAccessFile(img2, "rw");
		for (int i : testArray) {
			testvector.writeInt(i);
		}
		testvector.close();

		// Tests!!
		File img = new File("/tmp/000195.qimg");
		File compressedImg = new File("/tmp/test.rle");

		NativeImage ni = UtilNativeImageIo.loadImageFromFile(new File("/tmp/000195.qimg"));
		// encoder(testArray, "/tmp/asd.rle");

		if (img.exists()) {
			System.out.println("test input exists!");
		}

		encoderNativeIMage2File(ni, new File("/tmp/compressed.rle"));
		NativeImage natim = decoderFile2NativeImage(new File("/tmp/compressed.rle"));

		byte[] decompressedBytes = UtilNativeImageIo.imageToBytes(natim);

		// Testout
		File decomp = new File("/tmp/decompressed.qimg");
		RandomAccessFile fin = new RandomAccessFile(decomp, "rw");
		fin.write(decompressedBytes);

	}

	public static void encoderNativeIMage2File(NativeImage ni, File file) throws IOException, InterruptedException {
		byte[] bytes = UtilNativeImageIo.imageToBytes(ni);
		ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

		RandomAccessFile raf = new RandomAccessFile(file, "rw");

		// the 24 is valid, as long as the QIMG header file 24 bytes long
		ByteBuffer encodedBB = encodeFromBB2BB(bb, QIMGHEADER_SIZE);
		encodedBB.position(0);
		while (encodedBB.hasRemaining()) {
			raf.write(encodedBB.get());
		}
		raf.close();
	}

	public static NativeImage decoderFile2NativeImage(File inputFile) throws IOException, InterruptedException {

		RandomAccessFile raf = null;
		raf = new RandomAccessFile(inputFile, "r");
		ByteBuffer inputFromFile = ByteBuffer.allocate((int) raf.length());
		try {
			while (true) {
				inputFromFile.put(raf.readByte());
			}
		} catch (EOFException e) {
			inputFromFile.position(0);

		}
		inputFromFile.order(ByteOrder.LITTLE_ENDIAN);
		int pic_width = inputFromFile.getInt(1 * 4);
		int pic_height = inputFromFile.getInt(2 * 4);
		raf.close();
		inputFromFile.position(0);
		NativeImage ni = null;
		INativeMemory inm = DefaultJavaNativeMemoryAllocator.getInstance()
				.allocateNativeMemory(pic_width * pic_height * 4 + QIMGHEADER_SIZE);
		decodeFromBB2BB(inputFromFile, inm.getJavaAccessor(), QIMGHEADER_SIZE);
		inm.getJavaAccessor().position(0);
		inm.getJavaAccessor().order(ByteOrder.LITTLE_ENDIAN);
		ni = UtilNativeImageIo.wrapImageFromMemory(inm);
		return ni;
	}

	private static ByteBuffer encodeFromBB2BB(ByteBuffer i, int head) throws IOException, InterruptedException {

		ByteBuffer out;
		class Data {
			public int intData;
			public byte byteData;
			public boolean isByte;

			public Data(int i) {
				intData = i;
				isByte = false;
			}

			public Data(byte i, boolean bytetrue) {
				byteData = i;
				isByte = bytetrue;
			}
		}
		List<Data> o = new ArrayList<Data>();

		int totalbytes = 0;
		byte count = 1;

		int b, bprime;

		byte runcmd = 0;
		int rundata;
		int single = 0;
		byte[] headerbytes = new byte[24];
		// Skipping the header
		for (int j = 0; j < head; j++) {

			byte header = i.get();
			headerbytes[j] = header;
			// o.add(new Data(header));
			totalbytes += 1;
		}

		// System.out.println("data");

		try {
			b = i.getInt();
		} catch (Exception e) {
			out = null;
			out = ByteBuffer.allocate(totalbytes);
			for (int j = 0; j < headerbytes.length; j++) {
				out.put(headerbytes[j]);
			}
			// out.order(ByteOrder.BIG_ENDIAN);
			for (Data data : o) {
				if (data.isByte) {
					out.put(data.byteData);
				} else {
					out.putInt(data.intData);
				}
			}
			return out;
			// return total;
		}
		// totalbytes +=4;
		while (true) {
			try {

				bprime = i.getInt();
			} catch (Exception e) {
				if (count == -1) {

					single = b;
					o.add(new Data(single));
					totalbytes += 4;

				} else {

					runcmd = (byte) (-1 * count);

					rundata = b;
					o.add(new Data(runcmd, true));
					o.add(new Data(rundata));
					totalbytes += 5;

				}
				out = null;
				out = ByteBuffer.allocate(totalbytes);
				for (int j = 0; j < headerbytes.length; j++) {
					out.put(headerbytes[j]);
				}
				// out.order(ByteOrder.LITTLE_ENDIAN);
				for (Data data : o) {

					if (data.isByte) {
						out.put(data.byteData);
					} else {
						out.putInt(data.intData);
					}
				}
				return out;
				// return total;
			}
			// total++;

			if (bprime == b) {
				if (count < 127) {
					count++;

				} else {
					runcmd = -127;
					rundata = b;

					o.add(new Data(runcmd, true));
					o.add(new Data(rundata));
					totalbytes += 5;

					count = 1;

				}
			} else {
				if (count == -1) {

					single = b;
					o.add(new Data(single));
					totalbytes += 4;

				} else {

					runcmd = (byte) (-1 * count);
					rundata = b;
					o.add(new Data(runcmd, true));
					o.add(new Data(rundata));
					totalbytes += 5;

				}
				count = 1;
				b = bprime;
			}
		}
	}

	private static void decodeFromBB2BB(ByteBuffer i, ByteBuffer bb, int head) throws IOException {

		byte command = 0;
		int data = 0;

		// Getting the header
		// System.out.println("decode, filesize: " + i.length());
		for (int j = 0; j < head; j++) {
			byte header = i.get();
			// byte header = i.readByte();

			bb.put(header);
			// System.out.println(header);

		}

		// System.out.println("data");
		while (true) {
			try {

				command = i.get();

				// command = i.readByte();
				// System.out.println("input at: " + i.getFilePointer() + "
				// (command)");
			} catch (Exception e) {
				bb.position(0);
				return;
			}

			if (command < 0) {
				command = (byte) (-1 * command);

				// Don't catch errors here because
				// if EOF, encoding was incorrect

				data = i.getInt();
				// data = i.readInt();
				// System.out.println("input at: " + i.getFilePointer());

			} else {

				i.position(i.position() - 1);
				// i.seek(i.getFilePointer() - 1);
				data = i.getInt();
				// data = i.readInt();
				command = 1;
			}
			bb.order(ByteOrder.LITTLE_ENDIAN);
			for (; command > 0; command--) {

				bb.putInt(data);
				// System.out.println("WRITING");

				// o.writeInt(data);

			}

		}
	}

	/**
	 * Encoder for RandomAccesFile input, and RandomAccesFile Output. Replaced
	 * with the Bytebuffer versions
	 * 
	 * @param i
	 * @param o
	 * @param head
	 * @return
	 * @throws IOException
	 * @deprecated use {@link #encodeFromBB2BB()} instead.
	 */
	private static int encode(RandomAccessFile i, RandomAccessFile o, int head) throws IOException {
		int total = 0;
		byte count = 1;

		int b, bprime;

		byte runcmd = 0;
		int rundata;
		int single = 0;

		// Skipping the header
		for (int j = 0; j < head; j++) {
			byte header = i.readByte();
			o.writeByte(header);
			System.out.println(header + " fp: " + o.getFilePointer());
		}

		System.out.println("data");

		try {

			b = i.readInt();
			System.out.println(b + " " + i.getFilePointer());
		} catch (Exception e) {
			return total;
		}
		total = 1;
		while (true) {
			try {

				bprime = i.readInt();
				System.out.println(bprime + " " + i.getFilePointer());
			} catch (Exception e) {
				if (count == -1) {

					single = b;
					o.writeInt(single);

				} else {

					runcmd = (byte) (-1 * count);

					rundata = b;

					o.writeByte(runcmd);

					o.writeInt(rundata);

				}
				return total;
			}
			total++;

			if (bprime == b) {
				if (count < 127) {
					count++;
					System.out.println("count: " + count);
				} else {
					runcmd = -127;
					rundata = b;
					o.writeByte(runcmd);
					o.writeInt(rundata);

					count = 1;
					total++;

				}
			} else {
				if (count == -1) {

					single = b;
					o.writeInt(single);

				} else {

					runcmd = (byte) (-1 * count);

					rundata = b;
					o.writeByte(runcmd);

					o.writeInt(rundata);

				}
				count = 1;
				b = bprime;
			}
		}
	}

	/**
	 * Decoder for RandomAccesFile input, and RandomAccesFile Output. Replaced
	 * with the Bytebuffer versions
	 * 
	 * @param i
	 * @param o
	 * @param head
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @deprecated use {@link #decodeFromBB2BB()} instead.
	 */
	private static int decode(RandomAccessFile i, RandomAccessFile o, int head)
			throws IOException, InterruptedException {
		int total = 0;

		byte command;
		int data = 0;

		// Getting the header
		System.out.println("decode");
		for (int j = 0; j < head; j++) {
			byte header = i.readByte();
			o.writeByte(header);
			// System.out.println(header);

		}

		System.out.println("data");
		while (true) {
			try {

				command = i.readByte();

			} catch (EOFException e) {
				return total;
			}

			if (command < 0) {
				command = (byte) (-1 * command);

				// Don't catch errors here because
				// if EOF, encoding was incorrect

				data = i.readInt();

			} else {

				i.seek(i.getFilePointer() - 1);
				data = i.readInt();
				command = 1;
			}
			for (; command > 0; command--) {
				total += 4;
				o.writeInt(data);
				System.out.println(data + " " + o.getFilePointer());

			}

		}
	}

}
