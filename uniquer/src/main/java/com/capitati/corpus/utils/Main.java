package com.capitati.corpus.utils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.DisplaySetting;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.OptionException;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.builder.SwitchBuilder;
import org.apache.commons.cli2.commandline.Parser;
import org.apache.commons.cli2.option.Switch;
import org.apache.commons.cli2.util.HelpFormatter;
import org.apache.commons.cli2.validation.InvalidArgumentException;
import org.apache.commons.cli2.validation.Validator;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.NOPLogger;
import org.apache.log4j.spi.NOPLoggerRepository;

public class Main {
  @SuppressWarnings({"unchecked"})
  private static Map<String, Object> parseCommandLineOptions(
      final String[] args) {
    final DefaultOptionBuilder oBuilder = new DefaultOptionBuilder();
    final ArgumentBuilder aBuilder = new ArgumentBuilder();
    final SwitchBuilder sBuilder = new SwitchBuilder();
    final GroupBuilder gBuilder = new GroupBuilder();
    final Validator fileValidator = new Validator() {
      @Override
      public void validate(@SuppressWarnings("rawtypes") final List args)
      throws InvalidArgumentException {
        final String filename = (String )args.get(0);
        final File file = new File(filename);
        if(file.exists() == false)
          throw new InvalidArgumentException(
              "File [" + filename + "] does not exist");
      }
    };
    final Validator directoryValidator = new Validator() {
      @Override
      public void validate(@SuppressWarnings("rawtypes") final List args)
      throws InvalidArgumentException {
        final String filename = (String )args.get(0);
        final File file = new File(filename);
        if(file.exists() == false || file.isDirectory() == false)
          throw new InvalidArgumentException(
              "Directory [" + filename + "] does not exist");
      }
    };
    final Validator charsetValidator = new Validator() {
      @Override
      public void validate(@SuppressWarnings("rawtypes") final List args)
      throws InvalidArgumentException {
        final String charset = (String )args.get(0);
        try {
          Charset.forName(charset);
        } catch(final IllegalCharsetNameException illEx) {
          throw new InvalidArgumentException(
              "Illegal character set name: [" + charset +"]");
        } catch(final UnsupportedCharsetException unEx) {
          throw new InvalidArgumentException(
              "Unsupported character set name: [" + charset +"]");
        }
      }
    };
    final Option help = oBuilder.
        withLongName("help").
        withShortName("h").
        withDescription("Print help message and exit").
        withRequired(false).
        create();
    final Option source = oBuilder.
        withLongName("source").
        withShortName("s").
        withDescription("Source filename").
        withArgument(
            aBuilder.
            withName("FILE").
            withMinimum(1).
            withMaximum(1).
            withValidator(fileValidator).
            create()).
        withRequired(true).
        create();
    final Option target = oBuilder.
        withLongName("target").
        withShortName("t").
        withDescription("Target filename").
        withArgument(
            aBuilder.
            withName("FILE").
            withMinimum(1).
            withMaximum(1).
            withValidator(fileValidator).
            create()).
        withRequired(true).
        create();
    final Option inputCharSet = oBuilder.
        withLongName("inputcharset").
        withShortName("i").
        withDescription("Character set for the input files").
        withArgument(
            aBuilder.
            withName("CHARSET").
            withMinimum(1).
            withMaximum(1).
            withValidator(charsetValidator).
            withDefault("UTF-8").
            create()).
        withRequired(false).
        create();
    final Option outputCharSet = oBuilder.
        withLongName("outputcharset").
        withShortName("o").
        withDescription("Character set for the output files").
        withArgument(
            aBuilder.
            withName("CHARSET").
            withMinimum(1).
            withMaximum(1).
            withValidator(charsetValidator).
            withDefault("UTF-8").
            create()).
        withRequired(false).
        create();
    final Option suffix = oBuilder.
        withLongName("suffix").
        withShortName("x").
        withDescription("Suffix added to the output filenames").
        withArgument(
            aBuilder.
            withName("SUFFIX").
            withMinimum(1).
            withMaximum(1).
            withDefault("uniq").
            create()).
        withRequired(false).
        create();
    final Option workingDir = oBuilder.
        withLongName("workingdir").
        withShortName("w").
        withDescription("Working directory for temporary files").
        withArgument(
            aBuilder.
            withName("DIRECTORY").
            withMinimum(1).
            withMaximum(1).
            withValidator(directoryValidator).
            withDefault(System.getProperty("java.io.tmpdir")).
            create()).
        withRequired(false).
        create();
    final Option noFiles = oBuilder.
        withLongName("nofiles").
        withShortName("n").
        withDescription(
            "The number of temporary files to use (Hint only)").
        withArgument(
            aBuilder.
            withName("NUMBER").
            withMinimum(1).
            withMaximum(1).
            withDefault("100").
            create()).
        withRequired(false).
        create();
    final Option logFile = oBuilder.
          withLongName("logfile").
          withShortName("l").
          withDescription("Create a log of the corpus drops and duplications").
          withArgument(
              aBuilder.
              withName("FILENAME").
              withMinimum(1).
              withMaximum(1).
              create()).
          withRequired(false).
          create();
    final Option maxNoTokens = oBuilder.
          withLongName("maxnotokens").
          withShortName("m").
          withDescription(
              "Only use source sentences that have no more than a maximum " +
              "number of tokens").
          withArgument(
              aBuilder.
              withName("NUMBER").
              withMinimum(1).
              withMaximum(1).
              withDefault(Integer.toString(ICorpusUniquer.UNLIMITED_TOKENS)).
              create()).
          withRequired(false).
          create();
    final Switch verbose = sBuilder.
        withName("v").
        withDescription("Verbose output").
        withSwitchDefault(false).
        create();
    final Group group = gBuilder.
        withName("uniquing options").
        withOption(source).
        withOption(target).
        withOption(inputCharSet).
        withOption(outputCharSet).
        withOption(suffix).
        withOption(workingDir).
        withOption(noFiles).
        withOption(logFile).
        withOption(maxNoTokens).
        withOption(verbose).
        create();
    final Group helpGroup = gBuilder.
        withName("help options").
        withOption(help).
        create();
    final Group allGroup = gBuilder.
        withName("options").
        withOption(group).
        withOption(helpGroup).
        create();
    final Parser parser = new Parser();
    final HelpFormatter hf = new HelpFormatter();
    hf.setGroup(allGroup);
    hf.setShellCommand("uniquer");
    hf.getFullUsageSettings().add(DisplaySetting.ALL);

    // Parse helper options
    parser.setGroup(helpGroup);
    try {
      final CommandLine hcl = parser.parse(args);
      if(hcl.hasOption(help) == true) {
        hf.print();
        System.exit(7);
        return null;
      }
    } catch(final OptionException ex) {
      // Ignore
    }

    // Parse the rest of the command line
    parser.setGroup(group);
    CommandLine cl = null;    
    try {
      cl = parser.parse(args);
    } catch(final OptionException ex) {
      System.err.println(ex);
    }
    if(cl == null) {
      System.exit(6);
    }

    final CommandLine mcl = cl;
    final Map<String, Object> values = new HashMap<String, Object>() {
      private static final long serialVersionUID = 1L;
      {
        put("source", mcl.getValue(source));
        put("target", mcl.getValue(target));
        put("inputcharset", mcl.getValue(inputCharSet));
        put("outputcharset", mcl.getValue(outputCharSet));
        put("suffix", mcl.getValue(suffix));
        put("workingdir", mcl.getValue(workingDir));
        put("logfile", mcl.getValue(logFile, null));
        put("nofiles", Integer.parseInt((String )mcl.getValue(noFiles)));
        put("maxnotokens", Integer.parseInt((String )mcl.getValue(maxNoTokens)));
        put("verbose", new Boolean(mcl.getSwitch(verbose)));
      }};
      
      return values;
  }

  public static void main(String[] args) throws Exception {
    final Map<String, Object> values = parseCommandLineOptions(args);
    final String logFile = (String )values.get("logfile");

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
        new File((String )values.get("source")),
        new File((String )values.get("target")),
        Charset.forName((String )values.get("inputcharset")),
        (Integer )values.get("nofiles"),
        new File((String )values.get("workingdir")),
        Charset.forName((String )values.get("outputcharset")),
        logger);
    final String suffix = (String )values.get("suffix");
    final int maxNoTokens = (Integer )values.get("maxnotokens");
    final ImmutablePair<Long, Long> result = sorter.unique(suffix, maxNoTokens);

    logger.info(
        "Wrote " + result.getRight() + " sentence pairs and dropped " +
            result.getLeft() + " duplicates.");

    final boolean verbose = (Boolean )values.get("verbose");
    if(verbose) {
      System.out.println(
          String.format(
              "Wrote %d sentence pairs and dropped %d duplicates",
              result.getRight(), result.getLeft()));
    }

    System.exit(0);
  }
}
