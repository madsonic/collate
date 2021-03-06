package main.java.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import main.java.data.Author;
import main.java.data.CodeSnippet;
import main.java.data.SourceFile;

/**
 * This class handles commands created from a user's input.
 * 
 * @author Sebastian Quek
 *
 */
public class Logic {

    private CommandParser commandParser;
    private Storage storage;
    private HashMap<String, Author> authors;
    private String rootDirectory;
    private Author targetAuthor;

    private static final String AUTHOR_TAG = "@@author";

    private static final String MARKDOWN_TITLE = "# %s";
    private static final String MARKDOWN_H6 = "###### %s";
    private static final String MARKDOWN_CODE_LANGUAGE_START = "``` %s";
    private static final String MARKDOWN_CODE_LANGUAGE_END = "```";

    private static final String STRING_EMPTY = "";
    private static final String STRING_BACKSLASH = "\\";
    private static final char STRING_PERIOD = '.';

    private static final String REGEX_NEITHER_ALPHANUMERIC_NOR_SPACE = "[^ a-zA-Z0-9]+";

    private static final String ERROR_IO_EXCEPTION = "Encountered IOException for %s";
    
    public Logic() {
        commandParser = new CommandParser();
        authors = new HashMap<String, Author>();
    }

    public Command.Type executeCommand(String userInput) {
        Command command = commandParser.parse(userInput);
        switch (command.getCommandType()) {
            case COLLATE :
                handleCollate(command);
                return Command.Type.COLLATE;
            case VIEW :
                handleView(command);
                return Command.Type.VIEW;
            case SUMMARY :
                return Command.Type.SUMMARY;
            case EXIT :
                return Command.Type.EXIT;
            case INVALID :
            default :
                return Command.Type.INVALID;
        }
    }


    // ================================================================
    // "Collate" command methods
    // ================================================================

    private void handleCollate(Command command) {
        resetVariables();

        rootDirectory = command.getDirectory();
        storage = new Storage(rootDirectory);
        boolean willScanCurrentDirOnly = command.willScanCurrentDirOnly();
        ArrayList<String> fileTypes = command.getFileTypes();

        traverseDirectory(new File(rootDirectory),
                          willScanCurrentDirOnly,
                          fileTypes);
        saveCollatedFiles();
    }

    /**
     * Resets variables so that "collate" commands will not show data from
     * previously entered "collate" commands.
     */
    private void resetVariables() {
        authors = new HashMap<String, Author>();
        CodeSnippet.resetTotalLines();
    }

    /**
     * Scans the input folder recursively to create new Author, SourceFile and
     * CodeSnippet objects.
     * 
     * @param folder
     * @param willScanCurrentDirOnly
     * @param fileTypes
     */
    private void traverseDirectory(File folder,
                                   boolean willScanCurrentDirOnly,
                                   ArrayList<String> fileTypes) {
        if (folder.isFile()) {
            scanFile(folder, getFileExtension(folder));
        } else if (folder.isDirectory()) {
            scanFilesInFolder(folder, willScanCurrentDirOnly, fileTypes);
        }
    }

    private void scanFilesInFolder(File folder,
                                   boolean willScanCurrentDirOnly,
                                   ArrayList<String> fileTypes) {
        for (File file : folder.listFiles()) {
            if (!willScanCurrentDirOnly && file.isDirectory()) {
                // If no "only" keyword was given, traverse the directory
                // recursively
                traverseDirectory(file, willScanCurrentDirOnly, fileTypes);
            } else if (file.isFile() &&
                       hasIncludedFileType(fileTypes, getFileExtension(file))) {
                scanFile(file, getFileExtension(file));
            }
        }
    }

    /**
     * An empty fileTypes ArrayList indicates that no "include" keyword was
     * given.
     * 
     * @param fileTypes
     * @param fileExtension
     */
    private boolean hasIncludedFileType(ArrayList<String> fileTypes,
                                        String fileExtension) {
        return fileTypes.isEmpty() || fileTypes.contains(fileExtension);
    }

