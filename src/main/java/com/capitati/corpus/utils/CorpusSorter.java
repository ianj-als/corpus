package com.capitati.corpus.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.batik.ext.awt.image.codec.FileCacheSeekableStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import com.google.common.base.Function;

public class CorpusSorter implements ICorpusSorter {

  public final static int DEFAULTMAXTEMPFILES = 1024;

  // we divide the file into small blocks. If the blocks
  // are too small, we shall create too many temporary files.
  // If they are too big, we shall be using too much memory.
  private long estimateBestSizeOfBlocks() {
    final long filesSize =
        ((sourceFile.length() * 2) + (targetFile.length() * 2)) / 2;       

    /**
     * We multiply by two because later on someone insisted on counting the
     * memory usage as 2 bytes per character. By this model, loading a file with
     * 1 character will use 2 bytes.
     */
    // we don't want to open up much more than maxtmpfiles temporary files,
    // better run
    // out of memory first.
    final long blocksize = filesSize / maxNoTempFiles
        + (filesSize % maxNoTempFiles == 0 ? 0 : 1);

    // on the other hand, we don't want to create many temporary files
    // for naught. If blocksize is smaller than half the free memory, grow it.
    final long freemem = Runtime.getRuntime().freeMemory();
 
    return (blocksize < (freemem / 2)) ? (freemem / 2) : blocksize;
  }

  private List<File> sortInBatch(
      final Comparator<String> comparator,
      final Function<String, String> lineProcessor)
  throws IOException {
    final List<File> files = new ArrayList<File>();
    final long blockSize = estimateBestSizeOfBlocks();

    // Source file...
    final BufferedReader sourceReader =
        new BufferedReader(
            new InputStreamReader(
                new FileInputStream(sourceFile), inputCharSet));

    try {
      // Target file...
      final BufferedReader targetReader =
          new BufferedReader(
              new InputStreamReader(
                  new FileInputStream(targetFile), inputCharSet));

      try {
        // List of source file lines and target file positions
        final List<ImmutablePair<String, String>> lines =
            new ArrayList<ImmutablePair<String, String>>();

        String sourceLine = "";
        String targetLine = null;

        try {
          while(sourceLine != null) {
            long currentblocksize = 0;// in bytes

            while(currentblocksize < blockSize) {
              // Get next line from source 
              sourceLine = sourceReader.readLine();
              if(sourceLine == null) {
                break;
              }

              // Read the target line...
              targetLine = targetReader.readLine();
              if(targetLine == null) {
                break;
              }
              
              // Add current source line
              lines.add(
                  new ImmutablePair<String, String>(sourceLine, targetLine));

              // ram usage estimation, not very accurate, still more realistic
              // that the simple 2 * String.length
              currentblocksize +=
                  StringSizeEstimator.estimatedSizeOf(sourceLine + targetLine);
            }

            final File tempFile = sortAndSave(lines, comparator, lineProcessor);
            files.add(tempFile);
            lines.clear();
          }
        } catch(EOFException oef) {
          if(lines.size() > 0) {
            final File tempFile = sortAndSave(lines, comparator, lineProcessor);
            files.add(tempFile);
            lines.clear();
          }
        }
      } finally {
        targetReader.close();
      }
    } finally {
      sourceReader.close();
    }

    return files;
  }

  private File sortAndSave(
      final List<ImmutablePair<String, String>> linesAndPositions,
      final Comparator<String> stringComparator,
      final Function<String, String> lineProcessor) throws IOException {
    final Comparator<ImmutablePair<String, String>> comparator =
        new Comparator<ImmutablePair<String, String>>() {
          @Override
          public int compare(
              final ImmutablePair<String, String> p1,
              final ImmutablePair<String, String> p2) {
            final String str_one = p1.getLeft();
            final String str_two = p2.getLeft();
            final String norm_str_one = lineProcessor.apply(str_one);
            final String norm_str_two = lineProcessor.apply(str_two);
            final int normalised_cmp = norm_str_one.compareTo(norm_str_two);
            
            return (normalised_cmp != 0) ?
                normalised_cmp :
                stringComparator.compare(str_one, str_two);
          }
        };
    Collections.sort(linesAndPositions, comparator);
    
    final File newtmpfile =
        File.createTempFile("sort", "working", tempDirectory);
    newtmpfile.deleteOnExit();

    final OutputStream out = new FileOutputStream(newtmpfile);
    final BufferedWriter fbw =
        new BufferedWriter(
            new OutputStreamWriter(out, outputCharSet));

    try {
      for(ImmutablePair<String, String> pair : linesAndPositions) {
        fbw.write(pair.getLeft());
        fbw.newLine();
        fbw.write(pair.getRight());
        fbw.newLine();
      }
    } finally {
      fbw.close();
    }

    return newtmpfile;
  }

