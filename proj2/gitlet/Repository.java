package gitlet;

import java.io.File;
import static gitlet.Utils.*;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The 'objects' directory. */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    /** The 'staging' directory. */
    public static final File STAGING_DIR = join(GITLET_DIR, "staging");
    /** The 'commits' directory. */
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    /** The 'blobs' directory. */
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");

    /** TODO: javadoc here */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            System.exit(0);
        }
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        STAGING_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();

        /** TODO: initialize the first commit(go and build commit class), and create master branch and HEAD pointer */
        commit("initial commit");
    }

    public static void add() {

    }

    public static void commit(String message) {

    }
}
