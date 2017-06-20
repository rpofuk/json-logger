/*
DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
Copyright (c) 2016 Payara Foundation. All rights reserved.
The contents of this file are subject to the terms of the Common Development
and Distribution License("CDDL") (collectively, the "License").  You
may not use this file except in compliance with the License.  You can
obtain a copy of the License at
https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
or packager/legal/LICENSE.txt.  See the License for the specific
language governing permissions and limitations under the License.
When distributing the software, include this License Header Notice in each
file and include the License file at packager/legal/LICENSE.txt.
*/
package com.loraneo.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONObject;;

/**
 *
 * @author savage
 */
public class JSONLogFormatter extends Formatter {

    private static final String RECORD_NUMBER = "RecordNumber";
    private static final String METHOD_NAME = "MethodName";
    private static final String CLASS_NAME = "ClassName";

    private final HashMap<String, Object> loggerResourceBundleTable;
    private final LogManager logManager;

    private final Date date = new Date();

    private static boolean LOG_SOURCE_IN_KEY_VALUE = false;

    private static boolean RECORD_NUMBER_IN_KEY_VALUE = false;

    // Static Initialiser Block
    static {
        final String logSource = System.getProperty("com.sun.aas.logging.keyvalue.logsource");
        if ((logSource != null) && (logSource.equals("true"))) {
            LOG_SOURCE_IN_KEY_VALUE = true;
        }

        final String recordCount = System.getProperty("com.sun.aas.logging.keyvalue.recordnumber");
        if ((recordCount != null) && (recordCount.equals("true"))) {
            RECORD_NUMBER_IN_KEY_VALUE = true;
        }
    }

    private long recordNumber = 0;
    private String recordDateFormat;

    // Event separator
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    // String values for field keys
    private static final String TIMESTAMP_KEY = "_Timestamp";
    private static final String LOG_LEVEL_KEY = "_Level";
    private static final String PRODUCT_ID_KEY = "_Version";
    private static final String LOGGER_NAME_KEY = "_LoggerName";
    // String values for exception keys
    private static final String EXCEPTION_KEY = "_Exception";
    private static final String STACK_TRACE_KEY = "_StackTrace";
    // String values for thread excludable keys
    private static final String THREAD_ID_KEY = "_ThreadID";
    private static final String THREAD_NAME_KEY = "_ThreadName";
    private static final String LEVEL_VALUE_KEY = "_LevelValue";
    private static final String TIME_MILLIS_KEY = "_TimeMillis";
    private static final String MESSAGE_ID_KEY = "_MessageID";
    private static final String LOG_MESSAGE_KEY = "_LogMessage";

    private static final String RFC3339_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private String productId = "";

    public JSONLogFormatter() {
        super();
        loggerResourceBundleTable = new HashMap<String, Object>();
        logManager = LogManager.getLogManager();
    }

    @Override
    public String format(final LogRecord record) {
        return jsonLogFormat(record);
    }

    @Override
    public String formatMessage(final LogRecord record) {
        return jsonLogFormat(record);
    }

    /**
     * Payara can override this to specify product version.
     *
     * @return The string value of the product id.
     */
    protected String getProductId() {
        return System.getProperty("server.version");
    }

