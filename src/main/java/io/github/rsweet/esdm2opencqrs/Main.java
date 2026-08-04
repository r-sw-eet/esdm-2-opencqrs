package io.github.rsweet.esdm2opencqrs;

import io.github.rsweet.esdm2opencqrs.cli.ConformanceCommand;
import io.github.rsweet.esdm2opencqrs.cli.GenerateCommand;
import io.github.rsweet.esdm2opencqrs.cli.TargetsCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "esdm2opencqrs",
        description = "ESDM model in, a runnable Spring Boot + OpenCQRS application out.",
        mixinStandardHelpOptions = true,
        version = "esdm2opencqrs 0.1.0",
        subcommands = {GenerateCommand.class, TargetsCommand.class, ConformanceCommand.class})
public final class Main implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
