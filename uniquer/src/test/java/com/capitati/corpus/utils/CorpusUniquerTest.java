package com.capitati.corpus.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import junit.framework.Assert;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
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

	private static final String SUFFIX = "uniq";
	private static final Charset INPUT_CHAR_SET = Charset.forName("UTF-8");
	private static final Charset OUTPUT_CHAR_SET = Charset.forName("UTF-8");
	private File sourceFile;
	private File targetFile;
	private File uniqSourceFile;
	private File uniqTargetFile;
	private File tempDir;
	private Logger logger;
	
	@Before
	public void setUp() throws URISyntaxException {
	  final URI baseURI = getClass().getClassLoader().getResource(".").toURI();
	  sourceFile = new File(baseURI.getRawPath(), "source.test");
	  targetFile = new File(baseURI.getRawPath(), "target.test");
	  uniqSourceFile = new File(baseURI.getRawPath(), "source.test." + SUFFIX);
	  uniqTargetFile = new File(baseURI.getRawPath(), "target.test." + SUFFIX);
	  tempDir = new File(baseURI.getRawPath(), "temp");
	  if(tempDir.exists() == false) {
	    tempDir.mkdirs();
	  }
	  
	  logger = Logger.getLogger(CorpusUniquerTest.class);
	}

	@After
	public void tearDown() {
	  sourceFile.delete();
	  targetFile.delete();
	  uniqSourceFile.delete();
	  uniqTargetFile.delete();
	  logger = null;
	}

	private void createTestFiles(
	    final String[] source, final String[] target) throws Exception {
	  Assert.assertEquals(source.length, target.length);

	  final BufferedWriter sourceWriter =
	      new BufferedWriter(
	          new OutputStreamWriter(
	              new FileOutputStream(sourceFile), INPUT_CHAR_SET));
	  try {
	    final BufferedWriter targetWriter =
	        new BufferedWriter(
	            new OutputStreamWriter(
	                new FileOutputStream(targetFile), INPUT_CHAR_SET));

	    try {
	      for(final String line : source) {
	        sourceWriter.write(line);
	        sourceWriter.newLine();
	      }

	      for(final String line : target) {
	        targetWriter.write(line);
	        targetWriter.newLine();
	      }
	    } finally {
	      targetWriter.close();
	    }
	  } finally {
	    sourceWriter.close();
	  }
	}

	private void verifyFile(final String[] targets, final File file)
	throws Exception {
	  final BufferedReader reader =
	      new BufferedReader(
	          new InputStreamReader(
	              new FileInputStream(file), OUTPUT_CHAR_SET));
	  
	  try {
	    String line = "";
	    int noLines = 0;
	    int idx = 0;
	    
	    while(true) {
	      line = reader.readLine();
	      if(line == null) {
	        break;
	      }
	      Assert.assertEquals(targets[idx], line);
	      noLines++;
	      idx++;
	    }
	    
	    Assert.assertEquals(targets.length, noLines);
	  } finally {
	    reader.close();
	  }
	}
	
	private void verifyFiles(final String[] source, final String[] target)
	throws Exception {
	  verifyFile(source, uniqSourceFile);
	  verifyFile(target, uniqTargetFile);
	}
	
	private void createTestAndVerify(
	    final String[] source,
	    final String[] target,
	    final String[] targetUniqSource,
	    final String[] targetUniqTarget,
	    final long noSentencePairs,
	    final long noDuplicates,
	    final int maxNoTokens) throws Exception {
	  createTestFiles(source, target);

    final CorpusUniquer sorter = new CorpusUniquer(
        sourceFile,
        targetFile,
        INPUT_CHAR_SET,
        10,
        tempDir,
        OUTPUT_CHAR_SET,
        logger);
    final ImmutablePair<Long, Long> result = sorter.unique(SUFFIX, maxNoTokens);
    
    Assert.assertEquals(noSentencePairs, (long )result.getRight());
    Assert.assertEquals(noDuplicates, (long )result.getLeft());
    
    verifyFiles(targetUniqSource, targetUniqTarget);
	}

  @Test
  public void testCorpusSort() throws Exception {
    final String[] source = {"z", "x", "s", "p", "e", "c", "t", "r", "u", "m"};
    final String[] target = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
    final String[] targetSource = {"c", "e", "m", "p", "r", "s", "t", "u", "x", "z"};
    final String[] targetTarget = {"6", "5", "10", "4", "8", "3", "7", "9", "2", "1"};
    
    createTestAndVerify(
        source,
        target,
        targetSource,
        targetTarget,
        10,
        0,
        ICorpusUniquer.UNLIMITED_TOKENS);
  }
	
	@Test
	public void testWithDuplicates() throws Exception {
    final String[] source = {
        "The man in the hat",
        "THE  MAN IN THE HAT",
        "The  man in the hat",
        "The man  in the hat",
        "THE MAN IN THE HAT",
        "THE  MAN IN THE HAT"};
    final String[] target = {"5", "5", "2", "3", "4", "5"};
    final String[] targetSource = {
        "THE  MAN IN THE HAT",
        "The  man in the hat",
        "The man  in the hat",
        "THE MAN IN THE HAT"
    };
    final String[] targetTarget = {"5", "2", "3", "4"};

    createTestAndVerify(
        source,
        target,
        targetSource,
        targetTarget,
        4,
        2,
        ICorpusUniquer.UNLIMITED_TOKENS);
  }
	
	@Test
	public void testFilterLongSentences() throws Exception {
	  final String[] source = {
	      "A",
	      "A B",
	      "A B C",
	      "A B C D",
	      "A B C D E",
	      "A B C D E F"
	  };
	  final String[] target = {"1", "2", "3", "4", "5", "6"};
    final String[] targetSource = {
        "A",
        "A B",
        "A B C",
    };
    final String[] targetTarget = {"1", "2", "3"};

	  createTestAndVerify(
	      source,
	      target,
	      targetSource,
	      targetTarget,
	      3,
	      0,
	      3);
	  }

	@Test
	public void testEmptyLines() throws Exception {
	  final String[] source = {
        "",
        " ",
        "   ",
        "A B C D",
        "A B C D E",
        "A B C D E F"
    };
    final String[] target = {
        "A",
        "A B",
        "A B C",
        "",
        " ",
        "  "
    };
    final String[] targetSource = new String[0];
    final String[] targetTarget = new String[0];

    createTestAndVerify(
        source,
        target,
        targetSource,
        targetTarget,
        0,
        0,
        ICorpusUniquer.UNLIMITED_TOKENS);
	}
}