    private void scanFile(File file, String extension) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            boolean ignoreLine = false;
            Author currentAuthor = null;
            CodeSnippet currentSnippet = null;
            SourceFile currentFile = new SourceFile(generateRelativePath(file.getPath()),
                                                    extension);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(AUTHOR_TAG)) {
                    String authorName = findAuthorNameInLine(line, AUTHOR_TAG);

                    if (authorName.isEmpty()) {
                        // Author tag with no author name
                        ignoreLine = true;
                    } else {
                        ignoreLine = false;
                        currentAuthor = getAuthorByName(authorName);
                        currentSnippet = new CodeSnippet(currentAuthor,
                                                         currentFile);
                    }
                } else {
                    currentFile.addNumLines(1);
                    if (!ignoreLine && currentSnippet != null) {
                        currentSnippet.addLine(line);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(String.format(ERROR_IO_EXCEPTION, file.toString()));
        }
    }

    /**
     * Get previously created Author or create a new Author.
     * 
     * @param authorName
     */
    private Author getAuthorByName(String authorName) {
        Author currentAuthor;
        if (!authors.containsKey(authorName)) {
            currentAuthor = new Author(authorName);
            authors.put(authorName, currentAuthor);
        } else {
            currentAuthor = authors.get(authorName);
        }
        return currentAuthor;
    }

    /**
     * Generates path of files relative to the root directory.
     * 1) If the filepath is the same as the root directory, return the
     * filename.
     * 2) If the input root directory does not end with a backslash, return the
     * filename without the extra starting backslash.
     * 
     * @param filePath
     */
    private String generateRelativePath(String filePath) {
        if (filePath.equals(rootDirectory)) {
            return filePath.substring(filePath.lastIndexOf(STRING_BACKSLASH) + 1);

        } else if (rootDirectory.endsWith(STRING_BACKSLASH)) {
            return filePath.replace(rootDirectory, STRING_EMPTY);

        } else {
            return filePath.replace(rootDirectory + STRING_BACKSLASH,
                                    STRING_EMPTY);
        }
    }

    private String findAuthorNameInLine(String line, String authorTag) {
        try {
            String[] split = line.split(authorTag);

            // Only alphanumeric characters are allowed in the author's name
            return split[1].replaceAll(REGEX_NEITHER_ALPHANUMERIC_NOR_SPACE,
                                       STRING_EMPTY).trim();
        } catch (ArrayIndexOutOfBoundsException e) {
            return STRING_EMPTY;
        }
    }

    private String getFileExtension(File file) {
        int idxLastPeriod = file.getName().lastIndexOf(STRING_PERIOD);
        if (idxLastPeriod != -1) {
            return file.getName().substring(idxLastPeriod + 1);
        }
        return STRING_EMPTY;
    }

    private void saveCollatedFiles() {
        for (Author author : authors.values()) {
            ArrayList<String> collatedLines = new ArrayList<String>();

            collatedLines.add(String.format(MARKDOWN_TITLE, author.getName()));
            addCodeSnippetsOfAuthor(author, collatedLines);

            storage.addCollatedFile(author.getName(), collatedLines);
        }
    }

    private void addCodeSnippetsOfAuthor(Author author,
                                         ArrayList<String> collatedLines) {
        for (CodeSnippet codeSnippet : author.getCodeSnippets()) {
            collatedLines.add(String.format(MARKDOWN_H6,
                                            codeSnippet.getFile()
                                                       .getRelativeFilePath()));
            collatedLines.add(String.format(MARKDOWN_CODE_LANGUAGE_START,
                                            codeSnippet.getFile().getLanguage()));
            collatedLines.add(codeSnippet.toString());
            collatedLines.add(MARKDOWN_CODE_LANGUAGE_END);
        }
    }


    // ================================================================
    // View command methods
    // ================================================================

    private void handleView(Command command) {
        String authorName = command.getAuthorName();
        setTargetAuthorIfAuthorExists(authorName);
    }

    private void setTargetAuthorIfAuthorExists(String inputName) {
        for (Author author : authors.values()) {
            if (author.getName().toLowerCase().equals(inputName.toLowerCase())) {
                targetAuthor = author;
                return;
            }
        }
        targetAuthor = null;
    }


    // ================================================================
    // Methods for GUI/TUI
    // ================================================================

    public Collection<Author> getAuthors() {
        return authors.values();
    }

    public String getTargetAuthorName() {
        if (targetAuthor != null) {
            return targetAuthor.getName();
        }
        return null;
    }

    /**
     * Finds the total number of lines of code the target author wrote for each
     * source file.
     */
    public HashMap<SourceFile, Integer> getTargetAuthorStatistics() {
        HashMap<SourceFile, Integer> statistics = new HashMap<SourceFile, Integer>();

        if (targetAuthor != null) {
            for (CodeSnippet snippet : targetAuthor.getCodeSnippets()) {
                SourceFile currentFile = snippet.getFile();
                if (statistics.containsKey(currentFile)) {
                    statistics.put(currentFile, statistics.get(currentFile) +
                                                snippet.getNumLines());
                } else {
                    statistics.put(currentFile, snippet.getNumLines());
                }
            }
        }

        return statistics;
    }
}
