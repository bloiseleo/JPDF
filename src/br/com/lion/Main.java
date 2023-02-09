package br.com.lion;

import br.com.lion.commandHandler.CreatePDF;
import br.com.lion.interpreter.CommandBuilder;
import br.com.lion.interpreter.CommandHandler;
import br.com.lion.interpreter.MainCommandBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Main {
    private final static ArrayList<Class<? extends CommandHandler>> commandHandlers = new ArrayList<>(
            Arrays.asList(
                    CreatePDF.class
            )
    );
    private static HashMap<String, Class<? extends CommandHandler>> commands = new HashMap<>();
    private static void registerCommands() {
        commands = MainCommandBuilder.generate(commandHandlers);
    }
    public static void main(String[] args) throws IOException {
        registerCommands();
        CommandHandler commandHandler = CommandBuilder
                .from(commands, args);

        commandHandler.exec();
    }
}