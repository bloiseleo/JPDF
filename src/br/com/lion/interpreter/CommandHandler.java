package br.com.lion.interpreter;

public abstract class CommandHandler {
    private final String commandName;
    public CommandHandler(String commandName) {
        this.commandName = commandName;
    }
    public abstract boolean exec();
    public String getCommandName() {
        return this.commandName;
    }
}
