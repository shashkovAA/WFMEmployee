package ru.croc.dtk.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Shashkov Andrey
 */
public class Converter {

    private String lineSeparator;
    private String inputFileName,outputFileName;
    private String readlineFromFile = "";
    private long count = 0;
    private long traceCountRows1 = 0;
    private long traceCountRows2 = 0;
    private long worktime = 0;
    private  Properties property;
    private  String[] inputFieldsArray;
    private  String inputFileFieldsDelimiter;
    private  String[] outputFieldsIndexArray;
    private  boolean  insertCustoms = false;
    private  int numFieldsInConfigFile;
    private  SimpleDateFormat dateTimeFormat;

    private  ArrayList<Integer> contactIdList = new ArrayList<Integer>();
    private  ArrayList<String> timeStampList = new ArrayList<String>();

    private  HashSet<Integer> duplicatedIdHashSet = new HashSet<Integer>();
    private  HashMap<Integer,String> map = new HashMap<Integer,String>();

    private  String tempOUTputFilename;
    private  final String TEMP_NON_UNIQUE_ID_FILENAME = "tempDuplicateOut.csv";
    private  Logger log;

/*    public ConverterCsv() {

        Log4jXmlFile.checkExistLog4jXmlFile(getClass().getName());
        getLogger().info("");
        getLogger().info("Start script ConverterCsv...");
    }*/

    public static void main(String[] args) throws IOException {

        Converter myConverter = new Converter();
        myConverter.getProgramAttributes(args);
        myConverter.start();
        System.out.println("Details of the execution of the script you can see in the log file: logs/ConverterCsvLog.txt");
    }

    private void getProgramAttributes(String[] args) {

        if (args.length >= 1) {
            File configFile = new File(args[0]);
            if (configFile.exists())
                loadProperty(args[0]);
            else {
                getLogger().error("Employee file [" + args[0] + "] not found. Exit program.");
                System.exit(0);
            }
        } else {
            getLogger().error("Missing first mandatory program attributes: employee file name. Exit program.");
            System.exit(0);
        }

        if (args.length >= 2)
            inputFileName = args[1];
        else {
            getLogger().warn("Missing optional program attributes: <input_file_name>.");
            getLogger().warn("This value will be used from the configuration file.");

            if (!getProperty("inputfile.name").isEmpty())
                inputFileName = getProperty("inputfile.name");
            else {
                getLogger().error("Property [inputfile.name] has NOT required value in configuration file. Exit program.");
                System.exit(0);
            }
        }

        getLogger().info("Input file name:["+ inputFileName +"]");

        if (args.length >= 3)
            outputFileName = args[2];
        else {
            getLogger().warn("Missing optional program attributes: <output_file_name>.");
            getLogger().warn("This value will be used from the configuration file.");
            if (!getProperty("outputfile.name").isEmpty())
                outputFileName = getProperty("outputfile.name");
            else {
                getLogger().error("Property [outputfile.name] has NOT required value in configuration file. Exit program.");
                System.exit(0);
            }
        }

        setLineSeparator();

        getLogger().info("Output file name:["+ outputFileName +"]");
    }

    public void start() {

        worktime = System.currentTimeMillis();

        File inputFile = new File(inputFileName);
        if (!inputFile.exists()) {
            getLogger().error("Input file [" + inputFileName + "]  not found. Exit programm.");
            System.exit(0);
        }

        File outputFile = new File(outputFileName);
        if (!outputFile.exists())
            createNewFile(outputFile);
        else {
            getLogger().info("File [" +outputFileName+ "] is alredy exist in current directory. It will recreated ");
            if (!getProperty("outputfile.save.old").isEmpty() && getProperty("outputfile.save.old").equals("true"))
                renameOldConvertedFileAndCreateNewFileOne(outputFile);
            else {
                outputFile.delete();
                createNewFile(outputFile);
            }
        }

        if (!getProperty("outputfile.fields.dateformat").isEmpty())
            dateTimeFormat = new SimpleDateFormat(getProperty("outputfile.fields.dateformat"));
        else
            dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

        if (!getProperty("outputfile.remove.duplicate").isEmpty())
            if (getProperty("outputfile.remove.duplicate").equals("true"))
                createOutputFileWithRemoveDuplicate();
            else {
                createOutputFile(inputFileName, outputFileName);
                getLogger().info("Processed " + count + " lines at " + (System.currentTimeMillis() - worktime) + " ms.");
            }
        else {
            getLogger().warn("Property [outputfile.remove.duplicate] has NOT value in configuration file.");
            getLogger().warn("This value will be used as 'false'.");
            createOutputFile(inputFileName, outputFileName);
            getLogger().info("Processed " + count + " lines at " + (System.currentTimeMillis() - worktime) + " ms.");
        }
        getLogger().info("Stop script ConverterCsv...");
    }

