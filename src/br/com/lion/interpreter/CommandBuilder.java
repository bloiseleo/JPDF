package br.com.lion.interpreter;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class CommandBuilder {

    private final HashMap<String, Class<? extends CommandHandler>> commands;

    public CommandBuilder(HashMap<String, Class<? extends CommandHandler>> commands) {
        this.commands = commands;
    }
    public static CommandBuilder from(HashMap<String, Class<? extends CommandHandler>> commands) {
        return new CommandBuilder(commands);
    }

    public static CommandHandler from(HashMap<String, Class<? extends CommandHandler>> commands, String[] args) {
        CommandLineInterpreter cli = new CommandLineInterpreter();
        HashMap<String, String> commandMap = cli.interpretate(args);
        return new CommandBuilder(commands).buildCommandFrom(commandMap);
    }

    public CommandHandler buildCommandFrom(HashMap<String, String> commandMap) {
        Class<? extends CommandHandler> commandHandlerClassRef = commands.get(commandMap.get("mainCommand"));
        try {
            CommandHandler commandHandler = commandHandlerClassRef.getDeclaredConstructor().newInstance();
            return commandHandler;
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
