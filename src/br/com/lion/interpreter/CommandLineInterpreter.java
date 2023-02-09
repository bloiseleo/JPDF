package br.com.lion.interpreter;

import java.util.HashMap;

public class CommandLineInterpreter {

    public HashMap<String, String> interpretate(String[] args) {
        HashMap<String, String> commandMap = new HashMap<>();
        for(int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.contains("--")) {
                String argWithOutHifens = arg.replace("-", "");
                if(argWithOutHifens.contains("=")) {
                    String[] argAndParam = argWithOutHifens.split("=");
                    commandMap.put(argAndParam[0], argAndParam[1]);
                    continue;
                }
                commandMap.put(argWithOutHifens, this.getParamFromNextPosition(i, args));
                i++;
                continue;
            }
            commandMap.put("mainCommand", arg);
        }
        return commandMap;
    }
    private String getParamFromNextPosition(int position, String[] args) {
        if(args.length == position) {
            throw new IllegalArgumentException("Missing argument on position: " + (position+1));
        }
        return args[position + 1];
    }
}