    private void loadProperty(String filename) {
        property = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream(filename);
            property.load(input);
            getLogger().info("Properties file [" + filename +"] loaded succefully...");

        } catch (IOException except) {
            getLogger().error(except.getMessage());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    getLogger().error(e.getMessage());
                }
            }
        }
    }

    private void renameOldConvertedFileAndCreateNewFileOne(File mFile) {

        String fileExtension = inputFileName.substring(inputFileName.lastIndexOf('.'));

        SimpleDateFormat fileAppenderdateFormat = new SimpleDateFormat("dd_MM_yyyy-HH_mm");

        String oldName = mFile.getName().replaceAll(fileExtension, "");
        String newName = oldName + "-" + fileAppenderdateFormat.format(new Date());

        getLogger().info("It was renamed to [" + newName + fileExtension + "] and created new one.");

        File newCdrFile = new File(newName + fileExtension);
        mFile.renameTo(newCdrFile);

        File oldCdrFile = new File(oldName + fileExtension);
        createNewFile(oldCdrFile);
    }

    private void createNewFile(File file) {
        try {
            file.createNewFile();
        } catch (IOException except) {
            getLogger().error(except.getMessage());
        }
    }

    private void createOutputFile(String inputFileName, String convertFileName) {

        String idString = "";
        String ouputString = "";
        boolean insertId = false;
        boolean deleteFirstLine = false;

        if (getProperty("outputfile.insertid").equals("true"))
            insertId = true;

        if (getProperty("outputfile.headerstring.delete").equals("true"))
            deleteFirstLine = true;

        if (getProperty("outputfile.fields.insert.customs").equals("true"))
            insertCustoms = true;

        String oFFD = getProperty("outputfile.fieldsdelimiter");
        outputFieldsIndexArray = getProperty("outputfile.fields.sequence").split(",");
        numFieldsInConfigFile = outputFieldsIndexArray.length;

        inputFileFieldsDelimiter = getProperty("inputfile.fieldsdelimiter");
        String headerString = getProperty("outputfile.headerstring.value");

        try (BufferedReader buffer = new BufferedReader(new FileReader(inputFileName));
             BufferedWriter bufferOut = new BufferedWriter(new FileWriter(convertFileName)); ) {

            if (getProperty("outputfile.headerstring.insert").equals("true"))
                bufferOut.write(headerString + lineSeparator);

            while ((readlineFromFile = buffer.readLine()) != null) {
                if (!readlineFromFile.isEmpty()) {
                    inputFieldsArray = readlineFromFile.split(inputFileFieldsDelimiter);

                    count++;
                    if (insertId)
                        idString = String.valueOf(count) + oFFD;

                    ouputString = idString;

                    for (int i = 0; i < numFieldsInConfigFile - 1; i++)
                        ouputString+= checkFieldIsExistAndCustom(Integer.valueOf(outputFieldsIndexArray[i]))+ oFFD;

                    ouputString+= checkFieldIsExistAndCustom(Integer.valueOf(outputFieldsIndexArray[numFieldsInConfigFile-1])) +
                            lineSeparator;

                    if (!(deleteFirstLine && count == 1))
                        bufferOut.write(ouputString);
                }
            }
        } catch (FileNotFoundException except) {
            getLogger().error(except.getMessage());
        } catch (IOException except) {
            getLogger().error(except.getMessage());
        }

    }
    private String checkFieldIsExistAndCustom(int index){
        if (inputFieldsArray.length <= index)
            if (insertCustoms)
                if (getProperty("outputfile.field.insert." + index).isEmpty())
                    return "";
                else
                    return getProperty("outputfile.field.insert." + index);
            else
                return "";
        else if (insertCustoms)
            if (getProperty("outputfile.field.insert." + index).isEmpty())
                return inputFieldsArray[index];
            else
                return getProperty("outputfile.field.insert." + index);
        else
            return inputFieldsArray[index];

    }

    private void createOutputFileWithRemoveDuplicate(){

        tempOUTputFilename = inputFileName.substring(0,inputFileName.lastIndexOf('.')) + "_out.csv";
        getHashSetOfDuplicatedContactId();
        splitIncomingFile();
        getMapForPairDuplicatedIDLastAttemptTimeStamp();
        mergeTempFilesToOneWithNoDuplicates();
        createOutputFile(tempOUTputFilename, outputFileName);
        getLogger().info("Exported " + (traceCountRows1 + traceCountRows2) + " rows  at "+ (System.currentTimeMillis() - worktime) + " ms.");
    }


    private void getHashSetOfDuplicatedContactId () {

        int ContactID;
        HashSet<Integer> tempUniqueIdSet = new HashSet<Integer>();

        inputFileFieldsDelimiter = getProperty("inputfile.fieldsdelimiter");

        try (BufferedReader buffer = new BufferedReader(new FileReader(inputFileName));) {

            while ((readlineFromFile = buffer.readLine()) != null) {
                if (!readlineFromFile.isEmpty()) {
                    ContactID = 0;
                    inputFieldsArray = readlineFromFile.split(inputFileFieldsDelimiter);

                    if (isIdFieldContainsOnlyDigits(inputFieldsArray[getContactIdIndex()]))
                        ContactID = Integer.valueOf(inputFieldsArray[getContactIdIndex()]);

                    if (ContactID!=0 && !tempUniqueIdSet.contains(ContactID)) {
                        tempUniqueIdSet.add(ContactID);
                        //getLogger().debug(" Unique ID : " + ContactID);
                    } else if (ContactID!=0 && !duplicatedIdHashSet.contains(ContactID)) {
                        duplicatedIdHashSet.add(ContactID);
                        traceCountRows2++;
                        getLogger().debug("Duplicated ID :" + ContactID);
                    }
                }
            }

            getLogger().debug("Found "+ traceCountRows2 + " contacts with multiple call attempt." );
        } catch (FileNotFoundException except) {
            System.out.println(except.getMessage());
        } catch (IOException except) {
            System.out.println(except.getMessage());
        }
    }

    private void splitIncomingFile() {

        int ContactID;

        try (BufferedReader buffer = new BufferedReader(new FileReader(inputFileName));
             BufferedWriter bufferOut = new BufferedWriter(new FileWriter(tempOUTputFilename));
             BufferedWriter bufferOut2 = new BufferedWriter(new FileWriter(TEMP_NON_UNIQUE_ID_FILENAME));) {

            while ((readlineFromFile = buffer.readLine()) != null) {
                if (!readlineFromFile.isEmpty()) {
                    inputFieldsArray = readlineFromFile.split(inputFileFieldsDelimiter);
                    ContactID = 0;

                    if (isIdFieldContainsOnlyDigits(inputFieldsArray[getContactIdIndex()]))
                        ContactID = Integer.valueOf(inputFieldsArray[getContactIdIndex()]);

                    if (ContactID == 0)
                        bufferOut.write(readlineFromFile + lineSeparator);
                    else if (duplicatedIdHashSet.contains(ContactID)) {
                        contactIdList.add(ContactID);
                        timeStampList.add(inputFieldsArray[getContactAttemptTime()]);
                        bufferOut2.write(readlineFromFile + lineSeparator);
                    } else {
                        bufferOut.write(readlineFromFile + lineSeparator);
                        traceCountRows1++;
                    }
                }
            }
        } catch (FileNotFoundException except) {
            getLogger().error(except.getMessage());
        } catch (IOException except) {
            getLogger().error(except.getMessage());
        }

    }

    private void getMapForPairDuplicatedIDLastAttemptTimeStamp() {
        Date contactAttemptTimeStamp = null;
        int i;

        for(int duplicatedId : duplicatedIdHashSet){
            contactAttemptTimeStamp = getDateFromString("01.01.1970 00:00:00");

            for (i = 0; i<contactIdList.size(); i++)
                if (duplicatedId == contactIdList.get(i))
                    if ((contactAttemptTimeStamp.compareTo(getDateFromString(timeStampList.get(i))) <= 0)) {
                        contactAttemptTimeStamp = getDateFromString(timeStampList.get(i));
                        //getLogger().debug(contactAttemptTimeStamp.toString());
                    }
            map.put(duplicatedId, dateTimeFormat.format(contactAttemptTimeStamp));
        }
    }

    private void mergeTempFilesToOneWithNoDuplicates() {
        int ContactID;

        File outfile = new File(tempOUTputFilename);

        try (BufferedReader buffer = new BufferedReader(new FileReader(TEMP_NON_UNIQUE_ID_FILENAME));
             BufferedWriter bufferOut = new BufferedWriter(new FileWriter(outfile,true)); ) {

            while ((readlineFromFile = buffer.readLine()) != null) {
                if (!readlineFromFile.isEmpty()) {
                    inputFieldsArray = readlineFromFile.split(inputFileFieldsDelimiter);
                    ContactID = 0;

                    if (isIdFieldContainsOnlyDigits(inputFieldsArray[getContactIdIndex()]))
                        ContactID = Integer.valueOf(inputFieldsArray[getContactIdIndex()]);

                    for (Map.Entry<Integer, String> entry : map.entrySet()) {

                        if (ContactID == entry.getKey() && (inputFieldsArray[getContactAttemptTime()]).equals(entry.getValue())) {
                            bufferOut.append(readlineFromFile + lineSeparator);
                            //getLogger().debug("Append row:" + readlineFromFile);
                        }
                    }
                }
            }
        } catch (FileNotFoundException except) {
            getLogger().error(except.getMessage());
        } catch (IOException except) {
            getLogger().error(except.getMessage());
        }
        File tempNonUniqueIdFileName = new File (TEMP_NON_UNIQUE_ID_FILENAME);
        tempNonUniqueIdFileName.delete();

    }

    private int getContactIdIndex(){
        int index = -1;
        if (!getProperty("outputfile.field.index.id").isEmpty())
            if (isIdFieldContainsOnlyDigits(getProperty("outputfile.field.index.id")))
                index = Integer.valueOf(getProperty("outputfile.field.index.id"));
            else {
                getLogger().error("Property [outputfile.field.index.id] has BAD value in configuration file. Check this and try again.");
                System.exit(0);

            }
        else {
            getLogger().error("Property [outputfile.field.index.id] has NOT required value in configuration file. Check this and try again.");
            System.exit(0);
        }
        return index;
    }

    private int getContactAttemptTime(){

        int index = -1;
        if (!getProperty("outputfile.field.index.contact_attempt_time").isEmpty())
            if (isIdFieldContainsOnlyDigits(getProperty("outputfile.field.index.contact_attempt_time")))
                index = Integer.valueOf(getProperty("outputfile.field.index.contact_attempt_time"));
            else {
                getLogger().error("Property [outputfile.field.index.contact_attempt_time] has BAD value in configuration file. Check this and try again.");
                System.exit(0);

            }
        else {
            getLogger().error("Property [outputfile.field.index.contact_attempt_time] has NOT required value in configuration file. Check this and try again.");
            System.exit(0);
        }
        return index;
    }

    private boolean isIdFieldContainsOnlyDigits (String id) {
        String regex = "\\d+";
        if (id.matches(regex))
            return true;
        else
            return false;
    }

    private Date getDateFromString (String dateTimeString) {

        Date dateTime = null;
        try {
            dateTime = dateTimeFormat.parse(dateTimeString);
        } catch (ParseException except) {
            getLogger().error(except.getMessage());
        }
        return dateTime;
    }

    private String getProperty(String propName) {
        String prop = null;
        prop = property.getProperty(propName);
        if (prop == null) {
            getLogger().error("Property name [" + propName + "] is invalid or missing in configuration file. Check this and try again ..." );
            System.exit(0);
        }
        return prop;
    }

    private void setLineSeparator(){

        if (!getProperty("outputfile.line.separator").isEmpty()
                || getProperty("outputfile.line.separator").equals("\n")
                || getProperty("outputfile.line.separator").equals("\r\n")) {
            lineSeparator = getProperty("outputfile.line.separator");
            getLogger().debug("Property [outputfile.line.separator] has value in configuration file ["+ lineSeparator +"]");

        }
        else {
            lineSeparator = System.getProperty("line.separator");
            getLogger().error("Property [outputfile.line.separator] has NOT required value or missing. Use system value ["+ lineSeparator +"]");
        }



    }

    private Logger getLogger() {
        if ( log == null )
            log = LogManager.getLogger("Run");
        return log;
    }










}
