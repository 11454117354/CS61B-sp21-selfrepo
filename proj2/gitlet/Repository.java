package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet repository.
 *  Encapsulates the repository's on-disk state
 *  (e.g., .gitlet directory structure, commits, staging area),
 *  and implements the logic for Gitlet commands like init, add and commit.
 *
 *  @author Chen
 */
public class Repository {
    /**
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** Directories. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File STAGING_DIR = join(GITLET_DIR, "staging");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    public static final File HEADS_DIR = join(REFS_DIR, "heads");
    public static final File ADD_DIR = join(STAGING_DIR, "add");
    public static final File REMOVE_DIR = join(STAGING_DIR, "remove");

    /** Files. */
    public static final File MASTER_FILE =  join(HEADS_DIR, "master");
    public static final File HEAD_FILE = join(GITLET_DIR, "HEAD");


    /**
     * Initialize the persistence system and pointers for gitlet.
     * A .gitlet folder will be generated, inside which has the persistence structure, as described in design document
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        STAGING_DIR.mkdir();
        REFS_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        HEADS_DIR.mkdir();
        ADD_DIR.mkdir();
        REMOVE_DIR.mkdir();

        /// Create initial commit, and serialize it.
        Commit initialCommit = new Commit("initial commit");
        initialCommit.save();

        /// Write initial commit id into master pointer.
        String id = initialCommit.getId();
        try {
            MASTER_FILE.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeContents(MASTER_FILE, id);

        /// Set HEAD pointer (point to master)
        try {
            HEAD_FILE.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeContents(HEAD_FILE, "master");
    }

    /**
     * Add ONE file into the staging area.
     * Specifically form the blob file in blob folder, check if it already exists in the staging area,
     * and delete it if it's in remove area.
     *
     * @param fileName The file to add
     */
    public static void add(String fileName) {
        final File ADD_FILE = join(CWD, fileName);

        /// Check if the file exists.
        if (!ADD_FILE.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }

        /// Generate the SHA-1 hash.
        String blobId = generateHash(ADD_FILE);

        /// Form the blob file and fill in the content
        final File BLOB_FILE = join(BLOBS_DIR, blobId);
        if (!BLOB_FILE.exists()) {
            try {
                BLOB_FILE.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeContents(BLOB_FILE, readContentsAsString(ADD_FILE));
        }

        /// Check if current head commit is in track of this file.
        Commit headCommit = getHeadCommit();
        if (headCommit.getTrackedFiles().containsKey(fileName)) {
            String headBlobId = headCommit.getTrackedFiles().get(fileName); /// The hash of the existing old file.

            /// If the added file is identical to that tracked by head commit, do not add it into stage area.
            if (headBlobId.equals(blobId)) {
                File EXISTING_IDENTICAL_FILE_IN_ADD = join(ADD_DIR, fileName);
                if (EXISTING_IDENTICAL_FILE_IN_ADD.exists()) {
                    restrictedDelete(EXISTING_IDENTICAL_FILE_IN_ADD);
                }
            } else {  /// File content is not identical, add it into stage area.
                File STAGE_FILE = join(ADD_DIR, fileName);
                try {
                    STAGE_FILE.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                writeContents(STAGE_FILE, blobId);
            }
        } else {  /// No such file in head commit, add into stage area.
            File STAGE_FILE = join(ADD_DIR, fileName);
            try {
                STAGE_FILE.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeContents(STAGE_FILE, blobId);
        }

        /// Check if it is in remove area. If so, delete it.
        File REMOVE_FILE_WITH_ID = join(REMOVE_DIR, fileName);
        if (REMOVE_FILE_WITH_ID.exists()) {
            REMOVE_FILE_WITH_ID.delete();
        }
    }

    /**
     * Saves a snapshot of tracked files in the current commit and staging area
     * so they can be restored at a later time, creating a new commit.
     *
     * @param message Message of this commit
     */
    public static void commit(String message) {
        /// Check if the message is blank.
        if (Objects.equals(message, "")) {
            System.out.println("Please enter a commit message.");
            System.exit(0);
        }

        /// Create a new commit.
        String currentBranch = readContentsAsString(HEAD_FILE);
        String parent = readContentsAsString(join(HEADS_DIR, currentBranch));
        Map<String, String> newTrackedFiles = new HashMap<>(getHeadCommit().getTrackedFiles());
        File[] addedFiles = ADD_DIR.listFiles(), removedFiles = REMOVE_DIR.listFiles();
        /// Add files to trackFiles map.
        if ((addedFiles == null || addedFiles.length == 0)
                && (removedFiles == null || removedFiles.length == 0)) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        if (addedFiles != null) {
            for (File f : addedFiles) {
                if (f.isFile()) {
                    String name = f.getName();
                    String blobId = readContentsAsString(f);
                    newTrackedFiles.put(name, blobId);
                }
            }
        }
        /// Remove files in trackFiles map.
        if (removedFiles != null) {
            for (File f : removedFiles) {
                if (f.isFile()) {
                    newTrackedFiles.remove(f.getName());
                }
            }
        }
        ////  Create the new commit
        Commit thisCommit = new Commit(message, parent, null, newTrackedFiles);

        /// Write this commit into persistence system.
        thisCommit.save();
        File branchRef = join(HEADS_DIR, currentBranch);
        writeContents(branchRef, thisCommit.getId());

        clearStaging();
    }

    /**
     * 1. Unstage the file in staging area if it is in (but do not delete it);
     * 2. Remove the file from the working directory (if still exists) and move it
     *    to remove area, when it is tracked by head commit.
     *
     * @param fileName The file to remove
     */
    public static void rm(String fileName) {
        /// Situation 1
        File[] addedFiles = ADD_DIR.listFiles();
        boolean addContainsFile = false;
        if (addedFiles != null) {
            for (File f : addedFiles) {
                if (f.getName().equals(fileName)) {
                    if (!f.delete()) {
                        throw new RuntimeException("Failed to delete staging file: " + f.getPath());
                    }
                    addContainsFile = true;
                    break;
                }
            }
        }

        /// Situation 2
        Commit headCommit = getHeadCommit();
        boolean headContainsFile = headCommit.getTrackedFiles().containsKey(fileName);
        if (headContainsFile) {
            File REMOVE_FILE = join(REMOVE_DIR, fileName);
            try {
                REMOVE_FILE.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            writeContents(REMOVE_FILE, headCommit.getTrackedFiles().get(fileName));

            /// Delete it if exists in working directory
            File DELETE_FILE_IN_WORKING_DIR = join(CWD, fileName);
            if (DELETE_FILE_IN_WORKING_DIR.exists()) {
                DELETE_FILE_IN_WORKING_DIR.delete();
            }
        }

        /// Failure cases
        if ((!addContainsFile) && (!headContainsFile)) {
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }
    }

    /**
     * Print out the commit information from the HEAD commit to init commit.
     * If one commit has two parent, print out the info, and go along the first parent.
     */
    public static void log() {
        Commit currentCommit = getHeadCommit();
        while (true) {
            printLog(currentCommit);
            if (currentCommit.getParent() == null) {
                break;
            }
            currentCommit = getCommit(currentCommit.getParent());
        }
    }

    /**
     * Print out all the commits in repository, regardless of branches they're in.
     */
    public static void globalLog() {
        List<String> commitIds = plainFilenamesIn(COMMITS_DIR);
        if (commitIds == null) {
            System.exit(0);
        }
        for (String commitId : commitIds) {
            Commit currentCommit = getCommit(commitId);
            printLog(currentCommit);
        }
    }

    /**
     * Print out the ids of all commits that have the given commit message, one per line.
     *
     * @param message The message to find
     */
    public static void find(String message) {
        List<String> commitIds = plainFilenamesIn(COMMITS_DIR);
        if (commitIds == null) {
            System.out.println("Found no commit with that message.");
            System.exit(0);
        }
        boolean foundCommit = false;
        for (String commitId : commitIds) {
            Commit currentCommit = getCommit(commitId);
            if (Objects.equals(message, currentCommit.getMessage())) {
                System.out.println(commitId);
                foundCommit = true;
            }
        }
        if (!foundCommit) {
            System.out.println("Found no commit with that message.");
        }
    }

    /**
     * Displays what branches currently exist, and marks the current branch with a *.
     * Also displays what files have been staged for addition or removal.
     */
    public static void status() {
        /// Print branches.
        System.out.println("=== Branches ===");
        List<String> branchNames = plainFilenamesIn(HEADS_DIR);
        assert branchNames != null;
        Collections.sort(branchNames);
        for (String branchName : branchNames) {
            String currentBranch = readContentsAsString(HEAD_FILE);
            if (Objects.equals(branchName, currentBranch)) {
                System.out.print("*");
            }
            System.out.println(branchName);
        }
        System.out.println();

        /// Print staged files.
        System.out.println("=== Staged Files ===");
        List<String> stagedFiles = plainFilenamesIn(ADD_DIR);
        if (stagedFiles != null) {
            Collections.sort(stagedFiles);
            for (String stagedFile : stagedFiles) {
                System.out.println(stagedFile);
            }
        }
        System.out.println();

        /// Print removed files.
        System.out.println("=== Removed Files ===");
        List<String> removedFiles = plainFilenamesIn(REMOVE_DIR);
        if (removedFiles != null) {
            Collections.sort(removedFiles);
            for (String removedFile : removedFiles) {
                System.out.println(removedFile);
            }
        }
        System.out.println();

        /// Print modified but not staged files.
        System.out.println("=== Modifications Not Staged For Commit ===");
        List<String> printOutFiles = new ArrayList<>(5);
        /// Get the file tracked in the current commit, changed in the working directory, but not staged.
        Commit headCommit = getHeadCommit();
        Map<String, String> headTrackedFiles = headCommit.getTrackedFiles();
        for (String trackedFileName : headTrackedFiles.keySet()) {
            if (stagedFiles != null) {
                if (stagedFiles.contains(trackedFileName)) {
                    continue;
                }
            }
            File FILE_IN_WORKING_DIRECTORY = join(CWD, trackedFileName);
            if (FILE_IN_WORKING_DIRECTORY.exists()) {
                String currentHash = generateHash(FILE_IN_WORKING_DIRECTORY);
                if (!currentHash.equals(headTrackedFiles.get(trackedFileName))) {
                    printOutFiles.add(trackedFileName + " (modified)");
                }
            }
        }
        /// Get the file staged for addition, but with different contents than in the working directory;
        /// and get the file staged for addition, but deleted in the working directory.
        if (stagedFiles != null) {
            for (String stagedFile : stagedFiles) {
                File FILE_IN_WORKING_DIRECTORY = join(CWD, stagedFile);
                if (!FILE_IN_WORKING_DIRECTORY.exists()) {
                    printOutFiles.add(stagedFile + " (deleted)");
                }
                String addHashOrigin = readContentsAsString(join(ADD_DIR, stagedFile));
                if (FILE_IN_WORKING_DIRECTORY.exists()) {
                    if (!generateHash(FILE_IN_WORKING_DIRECTORY).equals(addHashOrigin)) {
                        printOutFiles.add(stagedFile + " (modified)");
                    }
                }
            }
        }
        /// Get the file Not staged for removal,
        /// but tracked in the current commit and deleted from the working directory.
        for (String trackedFileName : headTrackedFiles.keySet()) {
            if (removedFiles != null) {
                if (removedFiles.contains(trackedFileName)) {
                    continue;
                }
            }
            File FILE_IN_WORKING_DIRECTORY = join(CWD, trackedFileName);
            if (!FILE_IN_WORKING_DIRECTORY.exists()) {
                printOutFiles.add(trackedFileName + " (deleted)");
            }
        }
        /// Print out all those files.
        Collections.sort(printOutFiles);
        for (String printOutFile : printOutFiles) {
            System.out.println(printOutFile);
        }
        System.out.println();

        /// Print untracked files
        System.out.println("=== Untracked Files ===");
        List<String> printOutUntrackedFiles = new ArrayList<>(5);
        List<String> filesInWorkingDirectory = plainFilenamesIn(CWD);
        if (filesInWorkingDirectory != null) {
            for (String fileInWorkingDirectory : filesInWorkingDirectory) {
                if (headTrackedFiles.containsKey(fileInWorkingDirectory)) {
                    continue;
                }
                if (stagedFiles != null) {
                    if (stagedFiles.contains(fileInWorkingDirectory)) {
                        continue;
                    }
                }
                printOutUntrackedFiles.add(fileInWorkingDirectory);
            }
        }
        if (removedFiles != null) {
            for (String removedFile : removedFiles) {
                if (filesInWorkingDirectory != null) {
                    if (filesInWorkingDirectory.contains(removedFile)) {
                        printOutUntrackedFiles.add(removedFile);
                    }
                }
            }
        }
        Collections.sort(printOutUntrackedFiles);
        for (String printOutUntrackedFile : printOutUntrackedFiles) {
            System.out.println(printOutUntrackedFile);
        }
        System.out.println();
    }

    /**
     * Take the file in head commit, replace the one in working directory.
     *
     * @param fileName The file to check out
     */
    public static void checkOutFile(String fileName) {
        Commit headCommit = getHeadCommit();
        Map<String, String> trackedFiles = headCommit.getTrackedFiles();
        if (!trackedFiles.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        File CHECKOUT_FILE = join(CWD, fileName);
        if (!CHECKOUT_FILE.exists()) {
            try {
                CHECKOUT_FILE.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        String fileHash = trackedFiles.get(fileName);
        writeContents(CHECKOUT_FILE, (Object) readContents(join(BLOBS_DIR, fileHash)));
    }

    /**
     * Take the version of file in the given commit, replace the one in working directory.
     *
     * @param prefix The commit version to take (allow user to input the first few digit of the id)
     * @param fileName The file to check out
     */
    public static void checkOutCommit(String prefix, String fileName) {
        List<String> commitIds = plainFilenamesIn(COMMITS_DIR);
        List<String> qualifiedIds = new ArrayList<>(2);
        assert commitIds != null;
        for (String commitId : commitIds) {
            if (commitId.startsWith(prefix)) {
                qualifiedIds.add(commitId);
            }
        }
        if (qualifiedIds.isEmpty()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        } else if (qualifiedIds.size() > 1) {
            System.out.println("Prefix not unique.");
            System.exit(0);
        } else {
            Commit destinedCommit = getCommit(qualifiedIds.get(0));
            Map<String, String> trackedFiles = destinedCommit.getTrackedFiles();
            if (!trackedFiles.containsKey(fileName)) {
                System.out.println("File does not exist in that commit.");
                System.exit(0);
            }
            File CHECKOUT_FILE = join(CWD, fileName);
            if (!CHECKOUT_FILE.exists()) {
                try {
                    CHECKOUT_FILE.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            String fileHash = trackedFiles.get(fileName);
            writeContents(CHECKOUT_FILE, (Object) readContents(join(BLOBS_DIR, fileHash)));
        }
    }

    /**
     * Take all files in the head commit of the new branch, put them in working directory,
     * overwrite if one exists.
     * Reset the HEAD branch.
     * Delete all files that are not in checkout branch.
     *
     * @param branchName Name of branch to check out
     */
    public static void checkOutBranch(String branchName) {
        List<String> branched = plainFilenamesIn(HEADS_DIR);
        assert branched != null;
        if (!branched.contains(branchName)) {
            System.out.println("No such branch exists.");
            System.exit(0);
        }
        if (Objects.equals(branchName, readContentsAsString(HEAD_FILE))){
            System.out.println("No need to checkout the current branch.");
            System.exit(0);
        }

        /// Find if there are untracked files.
        List<String> filesInCWD = plainFilenamesIn(CWD);
        Map<String, String> headTrackedFiles = getHeadCommit().getTrackedFiles();
        if (filesInCWD != null) {
            for (String fileInCWD : filesInCWD) {
                if (headTrackedFiles.containsKey(fileInCWD)) {
                    continue;
                }
                System.out.println("There is an untracked file in the way; " +
                        "delete it, or add and commit it first.");
                System.exit(0);
            }
        }

        /// Change files.
        Commit branchHeadCommit = getCommit(readContentsAsString(
                join(BLOBS_DIR, branchName)));
        Map<String, String> trackedFilesBranch = branchHeadCommit.getTrackedFiles();
        for (String fileName : trackedFilesBranch.keySet()) {
            File FILE_CWD = join(CWD, fileName);
            if (!FILE_CWD.exists()) {
                try {
                    FILE_CWD.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            writeContents(FILE_CWD, (Object) readContents(join(BLOBS_DIR, trackedFilesBranch.get(fileName))));
        }
    }

    /**
     * Get the head commit by getting HEAD id in persistence.
     */
    private static Commit getHeadCommit() {
        String branch = readContentsAsString(HEAD_FILE);
        String headCommitId = readContentsAsString(join(HEADS_DIR, branch));
        return readObject(join(COMMITS_DIR, headCommitId), Commit.class);
    }

    /**
     * Get the commit by its id.
     *
     * @param id The id of the commit
     * @return The Commit corresponding to this id
     */
    private static Commit getCommit(String id) {
        return readObject(join(COMMITS_DIR, id), Commit.class);
    }

    /**
     * Print out the log information of one commit.
     *
     * @param currentCommit The commit to print log
     */
    private static void printLog(Commit currentCommit) {
        System.out.println("===");
        System.out.println("commit " + currentCommit.getId());
        if (currentCommit.getSecondParent() != null) {
            System.out.println("Merge: " + currentCommit.getParent().substring(0, 7)
                    + currentCommit.getSecondParent().substring(0, 7));
        }
        System.out.println("Date: " + currentCommit.getTimestamp());
        System.out.println(currentCommit.getMessage());
        System.out.println();
    }

    /**
     * Clear the files in staging area.
     */
    private static void clearStaging() {
        deleteChildren(ADD_DIR);
        deleteChildren(REMOVE_DIR);
    }

    /**
     * Delete children files and directories recursively.
     *
     * @param dir The directory to delete children
     */
    private static void deleteChildren(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                deleteChildren(f);
            }
            if (!f.delete()) {
                throw new RuntimeException("Failed to delete staging file: " + f.getPath());
            }
        }
    }

    /**
     * Generate SHA-1 hash for a file.
     *
     * @param file The file to generate hash
     * @return The hash of the file
     */
    private static String generateHash(File file) {
        return Utils.sha1((Object) readContents(file));
    }
}
