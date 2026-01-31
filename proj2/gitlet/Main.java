package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Chen
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                validateNumArgs(args, 1);
                Repository.init();
                break;
            case "add":
                checkInit();
                validateNumArgs(args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                checkInit();
                validateNumArgs(args, 2);
                Repository.commit(args[1]);
                break;
            case "rm":
                checkInit();
                validateNumArgs(args, 2);
                Repository.rm(args[1]);
                break;
            case "log":
                checkInit();
                validateNumArgs(args, 1);
                Repository.log();
                break;

            default:
                System.out.println("No command with that name exists.");
                System.exit(0);
                break;
        }
    }

    /**
     * Checks the number of arguments versus the expected number,
     * print out error message if they do not match.
     *
     * @param args Argument array passed in from command line
     * @param n Number of expected arguments
     */
    private static void validateNumArgs(String[] args, int n) {
        if (args.length != n) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }

    /**
     * Checks if this directory is correctly initialized.
     */
    private static void checkInit() {
        if (!Repository.GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }
}
