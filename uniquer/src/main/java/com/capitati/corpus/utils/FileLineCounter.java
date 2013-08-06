package com.capitati.corpus.utils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import org.apache.commons.lang3.tuple.ImmutablePair;

public class FileLineCounter implements ILineCounter<File> {
  final Collection<File> files = new ArrayList<File>();
  final Charset charSet;

  public FileLineCounter(final Charset theCharSet, final File ... theFiles) {
    for(final File aFile : theFiles) {
      files.add(aFile);
    }
    charSet = theCharSet;
  }

  @Override
  public Map<File, Long> countLines()
  throws InterruptedException, ExecutionException {
    final ForkJoinPool pool = new ForkJoinPool();
    final List<ForkJoinTask<ImmutablePair<File, Long>>> tasks =
        new ArrayList<ForkJoinTask<ImmutablePair<File, Long>>>();

    for(final File file : files) {
      final Callable<ImmutablePair<File, Long>> task =
          new Callable<ImmutablePair<File, Long>>() {
            @Override
            public ImmutablePair<File, Long> call() throws Exception {
              final Scanner scanner = new Scanner(file, charSet.name());
              
              try {
                long lineCnt = 0;
                while(scanner.hasNextLine() == true) {
                  lineCnt++;
                  scanner.nextLine();
                }
                
                return new ImmutablePair<File, Long>(file, lineCnt);
              } finally {
                scanner.close();
              }
            }
          };
      tasks.add(pool.submit(task));
    }

    final Map<File, Long> lineNos = new HashMap<File, Long>();
    for(final ForkJoinTask<ImmutablePair<File, Long>> task : tasks) {
      final ImmutablePair<File, Long> result = task.get();

      lineNos.put(result.getLeft(), result.getRight());
    }
    
    return lineNos;
  }
}
