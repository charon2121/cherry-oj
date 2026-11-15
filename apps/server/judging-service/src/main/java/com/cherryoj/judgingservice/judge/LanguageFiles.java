package com.cherryoj.judgingservice.judge;

/** 契约里的程序文件名；solutionFile 为 null 时该语言不支持 CORE。 */
public record LanguageFiles(String driverFile, String solutionFile) {
    public LanguageFiles {
        if (driverFile == null
                || !driverFile.matches("^[A-Za-z0-9_][A-Za-z0-9_.-]*$")
                || (solutionFile != null
                        && (!solutionFile.matches("^[A-Za-z0-9_][A-Za-z0-9_.-]*$") || driverFile.equals(solutionFile))))
            throw new IllegalArgumentException("files must be distinct plain file names");
    }
}
