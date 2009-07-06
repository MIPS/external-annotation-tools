package annotations.io.classfile;

import checkers.nullness.quals.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;

import annotations.Annotation;
import annotations.AnnotationFactory;
import annotations.el.AScene;
import annotations.io.IndexFileWriter;
/**
 * A <code> ClassFileReader </code> provides methods for reading in annotations
 *  from a class file into an {@link annotations.el.AScene}.
 */
public class ClassFileReader {

  public static final String INDEX_UTILS_VERSION =
    "Annotation file utilties: extract-annotations v2.2";

  /**
   * Main method meant to a a convenient way to read annotations from a class
   * file and write them to an index file.  For programmatic access to this
   * tool, one should probably use the read() methods instead.
   *
   * Usage: java annotations.io.ClassFileReader
   *  <options>
   *  <space-separated list of classes to analyze>
   *
   * <options> include:
   *   -h, --help   print usage information and exit
   *   --version    print version information and exit
   *
   * @param args options and classes to analyze;
   * @throws IOException if a class file cannot be found
   */
  public static void main(String[] args) throws IOException {
    boolean printUsage = false;
    for (String arg : args) {
      arg = arg.trim();
      if (arg.equals("-h") || arg.equals("-help") || arg.equals("--help")) {
        printUsage = true;
      }
    }

    for (String arg: args) {
      arg = arg.trim();
      if (arg.equals("-version") || arg.equals("--version")) {
        System.out.println(INDEX_UTILS_VERSION);
        if (!printUsage) {
          return;
        }
      }
    }

    if (args.length == 0 || printUsage) {
      System.out.println("usage: extract-annotations");
      System.out.println("            <options> ");
      System.out.println("            <space-separated list of classes to analyze>");
      System.out.println("");
      System.out.println("       <options> include: ");
      System.out.println("         -h, --help       print usage information and exit");
      System.out.println("         --version        print version information and exit");
      System.out.println("");
      System.out.println("For each class a.b.C given, extracts the annotations from that");
      System.out.println(" class and prints them in index-file format to a.b.C.jaif");
      System.out.println("Note that the class a.b.C must be located in your classpath");
      return;
    }

    // check args for well-formed names
    for (String arg : args) {
      arg = arg.trim();
      if (arg.contains("/") || arg.endsWith(".class")) {
        System.out.print("Error: " + arg +
            " does not appear to be a fully qualified class name");
        System.out.print(" please use names such as java.lang.Object");
        System.out.print(" instead of Ljava/lang/Object or");
        System.out.println(" java.lang.Object.class");
        return;
      }
    }

    for (String origName : args) {
      if (origName.startsWith("-")) {
        continue; // ignore options
      }
      origName = origName.trim();
      System.out.println("reading: " + origName);
      String className = origName;
//      if (!className.endsWith(".class")) {
//        className = className + ".class";
//      }

      AScene scene =
        new AScene();
      try {
        readFromClass(scene, className);
        String outputFile = origName + ".jaif";
        System.out.println("printing results to : " + outputFile);
        IndexFileWriter.write(scene, outputFile);
      } catch(IOException e) {
        System.out.println("There was an error in reading class: " + origName);
        System.out.println(
            "Did you ensure that this class is on your classpath?");
        return;
      } catch(Exception e) {
        System.out.println("Uknown error trying to extract annotations from: " +
            origName);
        System.out.println("Please send a copy the following output trace, " +
            "along with instructions on how to reproduce this error, " +
            "to the Javari mailing list at javari@csail.mit.edu");
        System.out.println(e.getMessage());
        e.printStackTrace();
        return;
      }
    }
  }

  /**
   * Reads the annotations from the class file <code> fileName </code>
   * and inserts them into <code> scene </code>.
   * <code> fileName </code> should be a file name that can be resolved from
   * the current working directory, which means it should end in ".class"
   * for standard Java class files.
   *
   * @param scene the scene into which the annotations should be inserted
   * @param fileName the file name of the class the annotations should be
   * read from
   * @throws IOException if there is a problem reading from
   * <code> fileName </code>
   */
  public static void read(AScene scene, String fileName)
  throws IOException {
    read(scene, new FileInputStream(fileName));
  }

  /**
   * Reads the annotations from the class <code> className </code>,
   * assumed to be in your classpath,
   * and inserts them into <code> scene </code>.
   *
   * @param scene the scene into which the annotations should be inserted
   * @param className the name of the class to read in
   * @throws IOException if there is a problem reading <code> className </code>
   */
  public static void readFromClass(AScene scene, String className)
  throws IOException {
    read(scene, new ClassReader(className));
  }

  /**
   * Reads the annotations from the class file <code> fileName </code>
   * and inserts them into <code> scene </code>.
   *
   * @param scene the scene into which the annotations should be inserted
   * @param in an input stream containing the class that the annotations
   * should be read from
   * @throws IOException if there is a problem reading from <code> in </code>
   */
  public static void read(AScene scene, InputStream in)
  throws IOException {
    read(scene, new ClassReader(in));
  }

  public static void read(AScene scene, ClassReader cr)
  {
      ClassAnnotationSceneReader ca = new ClassAnnotationSceneReader(scene);
      cr.accept(ca, true);
  }

}
