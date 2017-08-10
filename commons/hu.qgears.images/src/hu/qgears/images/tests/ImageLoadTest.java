package hu.qgears.images.tests;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.sql.Time;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;
import javax.rmi.CORBA.Util;

import hu.qgears.commons.UtilFile;
import hu.qgears.commons.UtilMd5;
import hu.qgears.commons.mem.DefaultJavaNativeMemoryAllocator;
import hu.qgears.commons.mem.INativeMemoryAllocator;
import hu.qgears.images.ENativeImageComponentOrder;
import hu.qgears.images.NativeImage;
import hu.qgears.images.SizeInt;
import hu.qgears.images.UtilNativeImageIo;
import hu.qgears.images.tiff.NativeTiffLoader;

public class ImageLoadTest {

	private ArrayList<NativeImage> imgList;
	private HashMap<URL, Long> urlMap;
	private INativeMemoryAllocator all;
	private File resultsCSV;
	private PrintWriter pw;
	private StringBuilder sb;
	public String TSM_TREAT_ROOT;
	public String TSM ;
	public String TREAT;
	public String TSM_RAM;
	public String TREAT_RAM;
	public String RAM;
	public static  DecimalFormat formatter;
	public static boolean cacheEmptyEnabled;

	// needs to create /mnt/tsm and /mnt/treat mounted subsystems
	public String SQUASHMOUNT = "/mnt";
	public String SQUASHRAM;

	/**
	 * 
	 * @param args
	 *            0: CF location of the gui.jar 1: tmpfs location of the gui.jar
	 *            2: RAM for test #2 and #4 3: RAM for test #7 (SQUASHRAM)
	 *            4: TSM_TREAT_ROOT location 5:SQUASHMOUNT ("/mnt")
	 *            6: cache empty enabling 1:enable; 0: disable
	 *            
	 * @throws IOException
	 * @throws InterruptedException 
	 */

	public static void main(String[] args) throws IOException, InterruptedException {
		// Initialization methods
		if (args.length == 0) {
			System.out.println("param0: give the location of gui.jar\n test app quits");
			return;
		}
		boolean debug = false;
		if (debug) {
			
			
			
			// Runtime.getRuntime().exec();
			return;
		}

		System.out.println("Initializing...");
		ImageLoadTest a = new ImageLoadTest(args);
		// not necessary, just for creating the setup for Test 6
		 //	a.initLZ4andQimg();

		System.out.println("Initialization done");

		// Run all the tests for every image file
		System.out.println("URL map reading");
		boolean first = true;
		boolean ramdisk = true;
		boolean squashFS = true;
		cacheEmptyEnabled= (args[6].equals("1"))? true : false;
		for (Map.Entry<URL, Long> img : a.urlMap.entrySet()) {
			String n=img.getKey().toString();
			n=n.substring(n.length()-10);
			System.out.println("Tests begins for file: "+n);
			// Test 1.
			a.cacheEmpty();
			a.copyFromLocbyURL(img.getKey(), img.getValue(), first,1);
			a.cacheEmpty();
			// Test 2.
			a.copyFromLocbyURL(URLEdit(img.getKey(), args[0], args[1]), img.getValue(), !first,2);
			a.cacheEmpty();
			// Test 3.
			a.copyFromFSbyName(img.getKey(), !ramdisk,3);
			a.cacheEmpty();
			// Test 4.
			a.copyFromFSbyName(img.getKey(), ramdisk,4);
			a.cacheEmpty();
			// Test 5.
			a.qimgLoad(img.getKey(), !squashFS, !ramdisk,5);
			a.cacheEmpty();
			// Test 6.
			a.qimgLoad(img.getKey(), squashFS, !ramdisk,6);
			a.cacheEmpty();
			// Test 7.
			a.qimgLoad(img.getKey(), squashFS, ramdisk,7);
			a.cacheEmpty();
			// Test TIFF
			a.tiffLoad(img.getKey(),8);
			a.cacheEmpty();
			// Test RLE
			a.testRLE(img.getKey(),9);
			a.cacheEmpty();
			System.out.println("Tests ends for file: "+n);

			// ending the line
			a.endLine();
		}
		System.out.println("URL MAP READING ENDED, TEST ENDED");

		// Ending method
		a.end();

	}

