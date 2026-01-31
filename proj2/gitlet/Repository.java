package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
        String blobId = Utils.sha1((Object) readContents(ADD_FILE));

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
        /// TODO: After finishing commit method, come back and check if this part is right.
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
            restrictedDelete(REMOVE_FILE_WITH_ID);
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
        String parent = readContentsAsString(HEAD_FILE);
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
        /// TODO: Test this part of function after rm is formed.
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
        String currentBranch = readContentsAsString(HEAD_FILE);
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
     *
     */
    public static void log() {

    }

    /** Get the head commit by getting HEAD id in persistence. */
    private static Commit getHeadCommit() {
        String branch = readContentsAsString(HEAD_FILE);
        String headCommitId = readContentsAsString(join(HEADS_DIR, branch));
        return readObject(join(COMMITS_DIR, headCommitId), Commit.class);
    }

    /** Clear the files in staging area. */
    private static void clearStaging() {
        deleteChildren(ADD_DIR);
        deleteChildren(REMOVE_DIR);
    }

    /** Delete children files and directories recursively. */
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
}