    /**
     * @param record
     *            The record to format.
     * @return The JSON formatted record.
     */
    private String jsonLogFormat(final LogRecord record) {
        try {
            final JSONObject eventObject = new JSONObject();

            /*
             * Create the timestamp field and append to object.
             */
            SimpleDateFormat dateFormatter;

            if (null != getRecordDateFormat()) {
                dateFormatter = new SimpleDateFormat(getRecordDateFormat());
            } else {
                dateFormatter = new SimpleDateFormat(RFC3339_DATE_FORMAT);
            }

            date.setTime(record.getMillis());
            final String timestampValue = dateFormatter.format(date);
            eventObject.put(TIMESTAMP_KEY,
                    timestampValue);

            /*
             * Create the event level field and append to object.
             */
            final Level eventLevel = record.getLevel();
            eventObject.put(LOG_LEVEL_KEY,
                    String.valueOf(eventLevel));

            /*
             * Get the product id and append to object.
             */
            productId = getProductId();
            eventObject.put(PRODUCT_ID_KEY,
                    productId);

            /*
             * Get the logger name and append to object.
             */
            String loggerName = record.getLoggerName();

            if (null == loggerName) {
                loggerName = "";
            }

            eventObject.put(LOGGER_NAME_KEY,
                    loggerName);

            /*
             * Get thread information and append to object if not excluded.
             */
            final int threadId = record.getThreadID();
            eventObject.put(THREAD_ID_KEY,
                    String.valueOf(threadId));

            // Thread Name
            String threadName;

            threadName = Thread.currentThread()
                    .getName();

            eventObject.put(THREAD_NAME_KEY,
                    threadName);

            /*
             * Get millis time for log entry timestamp
             */
            final Long timestamp = record.getMillis();
            eventObject.put(TIME_MILLIS_KEY,
                    String.valueOf(timestamp));

            /*
             * Include the integer value for log level
             */
            final Level level = record.getLevel();
            final int levelValue = level.intValue();
            eventObject.put(LEVEL_VALUE_KEY,
                    String.valueOf(levelValue));

            /*
             * Stick the message id on the entry
             */
            final String messageId = getMessageId(record);
            if (messageId != null && !messageId.isEmpty()) {
                eventObject.put(MESSAGE_ID_KEY,
                        messageId);
            }

            /*
             * Include ClassName and MethodName for FINER and FINEST log levels.
             */
            if (LOG_SOURCE_IN_KEY_VALUE || level.intValue() <= Level.FINE.intValue()) {
                final String sourceClassName = record.getSourceClassName();

                if (null != sourceClassName && !sourceClassName.isEmpty()) {
                    eventObject.put(CLASS_NAME,
                            sourceClassName);
                }

                final String sourceMethodName = record.getSourceMethodName();

                if (null != sourceMethodName && !sourceMethodName.isEmpty()) {
                    eventObject.put(METHOD_NAME,
                            sourceMethodName);
                }
            }

            /*
             * Add the record number to the entry.
             */
            if (RECORD_NUMBER_IN_KEY_VALUE) {
                recordNumber++;
                eventObject.put(RECORD_NUMBER,
                        String.valueOf(recordNumber));
            }

            String logMessage = record.getMessage();

            if (null == logMessage || logMessage.trim()
                    .equals("")) {
                final Throwable throwable = record.getThrown();
                if (null != throwable) {
                    try (StringWriter stringWriter = new StringWriter(); PrintWriter printWriter = new PrintWriter(stringWriter)) {
                        final JSONObject traceObject = new JSONObject();
                        throwable.printStackTrace(printWriter);
                        logMessage = stringWriter.toString();
                        traceObject.put(EXCEPTION_KEY,
                                throwable.getMessage());
                        traceObject.put(STACK_TRACE_KEY,
                                logMessage);
                        eventObject.put(LOG_MESSAGE_KEY,
                                traceObject);
                    }
                }
            } else {
                if (logMessage.contains("{0") && logMessage.contains("}") && null != record.getParameters()) {
                    logMessage = MessageFormat.format(logMessage,
                            record.getParameters());
                } else {
                    final ResourceBundle bundle = getResourceBundle(record.getLoggerName());
                    if (null != bundle) {
                        try {
                            logMessage = MessageFormat.format(bundle.getString(logMessage),
                                    record.getParameters());
                        } catch (final MissingResourceException ex) {
                            // Leave logMessage as it is because it already has
                            // an exception message
                        }
                    }
                }

                final StringBuilder logMessageBuilder = new StringBuilder();
                logMessageBuilder.append(logMessage);

                final Throwable throwable = getThrowable(record);
                if (null != throwable) {
                    try (StringWriter stringWriter = new StringWriter(); PrintWriter printWriter = new PrintWriter(stringWriter)) {
                        final JSONObject traceObject = new JSONObject();
                        throwable.printStackTrace(printWriter);
                        logMessage = stringWriter.toString();
                        traceObject.put(EXCEPTION_KEY,
                                logMessageBuilder.toString());
                        traceObject.put(STACK_TRACE_KEY,
                                logMessage);
                        eventObject.put(LOG_MESSAGE_KEY,
                                traceObject);
                    }
                } else {
                    logMessage = logMessageBuilder.toString();
                    eventObject.put(LOG_MESSAGE_KEY,
                            logMessage);
                }
            }

            return eventObject.toString() + LINE_SEPARATOR;

        } catch (final Exception ex) {
            new ErrorManager().error("Error in formatting Logrecord",
                    ex,
                    ErrorManager.FORMAT_FAILURE);
            return "";
        }
    }

    /**
     * @param record
     * @return
     */
    static String getMessageId(final LogRecord record) {
        final String message = record.getMessage();
        if (null != message && !message.isEmpty()) {
            final ResourceBundle bundle = record.getResourceBundle();
            if (null != bundle && bundle.containsKey(message)) {
                if (!bundle.getString(message)
                        .isEmpty()) {
                    return message;
                }
            }
        }
        return null;
    }

    /**
     * @param record
     * @return
     */
    static Throwable getThrowable(final LogRecord record) {
        return record.getThrown();
    }

    /**
     * @param loggerName
     *            Name of logger to get the ResourceBundle of.
     * @return The ResourceBundle for the logger name given.
     */
    private synchronized ResourceBundle getResourceBundle(final String loggerName) {
        if (loggerName == null) {
            return null;
        }

        ResourceBundle bundle = (ResourceBundle) loggerResourceBundleTable.get(loggerName);

        /*
         *  logManager.getLogger should not be relied upon.
         *  To deal with this check if bundle is null and logger is not.
         *  Put a new logger and bundle in the resource bundle table if so.
         */
        final Logger logger = logManager.getLogger(loggerName);
        if (null == bundle && null != logger) {
            bundle = logger.getResourceBundle();
            loggerResourceBundleTable.put(loggerName,
                    bundle);
        }

        return bundle;
    }

    /**
     * @return The date format for the record.
     */
    public String getRecordDateFormat() {
        return recordDateFormat;
    }

    /**
     * @param recordDateFormat
     *            The date format to set for records.
     */
    public void setRecordDateFormat(final String recordDateFormat) {
        this.recordDateFormat = recordDateFormat;
    }

}