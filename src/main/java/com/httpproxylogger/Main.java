package com.httpproxylogger;

import org.apache.commons.cli.*;

import java.io.File;

/**
 * Created by jarmolow on 2015-06-25.
 */
public class Main {

    public static final String DEFAULT_FOLDER = "http-proxy-logger-requests";

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("f").desc("Output folder (defaults to [" + DEFAULT_FOLDER + "])").hasArg(true).build());

        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
            if (cmd.getArgList().size() != 3) {
                throw new ParseException("You must provide host and ports to bind to");
            }
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            usage(options);
            return;
        }
        String out;
        if (cmd.hasOption("f")) {
            out = cmd.getOptionValue("f");
        } else {
            out = DEFAULT_FOLDER;
        }

        new ForwardingProxy(new File(out), Integer.parseInt(cmd.getArgList().get(0)), cmd.getArgList().get(1), Integer.parseInt(cmd.getArgList().get(2)))
                .bind();

    }

    private static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(Main.class.getName() + " [options] portToBindTo hostToForwardTo portToForwardTo", options);
    }
}
