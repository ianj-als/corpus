package com.capitati.corpus.utils;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.NOPLogger;
import org.apache.log4j.spi.NOPLoggerRepository;

public class Main {
  @SuppressWarnings("static-access")
  private static Options createOptions() {
    final Options options = new Options();
    
    options.addOption(
        OptionBuilder.
          withLongOpt("source").
          withDescription("Source filename").
          hasArg().
          isRequired().
          withArgName("FILENAME").
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("target").
          withDescription("Target filename").
          hasArg().
          isRequired().
          withArgName("FILENAME").
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("inputcharset").
          withDescription("Character set for the input files").
          hasArg().
          withArgName("CHARSET-CODE").
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("outputcharset").
          withDescription("Character set for the output files").
          hasArg().
          withArgName("CHARSET-CODE").
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("suffix").
          withDescription("Suffix added to uniqued files").
          hasArg().
          withArgName("STRING").
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("workingdir").
          withDescription("Working directory for temporary files").
          hasArg().
          withArgName("DIR NAME").
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("nofiles").
          withDescription("The number of temporary files to use for the " +
                          "sort phase (Hint only)").
          hasArg().
          withType(Integer.class).
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("verbose").
          withDescription("Verbose output").
          hasArg(false).
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("logfile").
          withDescription("Log the corpus drops and duplications").
          hasArgs().
          withArgName("LOG FILENAME").
          create());
    options.addOption(
        OptionBuilder.
          withLongOpt("maxnotokens").
          withDescription("The maximum number of tokens in the source").
          hasArg().
          withArgName("NUM TOKENS").
          withType(Integer.class).
          create());
    
    return options;
  }
  
  private static void displayHelp(final Options options) {
    final HelpFormatter hf = new HelpFormatter();
    hf.printHelp("uniquer", options, true);
    System.exit(0);
  }
  
  public static void main(String[] args) throws Exception {
    final Options options = createOptions();
    final CommandLineParser clp = new PosixParser();
    
    try {
      final CommandLine cl = clp.parse(options, args, true);
      
      if(cl.hasOption("help") == true || cl.hasOption("h") == true) {
        displayHelp(options);
      }
      
      final String sourceFile = cl.getOptionValue("source");
      final String targetFile = cl.getOptionValue("target");
      final String inputCharSet = cl.getOptionValue("inputcharset", "UTF-8");
      final String outputCharSet = cl.getOptionValue("outputcharset", "UTF-8");
      final String suffix = cl.getOptionValue("suffix", "uniq");
      final String workingDir = cl.getOptionValue("workingdir", "/tmp");
      final String logFile = cl.getOptionValue("logfile", null);
      final int noFiles = Integer.parseInt(cl.getOptionValue("nofiles", "100"));
      final int maxNoTokens =
          Integer.parseInt(cl.getOptionValue("maxnotokens", "-1"));
      final boolean verbose = cl.hasOption("verbose");
      
      Logger logger = null;
      if(logFile != null) {
        BasicConfigurator.configure(
            new FileAppender(
                new EnhancedPatternLayout(
                    "%d{dd MMM yyyy HH:mm:ss,SSS}: %-5p: %m%n"),
                logFile));
        logger = Logger.getLogger("uniquer");
      } else {
        BasicConfigurator.configure();
        logger = new NOPLogger(new NOPLoggerRepository(), "uniquer");
      }
      
      final CorpusUniquer sorter = new CorpusUniquer(
          new File(sourceFile),
          new File(targetFile),
          Charset.forName(inputCharSet),
          noFiles,
          new File(workingDir),
          Charset.forName(outputCharSet),
          logger);
      final ImmutablePair<Long, Long> result = sorter.unique(suffix, maxNoTokens);

      logger.info(
          "Wrote " + result.getRight() + " sentence pairs and dropped " +
          result.getLeft() + " duplicates.");
      
      if(verbose) {
        System.out.println(
            String.format(
                "Wrote %d sentence pairs and dropped %d duplicates",
                result.getRight(), result.getLeft()));
      }
    } catch(final ParseException ex) {
      displayHelp(options);
    }
  }
}
