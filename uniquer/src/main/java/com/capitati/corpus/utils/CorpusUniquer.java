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
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.log4j.Logger;

import com.google.common.base.Function;

public class CorpusUniquer implements ICorpusUniquer {
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

  private List<ImmutablePair<File, File>> sortInBatch(
      final Comparator<String> comparator,
      final Function<ImmutablePair<String, String>, Boolean> filter)
  throws IOException {
    final List<ImmutablePair<File, File>> files =
        new ArrayList<ImmutablePair<File, File>>();
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
        String targetLine = "";
        ImmutablePair<String, String> sourceTarget = null;

        try {
          while(sourceLine != null) {
            long currentblocksize = 0;// in bytes

            while(currentblocksize < blockSize) {
              // Get next line from source 
              sourceLine = StringUtils.strip(sourceReader.readLine());
              if(sourceLine == null) {
                break;
              }
              
              // Read the target line...
              targetLine = StringUtils.strip(targetReader.readLine());
              if(targetLine == null) {
                break;
              }

              // Filter
              sourceTarget = new ImmutablePair<String, String>(
                  sourceLine, targetLine);
              if(filter.apply(sourceTarget) == false) {
                logger.info(
                    "Dropping source sentence [" + sourceLine + "]" +
                    " with target sentence [" + targetLine + "]");
                continue;
              }

              // Add current source line
              lines.add(
                  new ImmutablePair<String, String>(sourceLine, targetLine));

              // ram usage estimation, not very accurate, still more realistic
              // that the simple 2 * String.length
              currentblocksize +=
                  StringSizeEstimator.estimatedSizeOf(sourceLine);
            }

            final ImmutablePair<File, File> tempFiles =
                sortAndSave(lines, comparator);
            try {
              if(tempFiles != null) {
                files.add(tempFiles);
              }
            } catch(final NullPointerException ex) {
              // Ignore...
            }
            lines.clear();
          }
        } catch(EOFException oef) {
          if(lines.size() > 0) {
            final ImmutablePair<File, File> tempFiles =
                sortAndSave(lines, comparator);
            try {
              files.add(tempFiles);
            } catch(final NullPointerException ex) {
              // Ignore...
            }
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

  private ImmutablePair<File, File> sortAndSave(
      final List<ImmutablePair<String, String>> lines,
      final Comparator<String> stringComparator) throws IOException {
    if(lines.size() < 1) {
      return null;
    }

    final Comparator<ImmutablePair<String, String>> comparator =
        new Comparator<ImmutablePair<String, String>>() {
          @Override
          public int compare(
              final ImmutablePair<String, String> p1,
              final ImmutablePair<String, String> p2) {
            return stringComparator.compare(p1.getLeft(), p2.getLeft());
          }
        };
    Collections.sort(lines, comparator);
    
    final File sourceTmpFile =
        File.createTempFile("sort", "src-working", tempDirectory);
    sourceTmpFile.deleteOnExit();
    
    final File targetTmpFile =
        File.createTempFile("sort", "trg-working", tempDirectory);
    targetTmpFile.deleteOnExit();

    final BufferedWriter sourceWriter =
        new BufferedWriter(
            new OutputStreamWriter(
                new FileOutputStream(sourceTmpFile), outputCharSet));
    try {
      final BufferedWriter targetWriter =
          new BufferedWriter(
              new OutputStreamWriter(
                  new FileOutputStream(targetTmpFile), outputCharSet));

      try {
        for(ImmutablePair<String, String> pair : lines) {
          sourceWriter.write(pair.getLeft());
          sourceWriter.newLine();
          targetWriter.write(pair.getRight());
          targetWriter.newLine();
        }
      } finally {
        targetWriter.close();
      }
    } finally {
      sourceWriter.close();
    }

    return new ImmutablePair<File, File>(sourceTmpFile, targetTmpFile);
  }

  private ImmutablePair<Long, Long> mergeSortedFiles(
      final File outputSourceFile,
      final File outputTargetFile,
      final List<ImmutablePair<File, File>> temporaryFiles,
      final Comparator<String> cmp,
      final Function<String, String> lineProcessor)
  throws IOException {
    // Populate priority queue with temporary files
    final PriorityQueue<ImmutablePair<BinaryFileBuffer, BinaryFileBuffer>> pq =
        new PriorityQueue<ImmutablePair<BinaryFileBuffer, BinaryFileBuffer>>(
            11,
            new Comparator<ImmutablePair<BinaryFileBuffer, BinaryFileBuffer>>() {
              @Override
              public int compare(
                  final ImmutablePair<BinaryFileBuffer, BinaryFileBuffer> i,
                  final ImmutablePair<BinaryFileBuffer, BinaryFileBuffer> j) {
                final String str_one = i.getLeft().peek();
                final String str_two = j.getLeft().peek();

                return cmp.compare(str_one, str_two);
              }
            });
    for(final ImmutablePair<File, File> files : temporaryFiles) {
      final BinaryFileBuffer sourceFileBuffer =
          new BinaryFileBuffer(files.getLeft(), outputCharSet);
      final BinaryFileBuffer targetFileBuffer =
          new BinaryFileBuffer(files.getRight(), outputCharSet);

      pq.add(
          new ImmutablePair<BinaryFileBuffer, BinaryFileBuffer>(
              sourceFileBuffer, targetFileBuffer));
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
          BinaryFileBuffer sourceBuffer = null;
          ImmutablePair<BinaryFileBuffer, BinaryFileBuffer> bufferPair = null;
          final Set<String> targetLines = new HashSet<String>();

          while(pq.size() > 0) {
            bufferPair = pq.poll();

            // Get source line
            sourceBuffer = bufferPair.getLeft();
            sourceLine = sourceBuffer.pop();
            // Process source line
            procSourceLine = lineProcessor.apply(sourceLine);

            // Lookup target line
            targetLine = bufferPair.getRight().pop();

            if(procSourceLine.compareTo(lastProcSourceLine) == 0) {
              procTargetLine = lineProcessor.apply(targetLine);

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
                logger.info(
                    "Duplicate sentence pair, source [" + sourceLine + "] with " +
                    "target [" + targetLine + "]");
                noDuplicates++;
              }
            } else {
              // Make a new set
              targetLines.clear();
              targetLines.add(lineProcessor.apply(targetLine));

              // Write source and target files
              lineCounter = writeSourceAndTargetLines(
                  sourceWriter,
                  sourceLine,
                  targetWriter,
                  targetLine,
                  lineCounter);
            }

            lastProcSourceLine = procSourceLine;

            if(sourceBuffer.empty() == true) {
              sourceBuffer.fbr.close();
              sourceBuffer.originalfile.delete();// we don't need you anymore
            } else {
              pq.add(bufferPair); // add it back
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
      for(final ImmutablePair<BinaryFileBuffer, BinaryFileBuffer> buffers : pq) {
        buffers.getLeft().close();
        buffers.getRight().close();
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
  private final int maxNoTempFiles;
  private final Logger logger;

  public CorpusUniquer(
      final File theSourceFile,
      final File theTargetFile,
      final Charset theInputCharSet,
      final int theMaxNumOfTempFiles,
      final File theTempDirectory,
      final Charset theOutputCharSet,
      final Logger theLogger) {
    sourceFile = theSourceFile;
    targetFile = theTargetFile;
    tempDirectory = theTempDirectory;
    inputCharSet = theInputCharSet;
    outputCharSet = theOutputCharSet;
    maxNoTempFiles = theMaxNumOfTempFiles;
    logger = theLogger;
  }
  
  public ImmutablePair<Long, Long> unique(
      final String suffix, final int maxNoTokens)
  throws Exception {
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
              "Missing files or directories: %s",
              StringUtils.join(missingFiles, ", ")));
    }

    final Comparator<String> comparator = new Comparator<String>() {
      @Override
      public int compare(String r1, String r2) {
        return r1.toLowerCase().compareTo(r2.toLowerCase());
      }
    };

    final Function<String, Boolean> noTokensFilter =
        (maxNoTokens <= ICorpusUniquer.UNLIMITED_TOKENS) ?
            new Function<String, Boolean>() {
              public Boolean apply(final String line) {
                return true;
              }
            } :
            new Function<String, Boolean>() {
              public Boolean apply(final String line) {
                final String[] split = line.split("[ ]+");
                return split.length <= maxNoTokens;
              }
            };
    final Function<ImmutablePair<String, String>, Boolean> filter =
        new Function<ImmutablePair<String, String>, Boolean>() {
          public Boolean apply(final ImmutablePair<String, String> srcTrg) {
            final String source = srcTrg.getLeft();
            final String target = srcTrg.getRight();
            
            if(source.length() < 1 || target.length() < 1) {
              return false;
            }

            if(noTokensFilter.apply(source) == false ||
               noTokensFilter.apply(target) == false) {
              return false;
            }
            
            return true;
          }
        };

    logger.info(
        "Starting uniquing with [" + sourceFile.getCanonicalPath() +
        "] and [" + targetFile.getCanonicalPath() + "] filtering on " +
        ((maxNoTokens == ICorpusUniquer.UNLIMITED_TOKENS) ?
            "infinite number of" :
            maxNoTokens) +
        " tokens using suffix [" + suffix + "]...");
    
    // Sort...
    final List<ImmutablePair<File, File>> tempFiles =
        sortInBatch(comparator, filter);
    // ...and merge
    final File outputSourceFile = new File(
        sourceFile.getAbsolutePath() + "." + suffix);
    final File outputTargetFile = new File(
        targetFile.getAbsolutePath() + "." + suffix);
    final Function<String, String> lineProcessor =
        new Function<String, String>() {
          public String apply(final String line) {
            return line.toLowerCase().replaceAll("[ ]+", "_");
          }
        };
    final ImmutablePair<Long, Long> result =
        mergeSortedFiles(
            outputSourceFile,
            outputTargetFile,
            tempFiles,
            comparator,
            lineProcessor);
    
    logger.info("Finished uniquing");
    
    return result;
  }
  
  public ImmutablePair<Long, Long> uniqueWithLineCountCheck(
      final String suffix, final int maxNoTokens)
  throws Exception {
    final FileLineCounter counter =
        new FileLineCounter(inputCharSet, sourceFile, targetFile);
    final Map<File, Long> lineNos = counter.countLines();
    final long sourceLineNos = lineNos.get(sourceFile);
    final long targetLineNos = lineNos.get(targetFile);
    if(sourceLineNos != targetLineNos) {
      throw new Exception("Source and target files have line count mismatch");
    }

    return unique(suffix, maxNoTokens);
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