	private static URL URLEdit(URL param, String before, String after) {
		URL ret = null;
		try {
			ret = new URL(param.toString().replaceAll(before, after));
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ret;

	}

	public void end() {

		// Add here ending methods

		endCSV(); // closes CSV file

	}

	public ImageLoadTest(String[] args) {
		//init formatter
		formatter = (DecimalFormat) NumberFormat.getInstance(Locale.US);
		DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
		symbols.setGroupingSeparator(' ');
		formatter.setDecimalFormatSymbols(symbols);
		
		RAM = args[2];
		TSM_TREAT_ROOT=args[4];
		TSM = TSM_TREAT_ROOT+"/imgtest/com.bbraun.wings.omnibb.tsm.images/dark";
		TREAT = TSM_TREAT_ROOT+"/imgtest/com.bbraun.wings.omnibb.treat.images/dark";
		SQUASHMOUNT=args[5];

		SQUASHRAM = args[3];
		TREAT_RAM = RAM + "/imgtest/com.bbraun.wings.omnibb.treat.images/dark";
		TSM_RAM = RAM + "/imgtest/com.bbraun.wings.omnibb.tsm.images/dark";
		imgList = new ArrayList<NativeImage>();
		urlMap = new HashMap<URL, Long>();
		all = DefaultJavaNativeMemoryAllocator.getInstance();
		try {
			//needs to run in the script
		//	Runtime.getRuntime().exec("rm -rf /tmp/imgtest");
			initMap_AndFS_FromJAR(args[0]);
		//	initTiffConvert();
		} catch (IOException e) {
			System.err.println("IO Exception");
			e.printStackTrace();
		}
		initCSV(args[0]);
	}

	public void initCSV(String path) {
//		resultsCSV = new File(path + "results.csv");
		resultsCSV = new File("/tmp/results.csv");
		int noOfTests = 9;
		try {
			pw = new PrintWriter(resultsCSV);
			sb = new StringBuilder();
			sb.append("name");
			sb.append(",");
			sb.append("size");
			for (int i = 1; i <= noOfTests; i++) {
				sb.append(",");
				sb.append("time(t" + i + ")"+((i==8)?" TIFF": ""));
			}

			sb.append("\n");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void endCSV() {
		pw.write(sb.toString());
		pw.close();
		System.out.println("CSV generated as " + resultsCSV.getName());
	}

	public void initMap_AndFS_FromJAR(String loc) throws IOException {

		System.out.println("InputJAR reading");
		String path = "jar:file:" + loc + "gui.jar!/";
		URL url = new URL(path);
		JarURLConnection conn = (JarURLConnection) url.openConnection();
		JarFile jar = conn.getJarFile();
		Enumeration<JarEntry> entries = jar.entries();

		// boolean flag = true;

		// new File("/tmp/imgtest").delete();
		new File(TSM).mkdirs();
		new File(TREAT).mkdirs();

		new File(TSM_RAM).mkdirs();

		new File(TREAT_RAM).mkdirs();

		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			// png choose
			if (entry.getName().endsWith(".png") && (entry.getName().contains("com.bbraun.wings.omnibb.treat.images")
					|| entry.getName().contains("com.bbraun.wings.omnibb.tsm.images"))) {

				File f = new File("/tmp/tmp.png");
//				File f = new File(TSM_TREAT_ROOT+"/imgtest/" + entry.getName());
//				File f_ram = new File(RAM + "/imgtest/" + entry.getName());
				
				// temporary file create
				InputStream is;
				FileOutputStream fos;
				// copy to hdd
				is = jar.getInputStream(entry);
				// get the input stream
				fos = new FileOutputStream(f);
				while (is.available() > 0) { // write contents of 'is' to 'fos'
					fos.write(is.read());
				}
				fos.close();
				is.close();
				// copy to ram
				is = jar.getInputStream(entry);
				// get the input stream
//				fos = new FileOutputStream(f_ram);
//				while (is.available() > 0) { // write contents of 'is' to 'fos'
//					fos.write(is.read());
//				}
//				fos.close();
//				is.close();

				// putting into the HashMap
				URL newUrl = new URL(path + entry.getName());

				urlMap.put(newUrl, f.length());
				f.delete();

			}

		}

	}

	/**
	 * Simply copies the an image to the memory from the location
	 * 
	 * @param url
	 *            Source URL of the png file
	 * @param value
	 *            Size of the png file
	 * @param first
	 *            If it is the first test, than it needs to create the metadata
	 *            for the .csv
	 * 
	 */

	public void copyFromLocbyURL(URL url, Long value, boolean first,int testnumber) {

		long start;
		long end;

		try {
			start = System.nanoTime();
			NativeImage ni = UtilNativeImageIo.loadPngAndConvert(url, all, ENativeImageComponentOrder.BGRA);
			end = System.nanoTime();

			// checksum
			byte[] bytearray = new byte[ni.getBuffer().getJavaAccessor().remaining()];
			UtilMd5.getMd5(bytearray);

			if (first) {
				String n=url.toString();
				n=n.substring(n.length()-10);
				sb.append(n);
				sb.append(",");
				sb.append(value);
				sb.append(",");
				sb.append(format(end - start));
				sb.append(",");
				log(testnumber,url,end-start);
				
			} else {
				sb.append(format(end - start));
				sb.append(";");
				log(testnumber,url,end-start);
			}
			
			
			// sb.append("\n");
		} catch (IOException e) {
			System.err.println("problem at loadPngAndConvert");
			e.printStackTrace();

		}

	}

	public void copyFromFSbyName(URL url, boolean RAMDISK,int testnumber) {
		String urlString = url.toString();
		String filename = urlString.substring(urlString.length() - 10);

		if (urlString.contains("com.bbraun.wings.omnibb.treat.images")) {
			if (RAMDISK)
				filename = TREAT_RAM + "/" + filename;
			else
				filename = TREAT + "/" + filename;

		} else if (urlString.contains("com.bbraun.wings.omnibb.tsm.images")) {
			if (RAMDISK)
				filename = TSM_RAM + "/" + filename;
			else
				filename = TSM + "/" + filename;
		}

		File f = new File(filename);
		if (f.exists()) {

			try {
				long start = System.nanoTime();

				NativeImage ni = UtilNativeImageIo.loadPngAndConvert(new URL("file:" + filename), all,
						ENativeImageComponentOrder.BGRA);
				long end = System.nanoTime();
				sb.append(format(end - start));
				log(testnumber, url, end - start);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
				
			
		}
		sb.append(",");

	}

	public void endLine() {
		sb.append("\n");
	}

	/**
	 * Png to Qimg converter, it saves the files into the filesystem.
	 * 
	 * @param ni
	 *            Native image parameter for conversion
	 * @param url
	 *            Pathname, as a conversion parameter
	 * @throws IOException
	 */
	public void png2Qimg(NativeImage ni, URL url) throws IOException {

		String filename = url.toString().replaceAll(".png", "");
		filename = filename.substring(filename.length() - 6) + ".qimg";

		byte[] bytes = UtilNativeImageIo.imageToBytes(ni);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(((url.toString().contains("tsm")) ? TSM : TREAT) + "/" + filename);
		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}
		fos.write(bytes);
		fos.close();

	}

	public void qimgLoad(URL url, boolean squash, boolean ramdisk,int testnumber) {
		String pathname = url.toString();
		pathname = pathname.replaceAll(".png", "");

		if (squash) {
			String squashFolder = pathname.contains("tsm") ? "/tsm" : "/treat";
			String squashPathname = (ramdisk ? SQUASHRAM : SQUASHMOUNT) + squashFolder + "/"
					+ pathname.substring(pathname.length() - 6) + ".qimg";

			long end;
			try {
				long start = System.nanoTime();
				NativeImage ni = UtilNativeImageIo.loadImageFromFile(new File(squashPathname));
				end = System.nanoTime();
				sb.append(format(end - start));
				sb.append(",");
				log(testnumber, url, end - start);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (!squash) {
			String folder = pathname.contains("tsm") ? TSM : TREAT;
			pathname = folder + "/" + pathname.substring(pathname.length() - 6) + ".qimg";
			try {
				long end;
				long start = System.nanoTime();
				NativeImage ni = UtilNativeImageIo.loadImageFromFile(new File(pathname));

				// UtilNativeImageIo.wrapImageFromMemory(UtilFile.loadAsByteBuffer(new
				// File(pathname), all));
				end = System.nanoTime();
				sb.append(format(end - start));
				sb.append(",");
				log(testnumber, url, end - start);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
/*
	public void initLZ4andQimg() {
		// make directories
		new File(TSM + "/lz4squash").mkdirs();
		new File(TREAT + "/lz4squash").mkdirs();

		for (Map.Entry<URL, Long> img : urlMap.entrySet()) {
			NativeImage ni;
			try {
				ni = UtilNativeImageIo.loadPngAndConvert(img.getKey(), all, ENativeImageComponentOrder.BGRA);

				// Png2Qimg convert and save, init later tests
				png2Qimg(ni, img.getKey());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		File dir;
		File[] list;
		dir = new File(TSM);
		list = dir.listFiles();
		for (File file : list) {
			if (file.getName().contains("qimg")) {
				try {
					Runtime.getRuntime().exec("cp " + file.getAbsolutePath() + " " + TSM + "/lz4squash");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

		dir = new File(TREAT);
		list = dir.listFiles();
		for (File file : list) {
			if (file.getName().contains("qimg")) {
				try {
					Runtime.getRuntime().exec("cp " + file.getAbsolutePath() + " " + TREAT + "/lz4squash");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

	}

	*/
	/*
	public void initTiffConvert() {
		File dir;
		File[] list;
		Runtime console = Runtime.getRuntime();

		try {
			// Generate TSM and TREAT files
			for (int i = 0; i < 2; i++) {

				console.exec("mkdir " + (i == 0 ? TSM : TREAT) + "/tiffdir");
				dir = new File((i == 0 ? TSM : TREAT));
				list = dir.listFiles();
				for (File file : list) {
					if (file.getName().endsWith(".png")) {
						String newPath = file.getAbsolutePath().substring(file.getAbsolutePath().length() - 10);
						newPath = newPath.replaceAll(".png", ".tiff");
						String cmd="ffmpeg -i " + file.getAbsolutePath() + " -compression_algo deflate -pix_fmt rgba " + (i == 0 ? TSM : TREAT) + "/tiffdir/"
								+ newPath;
						console.exec(cmd);
					}

				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
*/
	public void tiffLoad(URL url,int testnumber) {
		File tiffroot;
		String path = url.toString();
		boolean isTsm;
		if (path.contains("tsm")) {
			isTsm = true;
		} else {
			isTsm = false;
		}
		path = path.substring(path.length() - 10);
		path=(isTsm? TSM : TREAT )+"/tiffdir/"+path;
		path=path.replaceAll("png", "tiff");

		 long end=0;
		 long start=System.nanoTime();
		 try {
			BufferedImage bi =ImageIO.read(new File(path));
			//NativeTiffLoader.getInstance().loadImageFromTiff(new File(path));
			
			end=System.nanoTime();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		 sb.append(format(end - start));
		 sb.append(",");
		 log(testnumber, url, end - start);

	}

	public String format(long val){
		Long param=new Long(val);
		return formatter.format(param.longValue());
	}
	public void cacheEmpty() throws IOException, InterruptedException{
		if (!cacheEmptyEnabled)
			return;
		
		FileWriter fw= new FileWriter(new File("/proc/sys/vm/drop_caches"));
		fw.write("3");
		fw.close();
		
	}
	public void log(int testnumber,URL url,long elapsed){
		String n=url.toString();
		n.substring(n.length()-10);
		System.out.println("Test:"+ testnumber+ "File: "+n+ "Time (ns):"+format(elapsed));
	}
	
	public void testRLE(URL url, int testnumber){
		String pathname = url.toString();
		pathname = pathname.replaceAll(".png", "");

//		if (squash) {
//			String squashFolder = pathname.contains("tsm") ? "/tsm" : "/treat";
//			String squashPathname = (ramdisk ? SQUASHRAM : SQUASHMOUNT) + squashFolder + "/"
//					+ pathname.substring(pathname.length() - 6) + ".qimg";
//
//			long end;
//			try {
//				long start = System.nanoTime();
//				NativeImage ni = UtilNativeImageIo.loadImageFromFile(new File(squashPathname));
//				end = System.nanoTime();
//				sb.append(format(end - start));
//				sb.append(",");
//				log(testnumber, url, end - start);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		if (!squash) {
			String folder = pathname.contains("tsm") ? TSM : TREAT;
			pathname = folder + "/" + pathname.substring(pathname.length() - 6) + ".qimg";
			try {
				long end;
				NativeImage ni = UtilNativeImageIo.loadImageFromFile(new File(pathname));
				File temporary= new File("/tmp/temp.rle");
				//encode the file
				UtilNativeImageIo.RLEencoderNativeIMage2File(ni, temporary);
				//decode the file
				long start = System.nanoTime();
				NativeImage nidecoded=UtilNativeImageIo.RLEdecoderFile2NativeImage(temporary);
				// UtilNativeImageIo.wrapImageFromMemory(UtilFile.loadAsByteBuffer(new
				// File(pathname), all));
				end = System.nanoTime();
				sb.append(format(end - start));
				sb.append(",");
				log(testnumber, url, end - start);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//		}
		
	}
}

