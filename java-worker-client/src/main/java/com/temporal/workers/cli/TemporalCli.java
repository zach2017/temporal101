package com.temporal.workers.cli;

import picocli.CommandLine;

/**
 * Temporal Workers CLI
 * ====================
 * A command-line client for interacting with Temporal workflows.
 *
 * Usage:
 *     java -cp app.jar com.temporal.workers.cli.TemporalCli <command> [options]
 *
 * Commands:
 *     start     Start a new Hello World workflow
 *     status    Check the status of a workflow
 *     result    Wait for and retrieve a workflow result
 *     describe  Show full details of a workflow execution
 *     cancel    Cancel a running workflow
 *     terminate Terminate a running workflow immediately
 *     list      List recent workflow executions
 */
@CommandLine.Command(
        name = "temporal-cli",
        description = "CLI client for Temporal workers",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        subcommands = {
                StartCommand.class,
                StatusCommand.class,
                ResultCommand.class,
                DescribeCommand.class,
                CancelCommand.class,
                TerminateCommand.class,
                ListCommand.class,
        }
)
public class TemporalCli implements Runnable {

    @Override
    public void run() {
        // No subcommand given — print help
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TemporalCli()).execute(args);
        System.exit(exitCode);
    }
}
