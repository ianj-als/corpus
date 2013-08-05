package com.capitati.corpus.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.Logger;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class CorpusUniquerTest {
	{
	  BasicConfigurator.configure(
	      new ConsoleAppender(
	          new EnhancedPatternLayout(
	              "%d{dd MMM yyyy HH:mm:ss,SSS}: %p: %m%n"),
	              ConsoleAppender.SYSTEM_ERR));
	}
	
	private static void copyFile(File sourceFile, File destFile) throws IOException {
	    if(!destFile.exists()) {
	        destFile.createNewFile();
	    }

	    FileChannel source = null;
	    FileChannel destination = null;

	    try {
	        source = new FileInputStream(sourceFile).getChannel();
	        destination = new FileOutputStream(destFile).getChannel();
	        destination.transferFrom(source, 0, source.size());
	    }
	    finally {
	        if(source != null) {
	            source.close();
	        }
	        if(destination != null) {
	            destination.close();
	        }
	    }
	}

	@Test
	public void testCorpusSortWithDuplicates() throws Exception {
    final File sourceFile =
        new File(this.getClass().getClassLoader().
            getResource("test.en").toURI());
    final File targetFile =
        new File(this.getClass().getClassLoader().
            getResource("test.non").toURI());
    final Logger logger = Logger.getRootLogger();

    final CorpusUniquer sorter = new CorpusUniquer(
        sourceFile,
        targetFile,
        Charset.forName("UTF-8"),
        10,
        new File("/tmp"),
        Charset.forName("UTF-8"),
        logger);
    final ImmutablePair<Long, Long> result =
        sorter.unique("uniq", ICorpusUniquer.UNLIMITED_TOKENS);

    System.out.println(String.format("Dropped %d dups", result.getLeft()));
//    for(final String source : dups.keySet()) {
//      System.out.println(source);
//      System.out.println(StringUtils.join(dups.get(source), "\t\n"));
//    }
  }
	
	@Test
	public void testCorpusSort() throws Exception {
    final File sourceFile =
        new File(this.getClass().getClassLoader().
            getResource("clean-train.en").toURI());
    final File targetFile =
        new File(this.getClass().getClassLoader().
            getResource("clean-train.lt").toURI());
    final Logger logger = Logger.getRootLogger();

    final CorpusUniquer sorter = new CorpusUniquer(
        sourceFile,
        targetFile,
        Charset.forName("UTF-8"),
        10,
        new File("/tmp"),
        Charset.forName("UTF-8"),
        logger);
    final ImmutablePair<Long, Long> result =
        sorter.unique("uniq", ICorpusUniquer.UNLIMITED_TOKENS);

    System.out.println(String.format("Dropped %d dups", result.getLeft()));
//    for(final String source : dups.keySet()) {
//      System.out.println(source);
//      System.out.println(StringUtils.join(dups.get(source), "\t\n"));
//    }
	}
//	
//	@Test
//	public void testMergeSortedFiles() throws Exception {
//		String line;
//		List<String> result;
//		BufferedReader bf;
//		Comparator<String> cmp = new Comparator<String>() {
//			@Override
//			public int compare(String o1, String o2) {
//				return o1.compareTo(o2);
//			}
//		};
//		File out = File.createTempFile("test_results", ".tmp", null);
//		ExternalSort.mergeSortedFiles(this.fileList, out, cmp,
//				Charset.defaultCharset(), false);
//		
//		bf = new BufferedReader(new FileReader(out));
//
//		result = new ArrayList<String>();
//		while ((line = bf.readLine()) != null) {
//			result.add(line);
//		}
//		bf.close();
//		assertArrayEquals(Arrays.toString(result.toArray()), EXPECTED_MERGE_RESULTS,
//				result.toArray());
//	}
//	
//    @Test
//    public void testMergeSortedFiles_Distinct() throws Exception {
//        String line;
//        List<String> result;
//        BufferedReader bf;
//        Comparator<String> cmp = new Comparator<String>() {
//            @Override
//            public int compare(String o1, String o2) {
//                return o1.compareTo(o2);
//            }
//        };
//        File out = File.createTempFile("test_results", ".tmp", null);
//        ExternalSort.mergeSortedFiles(this.fileList, out, cmp,
//                Charset.defaultCharset(), true);
//        
//        bf = new BufferedReader(new FileReader(out));
//
//        result = new ArrayList<String>();
//        while ((line = bf.readLine()) != null) {
//            result.add(line);
//        }
//        bf.close();
//        assertArrayEquals(Arrays.toString(result.toArray()), EXPECTED_MERGE_DISTINCT_RESULTS,
//                result.toArray());
//    }
//
//    @Test
//    public void testMergeSortedFiles_Append() throws Exception {
//        String line;
//        List<String> result;
//        BufferedReader bf;
//        Comparator<String> cmp = new Comparator<String>()
//        {
//            @Override
//            public int compare(String o1, String o2)
//            {
//                return o1.compareTo(o2);
//            }
//        };
//        
//        File out = File.createTempFile("test_results", ".tmp", null);
//        writeStringToFile(out, "HEADER, HEADER\n");
//
//        ExternalSort.mergeSortedFiles(this.fileList, out, cmp, Charset.defaultCharset(), true, true);
//
//        bf = new BufferedReader(new FileReader(out));
//
//        result = new ArrayList<String>();
//        while ((line = bf.readLine()) != null)
//        {
//            result.add(line);
//        }
//        bf.close();
//        assertArrayEquals(Arrays.toString(result.toArray()), EXPECTED_HEADER_RESULTS, result.toArray());
//    }
//    
//	@SuppressWarnings("static-method")
//	@Test
//	public void testSortAndSave() throws Exception {
//		File f;
//		String line;
//		List<String> result;
//		BufferedReader bf;
//
//		List<String> sample = Arrays.asList(SAMPLE);
//		Comparator<String> cmp = new Comparator<String>() {
//			@Override
//			public int compare(String o1, String o2) {
//				return o1.compareTo(o2);
//			}
//		};
//		f = ExternalSort.sortAndSave(sample, cmp, Charset.defaultCharset(),
//				null, false);
//		assertNotNull(f);
//		assertTrue(f.exists());
//		assertTrue(f.length() > 0);
//		bf = new BufferedReader(new FileReader(f));
//
//		result = new ArrayList<String>();
//		while ((line = bf.readLine()) != null) {
//			result.add(line);
//		}
//		bf.close();
//		assertArrayEquals(Arrays.toString(result.toArray()), EXPECTED_SORT_RESULTS,
//				result.toArray());
//	}
//
//	@SuppressWarnings("static-method")
//	@Test
//	public void testSortAndSave_Distinct() throws Exception {
//		File f;
//		String line;
//		List<String> result;
//		BufferedReader bf;
//		List<String> sample = Arrays.asList(SAMPLE);
//		Comparator<String> cmp = new Comparator<String>() {
//			@Override
//			public int compare(String o1, String o2) {
//				return o1.compareTo(o2);
//			}
//		};
//
//		f = ExternalSort.sortAndSave(sample, cmp, Charset.defaultCharset(),
//				null, true, false);
//		assertNotNull(f);
//		assertTrue(f.exists());
//		assertTrue(f.length() > 0);
//		bf = new BufferedReader(new FileReader(f));
//
//		result = new ArrayList<String>();
//		while ((line = bf.readLine()) != null) {
//			result.add(line);
//		}
//		bf.close();
//		assertArrayEquals(Arrays.toString(result.toArray()),
//				EXPECTED_DISTINCT_RESULTS, result.toArray());
//	}
//
//    @Test 
//	public void testSortInBatch() throws Exception {
//        Comparator<String> cmp = new Comparator<String>() {
//            @Override
//            public int compare(String o1, String o2)
//            {
//                return o1.compareTo(o2);
//            }
//        };
//
//	    List<File> listOfFiles = ExternalSort.sortInBatch(this.csvFile, cmp, ExternalSort.DEFAULTMAXTEMPFILES, Charset.defaultCharset(), null, false, 1, false);
//	    assertEquals(1, listOfFiles.size());
//	    
//	    ArrayList<String> result = readLines(listOfFiles.get(0));
//        assertArrayEquals(Arrays.toString(result.toArray()),EXPECTED_MERGE_DISTINCT_RESULTS, result.toArray());
//	}
//	
//    /**
//     * Sample case to sort csv file.
//     * @throws Exception
//     * 
//     */
//    @Test
//    public void testCSVSorting() throws Exception {
//    	testCSVSortingWithParams(false);
//    	testCSVSortingWithParams(true);
//    }
//	
//    /**
//     * Sample case to sort csv file.
//     * @param usegzip use compression for temporary files
//     * @throws Exception
//     * 
//     */
//    public void testCSVSortingWithParams(boolean usegzip) throws Exception {
//
//        File out = File.createTempFile("test_results", ".tmp", null);
//
//        Comparator<String> cmp = new Comparator<String>() {
//            @Override
//            public int compare(String o1, String o2)
//            {
//                return o1.compareTo(o2);
//            }
//        };
//
//        // read header
//        FileReader fr = new FileReader(this.csvFile);
//        Scanner scan = new Scanner(fr);
//        String head = scan.nextLine();
//        
//        // write to the file
//        writeStringToFile(out, head+"\n");
//        
//        // omit the first line, which is the header..
//        List<File> listOfFiles = ExternalSort.sortInBatch(this.csvFile, cmp, ExternalSort.DEFAULTMAXTEMPFILES, Charset.defaultCharset(), null, false, 1, usegzip);
//
//        // now merge with append 
//        ExternalSort.mergeSortedFiles(listOfFiles, out, cmp, Charset.defaultCharset(), false, true, usegzip);
//        
//        ArrayList<String> result = readLines(out);
//        
//        assertEquals(12, result.size());
//        assertArrayEquals(Arrays.toString(result.toArray()),EXPECTED_HEADER_RESULTS, result.toArray());
//
//    }
//
//	public static ArrayList<String> readLines(File f) throws IOException {
//		BufferedReader r = new BufferedReader(new FileReader(f));
//		ArrayList<String> answer = new ArrayList<String>();
//		String line;
//		while ((line = r.readLine()) != null) {
//			answer.add(line);
//		}
//		return answer;
//	}
//
//	public static void writeStringToFile(File f, String s) throws IOException {
//		FileOutputStream out = new FileOutputStream(f);
//		try {
//			out.write(s.getBytes());
//		} finally {
//			out.close();
//		}
//	}
//	
}
