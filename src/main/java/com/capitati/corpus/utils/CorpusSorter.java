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
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

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

  private List<File> sortInBatch(final Comparator<String> comparator)
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
      final FileInputStream targetInputStream = new FileInputStream(targetFile);
      final FileChannel targetChannel = targetInputStream.getChannel();
      final BufferedReader targetReader =
          new BufferedReader(
              new InputStreamReader(targetInputStream, inputCharSet), 1);

      try {
        // List of source file lines and target file positions
        final List<ImmutablePair<String, Long>> lines =
            new ArrayList<ImmutablePair<String, Long>>();

        String sourceLine = "";
        long currentTargetPos = -1;

        try {
          while(sourceLine != null) {
            long currentblocksize = 0;// in bytes
            
            while(currentblocksize < blockSize) {
              // Get next line from source 
              sourceLine = sourceReader.readLine();
              if(sourceLine == null) {
                break;
              }
                
              // Record the current offset in the target file...
              currentTargetPos = targetChannel.position();
              System.err.println(String.format("Curr target pos: %d", currentTargetPos));
              // ...and advance one line.
              System.err.println("Target: " + targetReader.readLine());
              System.err.println(String.format("Post read Curr target pos: %d", targetChannel.position()));
                
              // Add current source line
              lines.add(
                  new ImmutablePair<String, Long>(sourceLine, currentTargetPos));

              // ram usage estimation, not very accurate, still more realistic
              // that the simple 2 * String.length
              currentblocksize += StringSizeEstimator.estimatedSizeOf(sourceLine);
            }

              final File tempFile = sortAndSave(lines, comparator);
              files.add(tempFile);
              lines.clear();
            }
          } catch(EOFException oef) {
            if(lines.size() > 0) {
              final File tempFile = sortAndSave(lines, comparator);
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
      final List<ImmutablePair<String, Long>> linesAndPositions,
      final Comparator<String> stringComparator) throws IOException {
    final Comparator<ImmutablePair<String, Long>> comparator =
        new Comparator<ImmutablePair<String, Long>>() {
          @Override
          public int compare(
              final ImmutablePair<String, Long> p1,
              final ImmutablePair<String, Long> p2) {
            return stringComparator.compare(p1.getLeft(), p2.getLeft());
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

    String line = null;
    try {
      for(ImmutablePair<String, Long> pair : linesAndPositions) {
        line = pair.getLeft();
        // Skip duplicate lines
        fbw.write(String.format("%016x,", pair.getRight()));
        fbw.write(line);
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

    // Raw temporary file line parser
    final Function<String, ImmutablePair<String, String>> lineParser =
        new Function<String, ImmutablePair<String, String>>() {
          public ImmutablePair<String, String> apply(final String rawLine) {
            final int commaIdx = rawLine.indexOf(',');
            final String fileKey = rawLine.substring(0, commaIdx);
            final String line = rawLine.substring(commaIdx + 1);

            return new ImmutablePair<String, String>(fileKey, line);
          }
        };

    // Populate priority queue with temporary files
    final PriorityQueue<BinaryFileBuffer> pq =
        new PriorityQueue<BinaryFileBuffer>(
            11,
            new Comparator<BinaryFileBuffer>() {
              @Override
              public int compare(
                  final BinaryFileBuffer i,
                  final BinaryFileBuffer j) {
                final ImmutablePair<String, String> iLine =
                    lineParser.apply(i.peek());
                final ImmutablePair<String, String> jLine =
                    lineParser.apply(j.peek());

                return cmp.compare(iLine.getRight(), jLine.getRight());
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
        // Target file reader...
        final FileInputStream targetInputStream = new FileInputStream(targetFile);
        final FileChannel targetChannel = targetInputStream.getChannel();
        final BufferedReader targetReader =
            new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(targetFile), inputCharSet));
      
        try {
          // Target file writer...
          final BufferedWriter targetWriter =
              new BufferedWriter(
                  new OutputStreamWriter(
                      new FileOutputStream(outputTargetFile), outputCharSet));
          
          try {
            // Merge...
            long lineCounter = 0;
            long targetPos = 0;
            long noDuplicates = 0;
            String sourceLine = null;
            String lastSourceLine = null;
            String targetLine = null;
            //String procSourceLine = null;
            String procTargetLine = null;
            BinaryFileBuffer bfb = null;
            ImmutablePair<String, String> parsedLine = null;
            final Set<String> targetLines = new HashSet<String>();

            while(pq.size() > 0) {
              bfb = pq.poll();

              // Parse the raw line
              parsedLine = lineParser.apply(bfb.pop());
              sourceLine = parsedLine.getRight();
              // Process source line
              //procSourceLine = lineProcessor.apply(sourceLine);
              
              // Set the position in the target file
              targetPos = Long.parseLong(parsedLine.getLeft(), 16);
              System.err.println(String.format("%s:%d", parsedLine.getLeft(), targetPos));

              // Lookup target line
              targetChannel.position(targetPos);
              targetLine = targetReader.readLine();
              // ...and process
              procTargetLine = lineProcessor.apply(targetLine);

              if(sourceLine.equals(lastSourceLine) == true) {
                if(targetLines.contains(procTargetLine) == false) {
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
              
              lastSourceLine = sourceLine;

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
          targetReader.close();
          targetInputStream.close();
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
    final List<File> tempFiles = sortInBatch(comparator);
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
