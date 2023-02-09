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
        commandMap.put("mainCommand", "create_pdf");
        commandMap.put("pdfName", "teste");
        return new CommandBuilder(commands).buildCommandFrom(commandMap);
    }

    public CommandHandler buildCommandFrom(HashMap<String, String> commandMap) {
        Class<? extends CommandHandler> commandHandlerClassRef = commands.get(commandMap.get("mainCommand"));
        commandMap.remove("mainCommand");
        Class[] parameterType = new Class[1];
        parameterType[0] = HashMap.class;
        try {
            return commandHandlerClassRef.getDeclaredConstructor(parameterType).newInstance(commandMap);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
