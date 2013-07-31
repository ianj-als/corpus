package com.capitati.corpus.utils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;

public class Main {
  public static void displayUsage() {
    System.out
        .println("java com.capitati.corpus.utils.Main [flags] sourcefile targetfile");
    System.out.println("Flags are:");
    System.out.println("-v or --verbose: verbose output");
    System.out
        .println("-t or --maxtmpfiles (followed by an integer): specify an upper bound on the number of temporary files");
    System.out
        .println("-c or --charset (followed by a charset code): specify the character set to use (for sorting)");
    System.out
        .println("-s or --store (following by a path): where to store the temporary files");
    System.out.println("-h or --help: display this message");
  }

  public static void main(String[] args) throws Exception {
    boolean verbose = false;
    String sourcefile = null, targetfile = null;
    File tempFileStore = null;
    Charset cs = Charset.defaultCharset();
    final List<File> filesToSort = new ArrayList<File>();

    for(int param = 0; param < args.length; ++param) {
      if(args[param].equals("-v") || args[param].equals("--verbose")) {
        verbose = true;
      } else if((args[param].equals("-h") || args[param].equals("--help"))) {
        displayUsage();
        return;
      } else if((args[param].equals("-c") || args[param].equals("--charset"))
          && args.length > param + 1) {
        param++;
        cs = Charset.forName(args[param]);
      } else if((args[param].equals("-s") || args[param].equals("--store"))
          && args.length > param + 1) {
        param++;
        tempFileStore = new File(args[param]);
      } else {
        if(sourcefile == null)
          sourcefile = args[param];
        else if(targetfile == null) {
          targetfile = args[param];
        }
      }
    }

    if(verbose)
      System.out.println(String.format("Sorting %s and %s...", sourcefile, targetfile));

    final CorpusSorter sorter = new CorpusSorter(
        new File(sourcefile),
        new File(targetfile),
        Charset.forName("UTF-8"),
        "uniq",
        100,
        new File("/tmp"),
        Charset.forName("UTF-8"));    
    final ImmutablePair<Long, Long> result = sorter.sortWithUniquing();
    
    if(verbose)
      System.out.println(String.format("Dropped %d duplicates", result.getLeft()));
  }
}
