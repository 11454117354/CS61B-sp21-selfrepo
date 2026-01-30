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
    private static final File CWD = new File(System.getProperty("user.dir"));
    /** Directories. */
    private static final File GITLET_DIR = join(CWD, ".gitlet");
    private static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    private static final File STAGING_DIR = join(GITLET_DIR, "staging");
    private static final File REFS_DIR = join(GITLET_DIR, "refs");
    private static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    private static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    private static final File HEADS_DIR = join(REFS_DIR, "heads");
    private static final File ADD_DIR = join(STAGING_DIR, "add");
    private static final File REMOVE_DIR = join(STAGING_DIR, "remove");

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

        // TODO: initialize the first commit(go and build commit class), and create master branch and HEAD pointer.
        Commit initialCommit = new Commit("initial commit");
    }
}
