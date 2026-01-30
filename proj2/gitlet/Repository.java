package gitlet;

import java.io.File;
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

    /** TODO: javadoc here */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        STAGING_DIR.mkdir();
        REMOVE_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        HEADS_DIR.mkdir();
        ADD_DIR.mkdir();
        REMOVE_DIR.mkdir();

        // Create initial commit, and serialize it.
        Commit initialCommit = new Commit("initial commit");
        initialCommit.save();

        // Write initial commit id into master pointer.
        String id = initialCommit.getId();
        File masterRef =  join(HEADS_DIR, "master");
        writeContents(masterRef, id);

        // Set HEAD pointer (point to master)
        File headFile = join(GITLET_DIR, "HEAD");
        writeContents(headFile, "master");
    }
}
