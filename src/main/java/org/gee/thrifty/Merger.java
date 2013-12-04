package org.gee.thrifty;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gee.thrifty.datatype.ObjectElement;
import org.gee.thrifty.exception.InvalidRuntimeArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Merger {
   
   private Logger logger = LoggerFactory.getLogger(getClass());
   
   private int fileMergeCount = 0;
   
   private File inputDirectory;;
   private File inputFile;
   private File outputFile;
   private Map<NamespaceScope, String> namespaceMap;
   
   /**
    * <p>
    * Creates an instance of this class.
    * </p>
    */
   public Merger() {
      namespaceMap = new TreeMap<NamespaceScope, String>();
   }
   
   public static void main(String[] args) throws Exception {
      
      Merger m = new Merger();
      try {
         m.parseArguments(args);
         m.merge();
      } catch (InvalidRuntimeArgumentException e) {
         m.printHelp();
      }
   }
   
   public void merge() throws IOException {
      
      validate();
      
      ObjectElement element = null;
      try {
         if (this.inputDirectory != null) {
            File[] files = this.inputDirectory.listFiles();
            if (files != null) {
               for (File jsonFile : files) {
                  logger.info("Processing file " + jsonFile.getAbsolutePath());
                  element = this.merge(new FileInputStream(jsonFile), element);
                  fileMergeCount++;
               }
            }
         } else if (this.inputFile != null) {
            logger.info("Processing file " + this.inputFile.getAbsolutePath());
            element = this.merge(new FileInputStream(this.inputFile));
            fileMergeCount++;
         }
      } catch (Exception e) {
         logger.error("An error occurred during processing. " + fileMergeCount + " files were processed prior to the error.", e);
      }
      
      this.write(element);
   }
   
   /**
    * <p>
    * Adds a namespace to the Thrift definition file. This namespace, along
    * with other added namespaces, will be written to the final output. Both
    * the scope and namespace are required. If either is missing then the
    * namespace will not be added.
    * </p>
    * 
    * @param scope The language, or scope, to which the namespace name is
    *       applied. Required.
    * @param namespace The name of the namespace. Required.
    * @return true if the namespace is added and false if not. If either the
    *       scope or the namespace is not provided then the namespace will not
    *       be added.
    */
   public boolean addNamespace(NamespaceScope scope, String namespace) {
      
      if (scope == null || namespace == null || namespace.trim().isEmpty()) {
         logger.warn("A namespace was not added because either the scope or the namespace name was not provided. The values given were: scope = '" + scope + "', namespace = '" + namespace + "'.");
         return false;
      }
      this.namespaceMap.put(scope, namespace);
      return true;
   }
   
   /**
    * <p>
    * Writes the Thrift definition that was created based on the set of JSON
    * files used during the merge process. The Thrift definition is written to
    * the file provided at {@link #outputFile} or to the console if no file
    * was given. 
    * </p>
    * 
    * @param element The element that is the composite of the JSON files given
    *       for the merge.
    * @throws IOException If an error occurs while writing the Thrift
    *       definition to the file.
    */
   private void write(ObjectElement element) throws IOException {
      
      if (element == null) {
         logger.error("No thrift element value was derived from the merge execution. Check that the input files exist and contain data.");
         return;
      }
      
      logger.info(fileMergeCount + " files were merged.");
      OutputStream outputStream = isWriteToFile() ?  new FileOutputStream(this.outputFile) : new ByteArrayOutputStream();
      writeNamespace(outputStream);
      element.write(outputStream);
      
      if (this.isWriteToFile()) {
         logger.info("Wrote json-to-thrift to file " + this.outputFile.getAbsolutePath() + ".");
         outputStream.close();
      } else {
         logger.info("json-to-thrift:\n" + outputStream.toString());
      }
   }
   
   /**
    * <p>
    * Writes the Thrift namespaces to the output stream. 
    * </p>
    */
   private void writeNamespace(OutputStream outputStream) throws IOException {
      
      if (this.namespaceMap == null || this.namespaceMap.isEmpty()) return;
      
      if (this.namespaceMap.containsKey(NamespaceScope.ALL)) {
         outputStream.write(writeNamespace(NamespaceScope.ALL, this.namespaceMap.get(NamespaceScope.ALL)));
      } else {
         for (NamespaceScope nsScope : this.namespaceMap.keySet()) {
            outputStream.write(writeNamespace(nsScope, this.namespaceMap.get(nsScope)));
         }
      }
   }
   
   /**
    * <p>
    * Creates a Thrift namespace statement based on the namespace scope and the
    * name of the namespace. Returns the string as an array of bytes.
    * </p>
    * 
    * @param nsScope The namespace scope. Required.
    * @param namespace The name of the namespace. Reqired.
    * @return A Thrift IDL statement that defines a namespace.
    */
   private byte[] writeNamespace(NamespaceScope nsScope, String namespace) {
      return new StringBuffer("namespace ")
         .append(nsScope.getCode())
         .append(" ")
         .append(this.namespaceMap.get(nsScope))
         .append(System.getProperty("line.separator"))
         .toString()
         .getBytes();
   }
   
   /**
    * <p>
    * Answers true if the Thrift struct definitions are written to a file and
    * false if written to the console.
    * </p>
    */
   private boolean isWriteToFile() {
      return this.outputFile != null;
   }
   
   /**
    * <p>
    * Confirms that the required parameters for processing the merge are
    * available. If they are not then an exception is thrown.
    * </p>
    * 
    * @throws InvalidRuntimeArgumentException If required parameters are not
    *       available.
    */
   private void validate() throws InvalidRuntimeArgumentException {
      
      if (this.inputDirectory == null && this.inputFile == null) {
         throw new InvalidRuntimeArgumentException("Either the inputDirectory or the inputFile must be specified prior to execution.");
      }
      
      if (this.inputDirectory != null && !this.inputDirectory.isDirectory()) {
         throw new InvalidRuntimeArgumentException("The inputDirectory '" + this.inputDirectory + "' is not a directory.");
      }
      
      if (this.inputDirectory == null && this.inputFile != null && !this.inputFile.isFile()) {
         throw new InvalidRuntimeArgumentException("The inputFile '" + this.inputFile + "' is not a file.");
      }
   }
   
   ObjectElement merge(InputStream stream, ObjectElement element) throws IOException {
      
      ObjectElement parsedElement = this.merge(stream);
      if (element == null) {
         element = parsedElement;
      }
      return (ObjectElement)element.merge(parsedElement);
   }
   
   ObjectElement merge(InputStream stream) throws IOException  {
      
      String contents = toString(stream);
      return merge(contents);
   }

   ObjectElement merge(String jsonStringGroup) throws IOException {
      
      ObjectElement element = null;
      BufferedReader br = new BufferedReader(new StringReader(jsonStringGroup));
      String json = br.readLine();
      while (json != null) {
         if (!json.isEmpty()) {
            logger.debug("Deserializing json: " + json);
            ObjectElement tempElement = parse(json);
            if (element == null) {
               element = tempElement;
            } else {
               logger.debug("Merging " + tempElement + " into " + element);
               element.merge(tempElement);
            }
         }
         json = br.readLine();
      }
      return element;
   }
   
   /**
    * <p>
    * Given a json string, parses it and converts it into an ObjectElement
    * instance.
    * </p>
    * 
    * @param json The json string to parse. Required.
    * @return The json string represented as an ObjectElement.
    */
   private ObjectElement parse(String json) {
      return new Converter().parse(json);
   }
   
   /**
    * <p>
    * Reads the given input stream's contents and returns it as a string.
    * </p>
    * 
    * @param inputStream The input stream to read. Required.
    * @return The input stream's contents as a string.
    * @throws IOException If an error occurs while accessing, reading or
    *       closing the file.
    */
   private String toString(InputStream inputStream) throws IOException {
      
      StringBuffer stringBuffer = new StringBuffer();
      InputStreamReader reader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));

      char[] charBuffer = new char[1024];
      int charsRead = reader.read(charBuffer);
      while (charsRead > 0) {
         stringBuffer.append(charBuffer, 0, charsRead);
         charsRead = reader.read(charBuffer);
      }
      reader.close();
      
      return stringBuffer.toString();
   }
   
   /**
    * <p>
    * Parses the command line for the arguments needed to run this program.
    * </p>
    * 
    * @param args The arguments from the command line.
    */
   private void parseArguments(String[] args)  {
      
      if (args == null) {
         return;
      }
      
      Pattern pattern = Pattern.compile("-(inputDirectory|inputFile|outputFile|nsAll|nsJava|nsCpp|nsPython|nsPerl|nsRuby|nsCocoa|nsCsharp)=\"?(.*)\"?");
      for (String argument : args) {
         
         Matcher matcher = pattern.matcher(argument);
         if (matcher.matches()) {

            String argName = matcher.group(1);
            String argValue = matcher.group(2);
            if (argName.equals("inputDirectory")) {
               this.inputDirectory = new File(argValue);
            } else if (argName.equals("inputFile")) {
               this.inputFile = new File(argValue);
            } else if (argName.equals("outputFile")) {
               this.outputFile = new File(argValue);
            } else if (argName.equals("nsAll")) {
               this.addNamespace(NamespaceScope.ALL, argValue);
            } else if (argName.equals("nsJava")) {
               this.addNamespace(NamespaceScope.JAVA, argValue);
            } else if (argName.equals("nsCpp")) {
               this.addNamespace(NamespaceScope.CPP, argValue);
            } else if (argName.equals("nsPython")) {
               this.addNamespace(NamespaceScope.PYTHON, argValue);
            } else if (argName.equals("nsPerl")) {
               this.addNamespace(NamespaceScope.PERL, argValue);
            } else if (argName.equals("nsRuby")) {
               this.addNamespace(NamespaceScope.RUBY, argValue);
            } else if (argName.equals("nsCocoa")) {
               this.addNamespace(NamespaceScope.COCOA, argValue);
            } else if (argName.equals("nsCsharp")) {
               this.addNamespace(NamespaceScope.CSHARP, argValue);
            }
            logger.debug("Runtime argument established | " + argName + " = " + argValue);
         }
      }
   }
   
   /**
    * <p>
    * Prints to System.out the runtime arguments that may be used to execute
    * this utility.
    * </p>
    */
   private void printHelp() {
      // TODO: fill in.
      System.out.println("help goes here. :D");
   }
   
}