  private ImmutablePair<Long, Long> mergeSortedFiles(
      final File outputSourceFile,
      final File outputTargetFile,
      final List<File> temporaryFiles,
      final Comparator<String> cmp,
      final Function<String, String> lineProcessor) throws IOException {
    // Populate priority queue with temporary files
    final PriorityQueue<BinaryFileBuffer> pq =
        new PriorityQueue<BinaryFileBuffer>(
            11,
            new Comparator<BinaryFileBuffer>() {
              @Override
              public int compare(
                  final BinaryFileBuffer i,
                  final BinaryFileBuffer j) {
                return cmp.compare(i.peek(), j.peek());
              }
            });
    for(final File f : temporaryFiles) {
      BinaryFileBuffer bfb = new BinaryFileBuffer(f, outputCharSet);
      pq.add(bfb);
    }

    try {
      // Source file writer...
      final BufferedWriter sourceWriter =
          new BufferedWriter(
              new OutputStreamWriter(
                  new FileOutputStream(outputSourceFile), outputCharSet));

      try {
        // Target file writer...
        final BufferedWriter targetWriter =
            new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(outputTargetFile), outputCharSet));

        try {
          // Merge...
          long lineCounter = 0;
          long noDuplicates = 0;
          String sourceLine = null;
          String procSourceLine = null;
          String lastProcSourceLine = "";
          String targetLine = null;
          String procTargetLine = null;
          BinaryFileBuffer bfb = null;
          final Set<String> targetLines = new HashSet<String>();

          while(pq.size() > 0) {
            bfb = pq.poll();

            // Get source line
            sourceLine = bfb.pop();
            procSourceLine = lineProcessor.apply(sourceLine);

            // Target line
            targetLine = bfb.pop();
            // ...and process
            procTargetLine = lineProcessor.apply(targetLine);

            System.err.println("Source " + sourceLine);
            System.err.println("Last source " + ((lastProcSourceLine == null) ? "" : lastProcSourceLine));
            System.err.println("Target " + targetLine);
            
            if(procSourceLine.compareTo(lastProcSourceLine) == 0) {
              if(targetLines.contains(procTargetLine) == false) {
                targetLines.add(procTargetLine);

                // Write source and target lines
                lineCounter = writeSourceAndTargetLines(
                    sourceWriter,
                    sourceLine,
                    targetWriter,
                    targetLine,
                    lineCounter);
              } else {
                // Update the duplicates
                noDuplicates++;
              }
            } else {
              // Make a new set
              targetLines.clear();
              targetLines.add(procTargetLine);

              // Write source and target files
              lineCounter = writeSourceAndTargetLines(
                  sourceWriter,
                  sourceLine,
                  targetWriter,
                  targetLine,
                  lineCounter);
            }

            lastProcSourceLine = procSourceLine;

            if(bfb.empty() == true) {
              bfb.fbr.close();
              bfb.originalfile.delete();// we don't need you anymore
            } else {
              pq.add(bfb); // add it back
            }
          }

          return new ImmutablePair<Long, Long>(noDuplicates, lineCounter);
        } finally {
          targetWriter.close();
        }
      } finally {
        sourceWriter.close();
      }
    } finally {
      for(final BinaryFileBuffer fileBuffer : pq) {
        fileBuffer.close();
      }
    }
  }

  private static long writeSourceAndTargetLines(
      final BufferedWriter sourceWriter,
      final String sourceLine,
      final BufferedWriter targetWriter,
      final String targetLine,
      final long lineCounter) throws IOException {
    // Write the source line
    sourceWriter.write(sourceLine);
    sourceWriter.newLine();

    // Write the target line
    targetWriter.write(targetLine);
    targetWriter.newLine();
      
    return lineCounter + 1;
  }
  
  private final File sourceFile;
  private final File targetFile;
  private final File tempDirectory;
  private final Charset inputCharSet;
  private final Charset outputCharSet;
  private final String sortedExtension;
  private final int maxNoTempFiles;

  public CorpusSorter(
      final File theSourceFile,
      final File theTargetFile,
      final Charset theInputCharSet,
      final String theSortedExtension,
      final int theMaxNumOfTempFiles,
      final File theTempDirectory,
      final Charset theOutputCharSet) {
    sourceFile = theSourceFile;
    targetFile = theTargetFile;
    tempDirectory = theTempDirectory;
    inputCharSet = theInputCharSet;
    outputCharSet = theOutputCharSet;
    sortedExtension = theSortedExtension;
    maxNoTempFiles = theMaxNumOfTempFiles;
  }

  public ImmutablePair<Long, Long> sortWithUniquing() throws Exception {
    final List<File> missingFiles = new ArrayList<File>() {
      private static final long serialVersionUID = 2350695434693544950L;
      {
        if(sourceFile.exists() == false) add(sourceFile);
        if(targetFile.exists() == false) add(targetFile);
        if(tempDirectory.exists() == false) add(tempDirectory);
      }};
    if(missingFiles.size() > 0) {
      throw new FileNotFoundException(
          String.format(
              "Missing files: %s", StringUtils.join(missingFiles, ", ")));
    }

//    final FileLineCounter counter =
//        new FileLineCounter(inputCharSet, sourceFile, targetFile);
//    final Map<File, Long> lineNos = counter.countLines();
//    final long sourceLineNos = lineNos.get(sourceFile);
//    final long targetLineNos = lineNos.get(targetFile);
//    if(sourceLineNos != targetLineNos) {
//      throw new Exception("Source and target files have line count mismatch");
//    }

    final Function<String, String> lineProcessor = new Function<String, String>() {
      public String apply(final String line) {
        return line.toLowerCase().replaceAll("[ ]+", "_");
      }
    };
    
    final Comparator<String> comparator = new Comparator<String>() {
      @Override
      public int compare(String r1, String r2) {
        return r1.compareTo(r2);
      }
    };

    // Sort...
    final List<File> tempFiles = sortInBatch(comparator, lineProcessor);
    // ...and merge
    final File outputSourceFile = new File(
        sourceFile.getAbsolutePath() + "." + sortedExtension);
    final File outputTargetFile = new File(
        targetFile.getAbsolutePath() + "." + sortedExtension);
    final ImmutablePair<Long, Long> result =
        this.mergeSortedFiles(
            outputSourceFile,
            outputTargetFile,
            tempFiles,
            comparator,
            lineProcessor);
    
    return result;
  }
}

class BinaryFileBuffer {
  public BufferedReader fbr;
  public File originalfile;
  private String cache;
  private boolean empty;

  public BinaryFileBuffer(File f, Charset cs)
      throws IOException {
    originalfile = f;
    InputStream in = new FileInputStream(f);
    fbr = new BufferedReader(new InputStreamReader(in, cs));
    reload();
  }

  public boolean empty() {
    return this.empty;
  }

  private void reload() throws IOException {
    try {
      if((this.cache = this.fbr.readLine()) == null) {
        this.empty = true;
        this.cache = null;
      } else {
        this.empty = false;
      }
    } catch(EOFException oef) {
      this.empty = true;
      this.cache = null;
    }
  }

  public void close() throws IOException {
    this.fbr.close();
  }

  public String peek() {
    if(empty())
      return null;
    return this.cache.toString();
  }

  public String pop() throws IOException {
    String answer = peek();
    reload();
    return answer;
  }

}
