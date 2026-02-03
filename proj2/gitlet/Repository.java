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
            quit("File does not exist.");
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
            quit("Please enter a commit message.");
        }

        /// Create a new commit.
        String currentBranch = readContentsAsString(HEAD_FILE);
        String parent = readContentsAsString(join(HEADS_DIR, currentBranch));
        Map<String, String> newTrackedFiles = new HashMap<>(getHeadCommit().getTrackedFiles());
        File[] addedFiles = ADD_DIR.listFiles(), removedFiles = REMOVE_DIR.listFiles();
        /// Add files to trackFiles map.
        if ((addedFiles == null || addedFiles.length == 0)
                && (removedFiles == null || removedFiles.length == 0)) {
            quit("No changes added to the commit.");
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
            quit("No reason to remove the file.");
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
        assert commitIds != null;
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
            quit("Found no commit with that message.");
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
            quit("File does not exist in that commit.");
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
        Commit destinedCommit = findCorrespondingCommit(prefix);
        Map<String, String> trackedFiles = destinedCommit.getTrackedFiles();
        if (!trackedFiles.containsKey(fileName)) {
            quit("File does not exist in that commit.");
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
            quit("No such branch exists.");
        }
        if (Objects.equals(branchName, readContentsAsString(HEAD_FILE))){
            quit("No need to checkout the current branch.");
        }

        /// Check if there are untracked files.
        checkUntrackedFiles();

        /// Change files.
        Commit branchHeadCommit = getCommit(readContentsAsString(
                join(HEADS_DIR, branchName)));
        writeAllFilesCWD(branchHeadCommit);

        /// Reset HEAD branch.
        writeContents(HEAD_FILE, branchName);

        /// Delete files that are not in the new commit.
        deleteFilesNotInHEADCommit();
    }

    /**
     * Create a new branch with the given name.This is actually a pointer pointing at HEAD.
     * Note that HEAD shouldn't change.
     *
     * @param branchName Name of the new branch
     */
    public static void branch(String branchName) {
        File branchFile = join(HEADS_DIR, branchName);
        if (branchFile.exists()) {
            quit("A branch with that name already exists.");
        }
        try {
            branchFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        writeContents(branchFile, getHeadCommit().getId());
    }

    /**
     * Deletes a branch with the given name.
     * That's to say only deletes the branch pointer, but do not delete those commits.
     *
     * @param branchName Name of branch to delete
     */
    public static void rmBranch(String branchName) {
        File branchToDelete = join(HEADS_DIR, branchName);
        if (!branchToDelete.exists()) {
            quit("A branch with that name does not exist.");
        }
        if (Objects.equals(branchName, readContentsAsString(HEAD_FILE))) {
            quit("Cannot remove the current branch.");
        }
        branchToDelete.delete();
    }

    /**
     * Set CWD to the destined commit, delete files that aren't tracked by that commit.
     * Move the HEAD pointer.
     * Clear staging area.
     *
     * @param prefix An arbitrary commit
     */
    public static void reset(String prefix) {
        /// Get the commit.
        Commit destinedCommit = findCorrespondingCommit(prefix);

        /// Check if there's untracked file.
        checkUntrackedFiles();

        /// Write files into CWD.
        writeAllFilesCWD(destinedCommit);

        /// This part is kind of weird? Change pointer.
        String branchName = readContentsAsString(HEAD_FILE);
        writeContents(join(HEADS_DIR, branchName), destinedCommit.getId());

        /// Delete files not in current HEAD commit.
        deleteFilesNotInHEADCommit();

        /// Clear staging area.
        clearStaging();
    }

    /**
     * Merges files from the given branch into the current branch.
     * 2 special situations below:
     * Situation 1(linear): Given branch's head commit is the split point. Do nothing and print message.
     * Situation 2(linear): Current branch's head commit is the split point. Checkout the given branch.
     * Both situation above don't make new commit.
     *
     * @param branchName The given branch to merge from
     */
    public static void merge(String branchName) {
        /// Check if there are staged files uncommited.
        if (!(hasFiles(ADD_DIR) || hasFiles(REMOVE_DIR))) {
            quit("You have uncommited changes.");
        }

        /// Check if the branch name exists.
        File branchFile = join(HEADS_DIR, branchName);
        if (!branchFile.exists()) {
            quit("A branch with that name does not exist.");
        }

        /// Check if the given branch is the current branch.
        if (Objects.equals(branchName, readContentsAsString(HEAD_FILE))) {
            quit("Cannot merge a branch with itself.");
        }

        /// Check if there are untracked files.
        checkUntrackedFiles();

        /// Get the split commit.
        Commit currentCommit = getHeadCommit();
        Commit givenBranchCommit =
                getCommit(readContentsAsString(join(HEADS_DIR, branchName)));
        Commit splitPointCommit = getSplitPointCommit(currentCommit, givenBranchCommit);

        /// Situation 1.
        if (splitPointCommit == givenBranchCommit) {
            quit("Given branch is an ancestor of the current branch.");
        }

        /// Situation 2.
        if (splitPointCommit == currentCommit) {
            checkOutBranch(branchName);
            quit("Current branch fast-forwarded.");
        }

        /// Non-special cases below.
        Map<String, String> filesCurrentCommit = currentCommit.getTrackedFiles();
        Map<String, String> filesGivenCommit = givenBranchCommit.getTrackedFiles();
        Map<String, String> filesSplitCommit = splitPointCommit.getTrackedFiles();

        for (String fileNameCurrentCommit : filesCurrentCommit.keySet()) {
            if (filesGivenCommit.containsKey(fileNameCurrentCommit)
                    && filesSplitCommit.containsKey(fileNameCurrentCommit)) {
                File fileCWD = join(CWD, fileNameCurrentCommit);
                String currentFileHash = filesCurrentCommit.get(fileNameCurrentCommit);
                String givenFileHash = filesGivenCommit.get(fileNameCurrentCommit);
                String splitFileHash = filesSplitCommit.get(fileNameCurrentCommit);

                /// Files modified in the given branch but not modified in current branch
                /// should be changed into the version in the given branch.
                if (Objects.equals(currentFileHash, splitFileHash)
                        && (!Objects.equals(givenFileHash, splitFileHash))) {
                    File givenFileBlob = join(BLOBS_DIR, givenFileHash);
                    writeContents(fileCWD, (Object) readContents(givenFileBlob));
                    add(fileNameCurrentCommit);
                }

                /// Files modified in different ways (3 files exists) are in conflict.
                if ((!Objects.equals(currentFileHash, givenFileHash))
                        && (!Objects.equals(currentFileHash, splitFileHash))
                        && (!Objects.equals(givenFileHash, splitFileHash))) {
                    File currentFileBlob = join(BLOBS_DIR, filesCurrentCommit.get(fileNameCurrentCommit));
                    File givenFileBlob = join(BLOBS_DIR, filesGivenCommit.get(fileNameCurrentCommit));
                    deelWithConflictMerge(fileNameCurrentCommit, currentFileBlob, givenFileBlob);
                }
            }

            if ((!filesGivenCommit.containsKey(fileNameCurrentCommit))
                    && filesSplitCommit.containsKey(fileNameCurrentCommit)) {
                String currentFileHash = filesCurrentCommit.get(fileNameCurrentCommit);
                String splitFileHash = filesSplitCommit.get(fileNameCurrentCommit);
                /// Files unmodified in current branch, but absent in given branch
                /// should be removed and untracked.
                if (Objects.equals(currentFileHash, splitFileHash)) {
                    File fileCWD = join(CWD, fileNameCurrentCommit);
                    fileCWD.delete();
                    rm(fileNameCurrentCommit);
                } else {
                    /// Files modified in current, deleted in given, should deel with conflict.
                    File currentFileBlob = join(BLOBS_DIR, filesCurrentCommit.get(fileNameCurrentCommit));
                    deelWithConflictMerge(fileNameCurrentCommit, currentFileBlob, null);
                }
            }

            /// Files absent at split, modified differently in given and current, should deel with conflict.
            if (!filesSplitCommit.containsKey(fileNameCurrentCommit)) {
                String currentFileHash = filesCurrentCommit.get(fileNameCurrentCommit);
                String givenFileHash = filesGivenCommit.get(fileNameCurrentCommit);
                if (!Objects.equals(currentFileHash, givenFileHash)) {
                    File currentFileBlob = join(BLOBS_DIR, filesCurrentCommit.get(fileNameCurrentCommit));
                    File givenFileBlob = join(BLOBS_DIR, filesGivenCommit.get(fileNameCurrentCommit));
                    deelWithConflictMerge(fileNameCurrentCommit, currentFileBlob, givenFileBlob);
                }
            }
        }

        for (String fileNameGivenCommit : filesGivenCommit.keySet()) {
            /// Files only present in the given branch should be checked out and staged.
            if ((!filesCurrentCommit.containsKey(fileNameGivenCommit))
                    && (!filesSplitCommit.containsKey(fileNameGivenCommit))) {
                checkOutCommit(givenBranchCommit.getId(), fileNameGivenCommit);
                add(fileNameGivenCommit);
            }
            /// Files modified in given, deleted in current, should deel with conflict.
            if ((!filesCurrentCommit.containsKey(fileNameGivenCommit))
                    && filesSplitCommit.containsKey(fileNameGivenCommit)) {
                String givenFileHash = filesGivenCommit.get(fileNameGivenCommit);
                String splitFileHash = filesSplitCommit.get(fileNameGivenCommit);
                if (!Objects.equals(givenFileHash, splitFileHash)) {
                    File givenFileBlob = join(BLOBS_DIR, filesGivenCommit.get(fileNameGivenCommit));
                    deelWithConflictMerge(fileNameGivenCommit, null, givenFileBlob);
                }
            }
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

    /**
     * Get the Commit by the prefix.
     *
     * @param prefix The arbitrary commit id
     * @return The wanted commit
     */
    private static Commit findCorrespondingCommit(String prefix) {
        List<String> commitIds = plainFilenamesIn(COMMITS_DIR);
        List<String> qualifiedIds = new ArrayList<>();
        assert commitIds != null;
        for (String commitId : commitIds) {
            if (commitId.startsWith(prefix)) {
                qualifiedIds.add(commitId);
            }
        }
        if (qualifiedIds.isEmpty()) {
            quit("No commit with that id exists.");
        } else if (qualifiedIds.size() > 1) {
            quit("Prefix not unique.");
        }
        return getCommit(qualifiedIds.get(0));
    }

    /**
     * Check if there are untracked files exist. If so, print a message.
     */
    private static void checkUntrackedFiles() {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        Map<String, String> headTrackedFiles = getHeadCommit().getTrackedFiles();
        if (filesInCWD != null) {
            for (String fileInCWD : filesInCWD) {
                if (headTrackedFiles.containsKey(fileInCWD)) {
                    continue;
                }
                quit("There is an untracked file in the way; " +
                        "delete it, or add and commit it first.");
            }
        }
    }

    /**
     * Delete the files that are not in current commit.
     */
    private static void deleteFilesNotInHEADCommit() {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        Map<String, String> headTrackedFiles = getHeadCommit().getTrackedFiles();
        if (filesInCWD != null) {
            for (String fileInCWD : filesInCWD) {
                if (headTrackedFiles.containsKey(fileInCWD)) {
                    continue;
                }
                restrictedDelete(fileInCWD);
            }
        }
    }

    /**
     * Write everything in one Commit to current directory.
     *
     * @param targetCommit The commit to get files from
     */
    private static void writeAllFilesCWD(Commit targetCommit) {
        Map<String, String> trackedFilesBranch = targetCommit.getTrackedFiles();
        for (String fileName : trackedFilesBranch.keySet()) {
            File FILE_CWD = join(CWD, fileName);
            if (!FILE_CWD.exists()) {
                try {
                    FILE_CWD.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            writeContents(FILE_CWD, (Object) readContents(
                    join(BLOBS_DIR, trackedFilesBranch.get(fileName))));
        }
    }

    /**
     * Check if a directory has files inside.
     *
     * @param directory The directory to check
     * @return True if it has files
     */
    private static boolean hasFiles(File directory) {
        File[] files = directory.listFiles();
        return files != null && files.length > 0;
    }

    /**
     * Quit the program with the message.
     *
     * @param message The message to print
     */
    private static void quit(String message) {
        System.out.println(message);
        System.exit(0);
    }

    /**
     * Get the split point commit by double circus pointer.
     *
     * @param c1 The newest commit of one branch
     * @param c2 The newest commit of another branch
     * @return The split point commit; null if no split point
     */
    private static Commit getSplitPointCommit(Commit c1, Commit c2) {
        if (c1 == null || c2 == null) {
            return null;
        }
        Commit pointerA = c1, pointerB = c2;
        while (pointerA != pointerB) {
            pointerA = (pointerA == null) ? c2 : getCommit(pointerA.getParent());
            pointerB = (pointerB == null) ? c1 : getCommit(pointerB.getParent());
        }
        return pointerA;
    }

    /**
     * Deel with merge conflict. Form a file with special content.
     *
     * @param fileName The name of the conflict file
     * @param currentFile The file from current branch, null if not exist
     * @param givenFile The file from given branch, null if not exist
     */
    private static void deelWithConflictMerge(String fileName, File currentFile, File givenFile) {
        String currentContent = (currentFile == null) ? null : readContentsAsString(currentFile);
        String givenContent = (givenFile == null) ? null : readContentsAsString(givenFile);

        String newContent = "<<<<<<< HEAD\n" + currentContent
                + "=======\n" + givenContent + ">>>>>>>\n";
        File newContentFile = join(CWD, fileName);
        if (!newContentFile.exists()) {
            try {
                newContentFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        writeContents(newContentFile, newContent);
        add(fileName);
    }
}






