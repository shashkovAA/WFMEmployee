package ru.croc.dtk.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Main {

    private  Logger log;
    //private String headerLine;
    private String lineSeparator = "\r\n";
    private String inputFileFieldsDelimiter;
    private  Properties property;
    private long worktime = 0;
    private String inputFileName, outputFileName, suffix,findField,changeField;
    private int findFieldIndex,changeFieldIndex;
    private  String[] inputFieldsArray;
    private boolean exportOnlyChangedRecord;

    private static final Logger logger = LogManager.getLogger("Run");

    public static void main(String[] args) throws FileNotFoundException {

        Main main = new Main();
        main.getProgramAttributes(args);
        main.start();
    }



    private void getProgramAttributes(String[] args) {

        logger.info("Start script ...");
        worktime = System.currentTimeMillis();

        if (args.length > 0) {
            File configFile = new File(args[0]);
            if (configFile.exists())
                loadProperty(args[0]);
            else {
                logger.error("Property file [" + args[0] + "] not found. Exit program.");
                System.exit(0);
            }
        } else {
            logger.error("Missing first mandatory program attributes: property file. Exit program.");
            System.exit(0);
        }

    }

    private void start() throws FileNotFoundException {

        inputFileName = getProperty("input_file");
        outputFileName = getProperty("output_file");
        suffix = getProperty("suffix");
        inputFileFieldsDelimiter = getProperty("delimiter");
        findField = getProperty("find_field");
        changeField = getProperty("change_field");

        exportOnlyChangedRecord = Boolean.valueOf(getProperty("export_only_changed"));

        StringBuffer sb = new StringBuffer();

        //inputFileFieldsDelimiter = "\\|";
        //outputFileName = "convertedEmployees.txt";

        File outfile = new File(outputFileName);
        if (outfile.exists())
            outfile.delete();

        String employeeId = "";
        String desktopUserName = "";
        boolean existFlag = false;
        int delta;

        String readlineFromFile = null;
        String temp = null;
        int recordLen = 0;
        int count = 0;
        int exportedCount = 0;

        //try (BufferedReader buffer = new BufferedReader(new FileReader(inputFileName));
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(new FileInputStream(inputFileName),"UTF-8"));
             //BufferedWriter bufferOut = new BufferedWriter(new FileWriter(outfile,true));) {
             BufferedWriter bufferOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outfile,true),"UTF-8"));) {

            while ((readlineFromFile = buffer.readLine()) != null) {

                if (!readlineFromFile.isEmpty()) {

                    inputFieldsArray = readlineFromFile.split(inputFileFieldsDelimiter);

                    if (count == 0) {
                        recordLen = inputFieldsArray.length;
                        logger.info("Count fields in record : " + recordLen);

                        //Get indexes for needed fields
                        for (int i = 0; i < inputFieldsArray.length; i++) {

                            if (inputFieldsArray[i].trim().equalsIgnoreCase(findField.trim())) {
                                findFieldIndex = i;
                                logger.info("Index for field [" + findField + "] is [" + i + "]");
                            }

                            if (inputFieldsArray[i].trim().equalsIgnoreCase(changeField.trim())) {
                                changeFieldIndex = i;
                                logger.info("Index for field [" + changeField + "] is [" + i + "]");
                            }
                        }

                        logger.info("Create record for header :" + readlineFromFile);
                        bufferOut.append(readlineFromFile + lineSeparator);
                    }
                    else
                    {
                        employeeId = inputFieldsArray[findFieldIndex]; // field 'EmployeeId'
                        desktopUserName = inputFieldsArray[changeFieldIndex];

                        if (!employeeId.isEmpty()) {

                            if (!desktopUserName.equals(employeeId + suffix)) {
                                inputFieldsArray[changeFieldIndex] = employeeId + suffix; // field 'Desktop Messaging User Name'
                            }
                            else
                                existFlag = true;
                        }

                        delta = recordLen;


                        for (int i = 0; i < inputFieldsArray.length; i++) {

                            sb.append(inputFieldsArray[i] + "|");
                            //System.out.print(inputFieldsArray[i] + "|");
                            delta--;
                        }

                        //Добиваем запись если она была изменена
                        for (int i = 0; i < delta; i++) {
                            sb.append( "|");
                        }


                        if (!exportOnlyChangedRecord) {
                            logger.info("Add record for export file :" + sb.toString() + lineSeparator);
                            bufferOut.append(sb.toString() + lineSeparator);
                            exportedCount++;
                        } else

                            if (!existFlag) {
                                logger.info("Add record for export file :" + sb.toString() + lineSeparator);
                                bufferOut.append(sb.toString() + lineSeparator);
                                exportedCount++;
                            }
                            else
                                logger.info("Record not added to export file :" + sb.toString() + lineSeparator);

                    }

                    count ++;

                    //Очищаем буфер для новой записи
                    sb.delete(0,sb.length());

                    existFlag = false;
                }


            }
        } catch (FileNotFoundException except) {
            logger.error(except.getMessage());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException except) {
            logger.error(except.getMessage());
        }

        logger.info("Added records to export file :" + exportedCount);
        logger.info("Total processed " + count + " records at " + (System.currentTimeMillis() - worktime) + " ms.");
        logger.info("Stop script ...");
    }

    private void loadProperty(String filename) {
        property = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream(filename);
            property.load(input);
            logger.info("Properties file [" + filename +"] loaded succefully...");

        } catch (IOException except) {
            logger.error(except.getMessage());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }
        }
    }
    private String getProperty(String propName) {
        String prop = null;
        prop = property.getProperty(propName);
        if (prop == null) {
            logger.error("Property name [" + propName + "] is invalid or missing in configuration file. Check this and try again ..." );
            System.exit(0);
        }
        else
            logger.info("Property name [" + propName + "] is loaded successfully. Value [" + prop + "]");
        return prop;
    }

}
