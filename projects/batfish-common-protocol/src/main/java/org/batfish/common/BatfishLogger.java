package org.batfish.common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class BatfishLogger {

  public static class BatfishLoggerHistory extends ArrayList<HistoryItem> {
    /** */
    private static final long serialVersionUID = 1L;

    public String toString(int logLevel) {
      StringBuilder sb = new StringBuilder();
      for (HistoryItem item : this) {
        if (item.getLevel() <= logLevel) {
          sb.append(item.getMessage());
        }
      }
      return sb.toString();
    }
  }

  private static class HistoryItem extends Pair<Integer, String> {
    /** */
    private static final long serialVersionUID = 1L;

    private HistoryItem(int i, String s) {
      super(i, s);
    }

    public int getLevel() {
      return _first;
    }

    public String getMessage() {
      return _second;
    }
  }

  public static enum LogLevel {
    ERROR(200),
    DEBUG(500),
    FATAL(100),
    INFO(400),
    OUTPUT(220),
    PEDANTIC(550),
    REDFLAG(250),
    UNIMPLEMENTED(270),
    WARN(300);

    private final int _priority;

    private LogLevel(int priority) {
      _priority = priority;
    }

    public boolean atLeast(LogLevel minimumVerbosityLevel) {
      return _priority >= minimumVerbosityLevel._priority;
    }

    public int priority() {
      return _priority;
    }
  }

  private static final int LOG_ROTATION_THRESHOLD = 10000;

  private static String getRotatedLogFilename(String logFilename) {
    DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
    String baseRotatedLogFilename = logFilename + '-' + df.format(new Date());
    File rotatedLogFile = new File(baseRotatedLogFilename);
    String returnFilename = baseRotatedLogFilename;

    int index = 0;
    while (rotatedLogFile.exists()) {
      returnFilename = baseRotatedLogFilename + "." + index;
      rotatedLogFile = new File(returnFilename);
      index++;
    }

    return returnFilename;
  }

  private final BatfishLoggerHistory _history;

  private LogLevel _level;

  private String _logFile;

  private int _numLinesSinceRotation = 0;

  private PrintStream _ps;

  private boolean _rotateLog = false;

  private long _timerCount;

  private boolean _timestamp;

  public BatfishLogger(String logLevel, boolean timestamp) {
    _timestamp = timestamp;
    setLogLevel(logLevel);
    _history = new BatfishLoggerHistory();
  }

  public BatfishLogger(String logLevel, boolean timestamp, PrintStream stream) {
    _history = null;
    _timestamp = timestamp;
    String levelStr = logLevel;
    setLogLevel(levelStr);
    _ps = stream;
  }

  public BatfishLogger(
      String logLevel, boolean timestamp, String logFile, boolean logTee, boolean rotateLog) {
    _history = null;
    _timestamp = timestamp;
    String levelStr = logLevel;
    setLogLevel(levelStr);
    _logFile = logFile;
    if (_logFile != null) {

      // if the file already exists, archive it
      File logFileFile = new File(_logFile);
      if (logFileFile.exists()) {
        String rotatedLog = getRotatedLogFilename(_logFile);
        if (!logFileFile.renameTo(new File(rotatedLog))) {
          throw new BatfishException(
              String.format("Failed to rename %s to %s", _logFile, rotatedLog));
        }
      }

      PrintStream filePrintStream = null;
      try {
        filePrintStream = new PrintStream(_logFile, "UTF-8");
        _rotateLog = rotateLog;
      } catch (FileNotFoundException | UnsupportedEncodingException e) {
        throw new BatfishException("Could not create logfile", e);
      }
      if (logTee) {
        _ps = new CompositePrintStream(System.out, filePrintStream);
      } else {
        _ps = filePrintStream;
      }

    } else {
      _ps = System.out;
    }
  }

  public void append(BatfishLoggerHistory history) {
    append(history, "");
  }

  public void append(BatfishLoggerHistory history, String prefix) {
    for (HistoryItem item : history) {
      int level = item.getLevel();
      String msg = prefix + item.getMessage();
      write(level, msg);
    }
  }

  public void close() {
    if (_logFile != null) {
      _ps.close();
    }
  }

  public void debug(String msg) {
    write(LogLevel.DEBUG.priority(), msg);
  }

  public void debugf(String format, Object... args) {
    debug(String.format(format, args));
  }

  public void error(String msg) {
    write(LogLevel.ERROR.priority(), msg);
  }

  public void errorf(String format, Object... args) {
    error(String.format(format, args));
  }

  public void fatal(String msg) {
    write(LogLevel.FATAL.priority(), msg);
  }

  private double getElapsedTime(long beforeTime) {
    long difference = System.currentTimeMillis() - beforeTime;
    double seconds = difference / 1000d;
    return seconds;
  }

  public BatfishLoggerHistory getHistory() {
    return _history;
  }

  public PrintStream getPrintStream() {
    return _ps;
  }

  public void info(String msg) {
    write(LogLevel.INFO.priority(), msg);
  }

  public void infof(String format, Object... args) {
    info(String.format(format, args));
  }

  public boolean isActive(int level) {
    return level <= _level._priority;
  }

  public void output(String msg) {
    write(LogLevel.OUTPUT.priority(), msg);
  }

  public void outputf(String format, Object... args) {
    output(String.format(format, args));
  }

  public void pedantic(String msg) {
    write(LogLevel.PEDANTIC.priority(), msg);
  }

  public void printElapsedTime() {
    double seconds = getElapsedTime(_timerCount);
    info("Time taken for this task: " + seconds + " seconds\n");
  }

  public void redflag(String msg) {
    write(LogLevel.REDFLAG.priority(), msg);
  }

  public void resetTimer() {
    _timerCount = System.currentTimeMillis();
  }

  private synchronized void rotateLog() {
    if (_logFile != null && _ps != null) {

      _ps.close();

      String rotatedLog = getRotatedLogFilename(_logFile);

      File logFile = new File(_logFile);
      if (!logFile.renameTo(new File(rotatedLog))) {
        throw new BatfishException(
            String.format("Failed to rename %s to %s", _logFile, rotatedLog));
      }

      try {
        PrintStream filePrintStream = new PrintStream(_logFile, "UTF-8");

        if (_ps instanceof CompositePrintStream) {
          _ps = new CompositePrintStream(System.out, filePrintStream);
        } else {
          _ps = filePrintStream;
        }
      } catch (Exception e) {
        // we have this try catch because new PrintStream throws
        // FileNotFoundException
        // this should not happen since we know that logFile can be created
        // in case it does happen, we cannot log this error to the log :)
        System.err.print("Could not rotate log" + e.getMessage());
      }
    }
  }

  public LogLevel getLogLevel() {
    return _level;
  }

  public void setLogLevel(LogLevel logLevel) {
    _level = logLevel;
  }

  public void setLogLevel(String logLevel) {
    _level = LogLevel.valueOf(logLevel.toUpperCase());
  }

  public void unimplemented(String msg) {
    write(LogLevel.UNIMPLEMENTED.priority(), msg);
  }

  public void warn(String msg) {
    write(LogLevel.WARN.priority(), msg);
  }

  public void warnf(String format, Object... args) {
    warn(String.format(format, args));
  }

  private synchronized void write(int level, String msg) {
    if (isActive(level)) {
      String outputMsg;
      if (_timestamp) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr = df.format(new Date());
        outputMsg = String.format("%s: %s", dateStr, msg);
      } else {
        outputMsg = msg;
      }
      if (_ps != null) {
        _ps.print(outputMsg);

        // logic for rotating log
        if (_rotateLog) {
          _numLinesSinceRotation++;

          if (_numLinesSinceRotation > LOG_ROTATION_THRESHOLD) {
            rotateLog();
            _numLinesSinceRotation = 0;
          }
        }
      } else {
        _history.add(new HistoryItem(level, msg));
      }
    }
  }
}
