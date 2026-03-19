package demo.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

/**
 * Activities for persisting and finalising extracted text.
 */
@ActivityInterface
public interface FileStorageActivities {

    /**
     * Create the temporary working directory structure for a file.
     * <pre>
     *   {tmpBase}/{baseName}/                  ← main tmp dir
     *   {tmpBase}/{baseName}/{baseName}_images/ ← image sub-dir (PDFs only)
     * </pre>
     *
     * @param tmpBase root tmp directory (e.g. /tmp/file-processor)
     * @param baseName the file name without extension
     * @return absolute path to the main tmp directory
     */
    @ActivityMethod
    String createTmpDirectories(String tmpBase, String baseName);

    /**
     * Merge multiple text files into a single combined output file. Used to
     * combine PDF body text + OCR text from extracted images.
     *
     * @param textFilePaths ordered list of .txt files to concatenate
     * @param outputPath destination combined .txt file
     * @return the output path that was written
     */
    @ActivityMethod
    String mergeTextFiles(List<String> textFilePaths, String outputPath);

    /**
     * Copy the final extracted text file to the caller-specified output
     * location and return the absolute path of the copy.
     *
     * @param sourcePath path to the final .txt in the tmp dir
     * @param outputLocation the user-requested output directory
     * @param outputFileName the output file name (e.g. "invoice_extracted.txt")
     * @return absolute path of the copied file
     */
    @ActivityMethod
    String copyToOutput(String sourcePath, String outputLocation, String outputFileName);
}
