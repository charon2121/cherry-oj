package com.cherryoj.judgingservice.judge;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 judge 返回的编译诊断、运行错误片段整理成可以给用户看的文字。
 *
 * <p>CORE 的用户文件 = 语言前缀 + 用户代码，编译器报的行号比编辑器多出前缀行数。这里不按语言分支，只认用户文件名后面的行号，
 * 统一减去前缀行数；落在前缀里的行标成 [预置代码]。各编译器、解释器写位置只有四种写法（文件:行、文件(行,、文件", line 行、
 * 文件 on line 行），另有紧跟在位置之后的源码摘录（"  行 |"）。13 门语言的真实输出见
 * 本模块测试资源 diagnostics/。驱动文件的位置与源码照常显示，不做遮掩。
 *
 * <p>所有路径只保留文件名：用户不需要、也不该看到判题节点的目录。
 */
public final class JudgeDiagnostics {
    static final String PRELUDE = "[预置代码]";

    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    /** 目录部分：前面不能紧挨着字母数字或点（避免把 "2/3"、"a.b/c" 当路径），后面必须还有文件名（避免吃掉 " / "）。 */
    private static final Pattern DIRECTORIES = Pattern.compile(
            "(?<![\\w.])(?:[A-Za-z]:)?(?:\\.{1,2})?[/\\\\](?:[^\\s/\\\\:\"'()\\[\\]]+[/\\\\])*(?=[^\\s/\\\\:\"'()\\[\\]])");
    private static final Pattern ANY_LOCATION = Pattern.compile(
            "[\\w.\\-]+\\.\\w+(?::\\d+|\\(\\d+,|\", line \\d+| on line \\d+)");
    private static final Pattern GUTTER = Pattern.compile("^(\\s*)(\\d+)( \\|)");

    private JudgeDiagnostics() {
    }

    /** 编译器的诊断总以换行结尾；judge 只给出截断后的片段、不给截断标记，最后没有换行就说明被切过。 */
    public static boolean looksCut(String compilerOutput) {
        return compilerOutput != null && !compilerOutput.isEmpty() && !compilerOutput.endsWith("\n");
    }

    /**
     * ACM 或 prefixLines 为 null 时只清理控制字符与路径。CORE 时还换算用户文件的行号；truncated 表示 judge 按长度截断过
     * 这段文字，最后一行可能是被切断的半行（solution.cpp:12 被切成 solution.cpp:1 会被换算错），先丢掉再换算。
     */
    public static String forUser(String text, String solutionFile, Integer prefixLines, boolean truncated) {
        if (text == null) {
            return null;
        }
        String cleaned = stripDirectories(CONTROL.matcher(ANSI.matcher(text).replaceAll("")).replaceAll(""));
        if (solutionFile == null || prefixLines == null) {
            return cleaned;
        }
        int lastNewline = cleaned.lastIndexOf('\n');
        if (truncated && lastNewline != cleaned.length() - 1) {
            cleaned = cleaned.substring(0, lastNewline + 1);
        }
        return shiftSolutionLines(cleaned, solutionFile, prefixLines);
    }

    static String stripDirectories(String text) {
        return DIRECTORIES.matcher(text).replaceAll("");
    }

    static String shiftSolutionLines(String text, String solutionFile, int prefixLines) {
        String name = Pattern.quote(solutionFile);
        String guard = "(?<![\\w.\\-])(";
        List<Pattern> locations = List.of(
                Pattern.compile(guard + name + ":)(\\d+)"),
                Pattern.compile(guard + name + "\\()(\\d+)(?=,)"),
                Pattern.compile(guard + name + "\", line )(\\d+)"),
                Pattern.compile(guard + name + " on line )(\\d+)"));
        StringBuilder out = new StringBuilder(text.length());
        boolean excerptBelongsToSolution = false;
        for (String line : text.split("\n", -1)) {
            String rewritten = line;
            boolean solutionLocation = false;
            for (Pattern location : locations) {
                Matcher matcher = location.matcher(rewritten);
                StringBuilder replaced = new StringBuilder();
                while (matcher.find()) {
                    solutionLocation = true;
                    matcher.appendReplacement(replaced, Matcher.quoteReplacement(
                            shiftedLocation(matcher.group(1), solutionFile, Long.parseLong(matcher.group(2)), prefixLines)));
                }
                matcher.appendTail(replaced);
                rewritten = replaced.toString();
            }
            if (solutionLocation) {
                excerptBelongsToSolution = true;
            }
            else if (ANY_LOCATION.matcher(line).find()) {
                excerptBelongsToSolution = false;
            }
            else if (excerptBelongsToSolution) {
                rewritten = shiftedGutter(rewritten, prefixLines);
            }
            out.append(rewritten).append('\n');
        }
        return out.substring(0, out.length() - 1);
    }

    private static String shiftedLocation(String head, String solutionFile, long line, int prefixLines) {
        if (line > prefixLines) {
            return head + (line - prefixLines);
        }
        return head.replace(solutionFile, PRELUDE) + line;
    }

    /** 源码摘录左侧的行号同样换算，并保持原来的宽度；前缀里的摘录行不动。 */
    private static String shiftedGutter(String line, int prefixLines) {
        Matcher matcher = GUTTER.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        long number = Long.parseLong(matcher.group(2));
        if (number <= prefixLines) {
            return line;
        }
        String shifted = String.valueOf(number - prefixLines);
        String padded = " ".repeat(Math.max(0, matcher.group(2).length() - shifted.length())) + shifted;
        return matcher.group(1) + padded + matcher.group(3) + line.substring(matcher.end());
    }
}
