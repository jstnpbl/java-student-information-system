// StudentInfoSystem.java
import java.io.Console;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class StudentInfoSystem {

    // TESTING SWITCH: true = if there is no students.txt yet, start with sample students
    // (and save them to a new students.txt). Once the file exists, it is always used instead.
    // To go back to the sample data: close the program and delete students.txt.
    // Set this to false to start with an empty list when there is no file.
    private static final boolean LOAD_SAMPLE_DATA = true;

    // ---- Login ----
    // TESTING SWITCH: set REQUIRE_LOGIN to false to skip the login screen while testing.
    // NOTE: this is a simple login SCREEN, not real security. The username and password are
    // written right here in the source code, and students.txt can be opened by anyone.
    private static final boolean REQUIRE_LOGIN = true;
    private static final String ADMIN_USER = "admin";        // not case-sensitive
    private static final String ADMIN_PASS = "password123";  // the DEFAULT password (case-sensitive)
    private static final int MIN_PASSWORD_LENGTH = 6;        // for passwords chosen with "Change Password"
    private static final int MAX_LOGIN_ATTEMPTS = 3;

    // true  = typing the password shows nothing on screen (hidden, no asterisks either).
    // false = the password is visible while typing. Use false if hidden typing misbehaves
    //         on your computer, or while you are testing.
    private static final boolean HIDE_PASSWORD = false;

    // A one-time message shown above the main menu (used for the welcome after login).
    private static String menuNotice = "";

    // ---- Where records are saved ----
    // One student per line, fields separated by '|':
    //   id|fullName|middleInitial|course|age|average      (a '.' means "no middle initial")
    // The first line, NEXT_ID=..., remembers the next ID number so that an ID
    // is never reused, even if the newest student is deleted and the program restarted.
    private static final String DATA_FILE = "students.txt";
    private static final Path DATA_PATH = Paths.get(DATA_FILE);
    private static final Path BACKUP_PATH = Paths.get(DATA_FILE + ".bak");       // the version before the last save
    private static final Path CORRUPT_PATH = Paths.get(DATA_FILE + ".corrupt");  // copy of a file that had bad lines
    private static final String NEXT_ID_PREFIX = "NEXT_ID=";

    // A changed password is stored here as a salted hash (never as plain text).
    // If this file is missing or damaged, the default password (ADMIN_PASS) works again.
    private static final Path LOGIN_PATH = Paths.get("login.txt");
    private static String savedSalt = "";   // both stay "" until the password has been changed
    private static String savedHash = "";

    // The spreadsheet file written by "Export to CSV".
    private static final String CSV_FILE = "students.csv";

    // The ways View All Students can be sorted (menu choice 1 is position 0).
    private static final String[] SORT_LABELS = {
        "ID", "Name (A-Z)", "Average (high to low)", "Average (low to high)", "Age (youngest first)"
    };

    // In-memory list of students. Records are lost when the program closes.
    private static ArrayList<Student> students = new ArrayList<>();
    private static Scanner scanner = new Scanner(System.in);

    // The widest the NAME column in the table is allowed to get. Longer names are cut with "...".
    private static final int MAX_NAME_WIDTH = 40;

    // The only courses/tracks we accept.
    private static final String[] COURSES = {"ICT", "HUMSS", "STEM", "ABM", "GAS"};

    // IDs look like 2026-001. The year is fixed for now.
    private static final String ID_YEAR = "2026";

    // The number the NEXT new student will get. It only ever goes up,
    // so a deleted student's ID is never handed out again.
    private static int nextIdNumber = 1;

    // The word the user can type at any prompt to back out of what they're doing.
    private static final String CANCEL_WORD = "cancel";

    // A tiny "signal" class. When the user types 'cancel', readInput() throws one of these.
    // It jumps straight out of whatever we were doing (Add, Update, ...) to the code
    // that catches it. That way we don't need to check for 'cancel' after every prompt.
    private static class CancelException extends RuntimeException {
    }

    // ===================== MAIN MENU LOOP =====================
    public static void main(String[] args) {
        // Log in BEFORE touching any data, so a failed login never loads or changes students.txt.
        if (REQUIRE_LOGIN) {
            loadLoginFile();
            checkLogin();
        }

        // Load saved records, or (first run only) start from the sample data.
        boolean fileExisted = Files.exists(DATA_PATH);
        boolean hadWarnings = false;
        if (fileExisted) {
            hadWarnings = loadStudentsFromFile();
        } else if (LOAD_SAMPLE_DATA) {
            loadSampleData();
        }
        setNextIdNumberFromExistingStudents();   // makes new IDs continue after the highest existing one
        if (!fileExisted && LOAD_SAMPLE_DATA) {
            saveStudents();                      // create students.txt with the sample students
        }
        if (hadWarnings) {
            pressEnterToContinue();              // so the warnings aren't wiped by the first screen clear
        }

        boolean running = true;

        while (running) {
            clearScreen();
            showMenu();
            String choice = readLine().trim();   // raw read: 'cancel' means nothing at the menu

            // Each feature (1-9) gets a fresh, clean screen.
            if (choice.matches("[1-9]")) {
                clearScreen();
            }

            boolean skipPause = false;   // View All has its own "Back" option, so it needs no pause
            try {
                if (choice.equals("1")) {
                    addStudent();
                } else if (choice.equals("2")) {
                    skipPause = viewAllStudents();
                } else if (choice.equals("3")) {
                    searchById();
                } else if (choice.equals("4")) {
                    searchByName();
                } else if (choice.equals("5")) {
                    updateStudent();
                } else if (choice.equals("6")) {
                    deleteStudent();
                } else if (choice.equals("7")) {
                    showStatistics();
                } else if (choice.equals("8")) {
                    exportToCsv();
                } else if (choice.equals("9")) {
                    changePassword();
                } else if (choice.equals("0")) {
                    System.out.println("Goodbye!");
                    running = false;
                } else {
                    System.out.println("Invalid choice. Please enter a number from 0 to 9.");
                }
            } catch (CancelException e) {
                // The user typed 'cancel' somewhere inside a feature.
                System.out.println("Cancelled. Nothing was saved or changed.");
            }

            // Let the user read the result before the screen is cleared.
            if (running && !skipPause) {
                pressEnterToContinue();
            }
        }
        scanner.close();
    }

    private static void showMenu() {
        if (!menuNotice.isEmpty()) {
            System.out.println(menuNotice);
            System.out.println();
            menuNotice = "";   // show it only once
        }
        System.out.println("===== STUDENT INFORMATION SYSTEM =====");
        System.out.println("1. Add Student");
        System.out.println("2. View All Students (sort / filter)");
        System.out.println("3. Search Student by ID");
        System.out.println("4. Search Student by Name");
        System.out.println("5. Update Student Record");
        System.out.println("6. Delete Student Record");
        System.out.println("7. Class Statistics");
        System.out.println("8. Export to CSV (for Excel)");
        System.out.println("9. Change Password");
        System.out.println("0. Exit");
        System.out.println();
        System.out.print("Choose an option: ");
    }

    private static void printCancelHint() {
        System.out.println("(Type '" + CANCEL_WORD + "' at any prompt to go back to the menu.)");
    }

    // ===================== SAMPLE DATA (for testing) =====================
    // Only used when there is no students.txt yet (see LOAD_SAMPLE_DATA).

    // Covers every course, with and without middle initials, the age and average
    // limits (18, 80, 0, 100), a decimal average, and similar names ("Juan"/"Juana")
    // so name search can be tested without typing everything in each time.
    private static void loadSampleData() {
        char none = Student.NO_MIDDLE_INITIAL;
        students.add(new Student("2026-001", "Juan Dela Cruz",         none, "ICT",   18, 87.50));
        students.add(new Student("2026-002", "Maria Santos",           'L',  "STEM",  19, 92.25));
        students.add(new Student("2026-003", "Pedro Reyes",            'A',  "HUMSS", 20, 78.00));
        students.add(new Student("2026-004", "Ana Marie Garcia",       none, "ABM",   21, 85.75));
        students.add(new Student("2026-005", "Jose Mercado",           'P',  "GAS",   80, 100.0));
        students.add(new Student("2026-006", "Juana Lopez",            'C',  "ICT",   25, 0.0));
        students.add(new Student("2026-007", "Mark Anthony Villanueva",'B',  "STEM",  30, 69.40));
        students.add(new Student("2026-008", "Carla Bautista",         none, "HUMSS", 22, 95.00));
    }

    // ===================== LOGIN =====================

    // Asks for the username and password. After MAX_LOGIN_ATTEMPTS wrong tries, the program stops.
    // The error message is the same whether the username or the password was wrong,
    // so it doesn't reveal which one was right.
    private static void checkLogin() {
        int attemptsLeft = MAX_LOGIN_ATTEMPTS;
        String error = "";

        while (attemptsLeft > 0) {
            clearScreen();
            System.out.println("===== SYSTEM LOGIN =====");
            if (!error.isEmpty()) {
                System.out.println(error);
            }
            System.out.println();
            if (passwordIsHidden()) {
                System.out.println("(When you type the password, nothing will appear on screen.");
                System.out.println(" That is normal. Just type it and press Enter.)");
                System.out.println();
            }

            System.out.print("Username: ");
            String username = readLine().trim();
            String password = readPassword("Password: ");

            if (username.equalsIgnoreCase(ADMIN_USER) && passwordMatches(password)) {
                menuNotice = "Login successful. Welcome!";
                return;   // carry on to the main menu
            }

            attemptsLeft--;
            error = "Invalid username or password. Attempts remaining: " + attemptsLeft;
        }

        System.out.println();
        System.out.println("Invalid username or password.");
        System.out.println("Too many failed attempts. Shutting down.");
        System.exit(1);
    }

    // True if the password will be typed invisibly: hiding is switched on AND Java has a real console.
    private static boolean passwordIsHidden() {
        return HIDE_PASSWORD && System.console() != null;
    }

    // Reads a password. In a real Command Prompt window the typing is hidden.
    // If hiding is switched off, or Java has no console (for example when run from some
    // editors), it falls back to normal typing, where the password is visible.
    private static String readPassword(String prompt) {
        Console console = System.console();
        if (HIDE_PASSWORD && console != null) {
            char[] typed = console.readPassword(prompt);
            if (typed == null) {   // input was closed
                System.out.println();
                System.out.println("Input ended. Exiting program.");
                System.exit(0);
            }
            String password = new String(typed).trim();
            Arrays.fill(typed, ' ');   // wipe the characters from memory
            return password;
        }
        System.out.print(prompt);
        return readLine().trim();
    }

    // ===================== PASSWORD STORAGE =====================
    // How a changed password is kept safe: we never store the password itself.
    // We store a random "salt" plus the SHA-256 "hash" of (salt + password). A hash can't be
    // turned back into the password, but the same password always gives the same hash,
    // so we can check a typed password by hashing it again and comparing.
    // (This keeps the password unreadable in login.txt. It is still only a simple login
    // screen: anyone who can edit the files can delete login.txt to reset the password.)

    private static String hashPassword(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, bytes));   // 64 hex characters
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);   // every Java has it
        }
    }

    private static String createSalt() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return String.format("%032x", new BigInteger(1, bytes));   // 32 hex characters
    }

    // Reads login.txt if there is one. A missing, unreadable or damaged file just means
    // "use the default password".
    private static void loadLoginFile() {
        savedSalt = "";
        savedHash = "";
        if (!Files.exists(LOGIN_PATH)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(LOGIN_PATH, StandardCharsets.UTF_8);
            if (lines.size() >= 2
                    && lines.get(0).trim().matches("[0-9a-f]{32}")
                    && lines.get(1).trim().matches("[0-9a-f]{64}")) {
                savedSalt = lines.get(0).trim();
                savedHash = lines.get(1).trim();
            }
        } catch (IOException e) {
            // can't read it: fall back to the default password
        }
    }

    // True if the typed password is the right one.
    private static boolean passwordMatches(String typed) {
        if (savedHash.isEmpty()) {
            return typed.equals(ADMIN_PASS);   // never changed: the default password
        }
        return hashPassword(savedSalt, typed).equals(savedHash);
    }

    // Like readPassword, but pressing Enter on an empty line cancels.
    private static String readPasswordOrCancel(String prompt) {
        String text = readPassword(prompt);
        if (text.isEmpty()) {
            throw new CancelException();
        }
        return text;
    }

    private static void changePassword() {
        if (!REQUIRE_LOGIN) {
            System.out.println("The login is switched off (REQUIRE_LOGIN = false), so there is no password to change.");
            return;
        }

        System.out.println("CHANGE PASSWORD");
        System.out.println("(Press Enter without typing anything to cancel.)");
        System.out.println();

        String current = readPasswordOrCancel("Current password: ");
        if (!passwordMatches(current)) {
            System.out.println("Incorrect password. Password not changed.");
            return;
        }

        String newPassword;
        while (true) {
            newPassword = readPasswordOrCancel("New password (at least " + MIN_PASSWORD_LENGTH + " characters): ");
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                System.out.println("Error: the password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            } else {
                break;
            }
        }

        String again = readPasswordOrCancel("Type the new password again: ");
        if (!again.equals(newPassword)) {
            System.out.println("The two passwords did not match. Password not changed.");
            return;
        }

        String salt = createSalt();
        List<String> lines = new ArrayList<>();
        lines.add(salt);
        lines.add(hashPassword(salt, newPassword));
        try {
            Files.write(LOGIN_PATH, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Could not save the new password: " + e.getMessage());
            System.out.println("Password not changed.");
            return;
        }

        savedSalt = lines.get(0);
        savedHash = lines.get(1);
        System.out.println("Password changed. Use the new password the next time you log in.");
    }

    // ===================== SCREEN HELPERS =====================

    // Clears the console. On Windows this runs the same "cls" command you'd type yourself.
    // On other systems it prints a special code that most terminals understand.
    private static void clearScreen() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Couldn't clear for some reason: just print blank lines instead.
            for (int i = 0; i < 30; i++) {
                System.out.println();
            }
        }
    }

    private static void pressEnterToContinue() {
        System.out.println();
        System.out.print("Press Enter to continue...");
        readLine();
    }

    // ===================== INPUT HELPERS =====================

    // Reads one line from the keyboard, with no special meaning for any word.
    // If the input stream closes (for example Ctrl+Z in Command Prompt),
    // Scanner would crash the program, so we exit politely instead.
    private static String readLine() {
        try {
            return scanner.nextLine();
        } catch (NoSuchElementException e) {
            System.out.println();
            System.out.println("Input ended. Exiting program.");
            System.exit(0);
            return "";   // never reached, but Java needs a return here
        }
    }

    // Like readLine(), but if the user typed 'cancel' (any capitals), it throws a
    // CancelException instead. Every prompt inside a feature uses this one.
    private static String readInput() {
        String text = readLine();
        if (text.trim().equalsIgnoreCase(CANCEL_WORD)) {
            throw new CancelException();
        }
        return text;
    }

    // Keeps asking until the user types something that isn't blank.
    private static String readNonEmptyText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String text = readInput().trim();   // removes spaces at the ends only
            if (text.isEmpty()) {
                System.out.println("Error: this field cannot be empty.");
            } else {
                return text;
            }
        }
    }

    // A full name: not blank, and no '|' (that character separates fields in students.txt).
    private static String readFullName(String prompt) {
        while (true) {
            String name = readNonEmptyText(prompt);
            if (name.contains("|")) {
                System.out.println("Error: the '|' character is not allowed in names.");
            } else {
                return name;
            }
        }
    }

    // Keeps asking until the user types y or n (any capitals). Returns true for y.
    private static boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            String answer = readInput().trim().toLowerCase();
            if (answer.equals("y")) {
                return true;
            } else if (answer.equals("n")) {
                return false;
            }
            System.out.println("Please type y or n.");
        }
    }

    // Keeps asking until the user types a whole number between min and max.
    private static int readIntInRange(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String text = readInput().trim();
            try {
                int value = Integer.parseInt(text);   // throws if text isn't a whole number
                if (value < min || value > max) {
                    System.out.println("Error: must be between " + min + " and " + max + ".");
                } else {
                    return value;
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: please enter a whole number (digits only).");
            }
        }
    }

    // Keeps asking until the user types a decimal number between min and max.
    private static double readDoubleInRange(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt);
            String text = readInput().trim();

            // Java's parseDouble also accepts "NaN", "Infinity", "88d", "1e2"...
            // so we first check that the text looks like a plain number: 88 or 88.5 or .5
            // (optional minus sign, digits, optional decimal part).
            if (!text.matches("-?(\\d+\\.?\\d*|\\.\\d+)")) {
                System.out.println("Error: please enter a plain number (e.g. 88.5).");
                continue;   // ask again
            }

            double value = Double.parseDouble(text);   // safe now: the text is a valid number
            // A super long string of digits can overflow to Infinity, so check that too.
            if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
                System.out.printf("Error: must be between %.0f and %.0f.%n", min, max);
            } else {
                return value;
            }
        }
    }

    // Asks for a middle initial: one letter, or [N/A] for none.
    // For "none" we accept [N/A], N/A, n/a, [n/a]... in any capitals.
    // (A single letter N is still treated as the initial N.)
    private static char readMiddleInitial(String prompt) {
        while (true) {
            System.out.print(prompt);
            String text = readInput().trim();
            String upper = text.toUpperCase();

            if (upper.equals("[N/A]") || upper.equals("N/A")) {
                return Student.NO_MIDDLE_INITIAL;
            }
            if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
                return Character.toUpperCase(text.charAt(0));   // store as a capital
            }
            System.out.println("Error: enter exactly one letter (e.g. L), or [N/A] for none.");
        }
    }

    // Shows the numbered course list and returns the chosen course name.
    private static String readCourse() {
        System.out.println("Course/Track:");
        for (int i = 0; i < COURSES.length; i++) {
            System.out.println("  " + (i + 1) + ". " + COURSES[i]);
        }
        int choice = readIntInRange("Choose a course (1-" + COURSES.length + "): ", 1, COURSES.length);
        return COURSES[choice - 1];   // menu number 1 is array position 0
    }

    // ===================== SAVING AND LOADING =====================

    // True if 'course' is one of the allowed courses (exact match, e.g. "ICT").
    private static boolean isValidCourse(String course) {
        for (String c : COURSES) {
            if (c.equals(course)) {
                return true;
            }
        }
        return false;
    }

    // Turns one line of the file into a Student, or returns null if the line is not valid.
    // The checks match the rules used when adding a student.
    private static Student parseStudentLine(String line) {
        String[] parts = line.split("\\|", -1);   // -1 keeps empty fields at the end
        if (parts.length != 6) {
            return null;
        }

        String id = parts[0].trim();
        String name = parts[1].trim();
        String initialText = parts[2].trim();
        String course = parts[3].trim();
        if (id.isEmpty() || name.isEmpty() || initialText.length() != 1 || !isValidCourse(course)) {
            return null;
        }

        char initial = Character.toUpperCase(initialText.charAt(0));
        if (initial != Student.NO_MIDDLE_INITIAL && !Character.isLetter(initial)) {
            return null;
        }

        try {
            int age = Integer.parseInt(parts[4].trim());
            double average = Double.parseDouble(parts[5].trim());
            if (age < 18 || age > 80) {
                return null;
            }
            if (Double.isNaN(average) || Double.isInfinite(average) || average < 0 || average > 100) {
                return null;
            }
            return new Student(id, name, initial, course, age, average);
        } catch (NumberFormatException e) {
            return null;   // age or average wasn't a number
        }
    }

    // Reads students.txt into the 'students' list.
    // Bad lines are skipped with a warning (never a crash). Returns true if anything was printed.
    private static boolean loadStudentsFromFile() {
        List<String> lines;
        try {
            lines = Files.readAllLines(DATA_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // We can't read it, so we must NOT carry on: the next save would overwrite the file.
            System.out.println("Could not read " + DATA_FILE + ": " + e.getMessage());
            System.out.println("Exiting so your data is not overwritten. Fix the problem and run again.");
            System.exit(1);
            return false;   // never reached
        }

        int skipped = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);   // some editors put an invisible marker at the start
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;   // blank lines are fine
            }

            if (line.startsWith(NEXT_ID_PREFIX)) {
                try {
                    nextIdNumber = Integer.parseInt(line.substring(NEXT_ID_PREFIX.length()).trim());
                } catch (NumberFormatException e) {
                    System.out.println("Warning: skipped line " + (i + 1) + " (bad NEXT_ID): " + line);
                    skipped++;
                }
                continue;
            }

            Student student = parseStudentLine(line);
            if (student == null || findStudentById(student.getStudentId()) != null) {
                System.out.println("Warning: skipped line " + (i + 1) + " (invalid or duplicate): " + line);
                skipped++;
            } else {
                students.add(student);
            }
        }

        if (skipped == 0) {
            return false;
        }

        // The next save would drop the bad lines, so keep a copy of the original first.
        System.out.println();
        System.out.println(skipped + " line(s) were skipped.");
        try {
            Files.copy(DATA_PATH, CORRUPT_PATH, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("A copy of the original file was saved as " + CORRUPT_PATH + ".");
        } catch (IOException e) {
            System.out.println("(Could not make a backup copy of the original file.)");
        }
        return true;
    }

    // One student as one line of text, in the format described near the top of the file.
    private static String formatStudentLine(Student s) {
        return s.getStudentId() + "|" + s.getFullName() + "|" + s.getMiddleInitial() + "|"
                + s.getCourse() + "|" + s.getAge() + "|" + s.getAverage();
    }

    // Writes every student to students.txt. Called after each add, update, and delete.
    // Steps: copy the old file to students.txt.bak, write the new data to a temporary file,
    // then rename it over students.txt. That way a crash in the middle of saving can't
    // leave a half-written students.txt behind.
    private static void saveStudents() {
        List<String> lines = new ArrayList<>();
        lines.add(NEXT_ID_PREFIX + nextIdNumber);
        for (Student s : students) {
            lines.add(formatStudentLine(s));
        }

        Path tempPath = Paths.get(DATA_FILE + ".tmp");
        try {
            if (Files.exists(DATA_PATH)) {
                Files.copy(DATA_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(tempPath, lines, StandardCharsets.UTF_8);
            Files.move(tempPath, DATA_PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println();
            System.out.println("WARNING: could not save to " + DATA_FILE + ": " + e.getMessage());
            System.out.println("Your change is still in memory, but it will be lost when you exit.");
            pressEnterToContinue();
        }
    }

    // ===================== STUDENT HELPERS =====================

    // Returns the matching student, or null if the ID doesn't exist.
    private static Student findStudentById(String id) {
        for (Student s : students) {
            if (s.getStudentId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    // Returns every student whose full name contains the text (ignoring capitals).
    private static ArrayList<Student> findStudentsByName(String query) {
        String queryLower = query.toLowerCase();
        ArrayList<Student> matches = new ArrayList<>();
        for (Student s : students) {
            if (s.getFullName().toLowerCase().contains(queryLower)) {
                matches.add(s);
            }
        }
        return matches;
    }

    // Asks for an ID and returns that student, or null if we couldn't get one.
    // If the ID doesn't exist, offers to look the student up by name instead.
    // 'action' is just a word for the prompt, like "update" or "delete".
    private static Student askForExistingStudent(String action) {
        if (students.isEmpty()) {
            System.out.println("There are no student records yet.");
            return null;
        }

        while (true) {
            String id = readNonEmptyText("Enter Student ID to " + action + ": ");
            Student found = findStudentById(id);
            if (found != null) {
                return found;
            }

            System.out.println("No student found with ID " + id + ".");
            if (!readYesNo("Search by name instead? (y/n): ")) {
                return null;   // back to the menu
            }

            String query = readNonEmptyText("Enter name (or part of a name): ");
            ArrayList<Student> matches = findStudentsByName(query);
            if (matches.isEmpty()) {
                System.out.println("No students found matching \"" + query + "\".");
                return null;
            }
            System.out.println(matches.size() + " match(es) found:");
            printStudentTable(matches);
            System.out.println("Use an ID from the list above.");
            // loop around: ask for the ID again, now that the user can see it
        }
    }

    // Looks at every existing ID (like "2026-007"), finds the biggest number after the dash,
    // and starts counting from the next one.
    private static void setNextIdNumberFromExistingStudents() {
        int highest = 0;
        for (Student s : students) {
            String id = s.getStudentId();
            if (id.startsWith(ID_YEAR + "-")) {
                try {
                    int number = Integer.parseInt(id.substring(ID_YEAR.length() + 1));
                    if (number > highest) {
                        highest = number;
                    }
                } catch (NumberFormatException e) {
                    // not a number after the dash: ignore this ID
                }
            }
        }
        // Never go backwards: nextIdNumber may already hold a bigger value read from the file.
        nextIdNumber = Math.max(nextIdNumber, highest + 1);
    }

    // Builds the next ID, like 2026-001, and moves the counter forward.
    private static String generateStudentId() {
        while (true) {
            String id = ID_YEAR + "-" + String.format("%03d", nextIdNumber);
            nextIdNumber++;
            if (findStudentById(id) == null) {   // safety check: skip it if somehow taken
                return id;
            }
        }
    }

    // How a middle initial is shown in the update summary.
    private static String initialText(char initial) {
        if (initial == Student.NO_MIDDLE_INITIAL) {
            return "[N/A]";
        }
        return String.valueOf(initial);
    }

    private static void printStudent(Student s) {
        System.out.println("ID:      " + s.getStudentId());
        System.out.println("Name:    " + s.getDisplayName());
        System.out.println("Course:  " + s.getCourse());
        System.out.println("Age:     " + s.getAge());
        System.out.printf("Average: %.2f%n", s.getAverage());
    }

    // Cuts text down to maxLength characters, ending in "..." if something was removed.
    private static String shorten(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    // Prints a list of students as an aligned table (used by View All and Search by Name).
    // The NAME column is as wide as the longest name (up to MAX_NAME_WIDTH), so rows
    // always line up. In the formats: %-10s = left-aligned text, 10 characters wide;
    // %3d = number, 3 wide, right-aligned; %7.2f = decimal with 2 places, 7 wide.
    private static void printStudentTable(ArrayList<Student> list) {
        int nameWidth = "NAME".length();
        for (Student s : list) {
            nameWidth = Math.max(nameWidth, s.getDisplayName().length());
        }
        nameWidth = Math.min(nameWidth, MAX_NAME_WIDTH);

        // The width is not known until now, so the format text is built while the program runs.
        String headerFormat = "%-10s | %-" + nameWidth + "s | %-6s | %3s | %7s%n";
        String rowFormat    = "%-10s | %-" + nameWidth + "s | %-6s | %3d | %7.2f%n";

        // 10 + 3 + name + 3 + 6 + 3 + 3 + 3 + 7 = total width of one row
        String line = "-".repeat(10 + 3 + nameWidth + 3 + 6 + 3 + 3 + 3 + 7);

        System.out.println();
        System.out.println(line);
        System.out.printf(headerFormat, "STUDENT ID", "NAME", "COURSE", "AGE", "AVERAGE");
        System.out.println(line);
        for (Student s : list) {
            System.out.printf(rowFormat, s.getStudentId(), shorten(s.getDisplayName(), nameWidth),
                    s.getCourse(), s.getAge(), s.getAverage());
        }
        System.out.println(line);
    }

    // ===================== FEATURES =====================

    private static void addStudent() {
        printCancelHint();

        // Collect and validate EVERYTHING first; only then create the record.
        // If the user types 'cancel' at any prompt, we jump out right here and nothing is saved.
        String name = readFullName("Enter Full Name: ");
        char middleInitial = readMiddleInitial("Enter Middle Initial (a letter, or [N/A] for none): ");
        String course = readCourse();
        int age = readIntInRange("Enter Age (18-80): ", 18, 80);
        double average = readDoubleInRange("Enter Average (0-100): ", 0, 100);

        // Show what is about to be saved and ask first. (The real ID is only
        // generated after the user says yes, so a "no" never uses up an ID number.)
        Student preview = new Student("(assigned when saved)", name, middleInitial, course, age, average);
        System.out.println();
        System.out.println("Please review the new student:");
        printStudent(preview);
        System.out.println();
        if (!readYesNo("Save this student? (y/n): ")) {
            System.out.println("Student not saved.");
            return;
        }

        String id = generateStudentId();
        Student newStudent = new Student(id, name, middleInitial, course, age, average);
        students.add(newStudent);
        saveStudents();

        System.out.println();
        System.out.println("Student added! Here is the new record:");
        printStudent(newStudent);
    }

    // Compares two students for sorting. sortKey is the menu number (see SORT_LABELS).
    // A negative result means "a goes first", positive means "b goes first".
    private static int compareStudents(Student a, Student b, int sortKey) {
        int result = 0;
        if (sortKey == 2) {
            result = a.getFullName().compareToIgnoreCase(b.getFullName());
        } else if (sortKey == 3) {
            result = Double.compare(b.getAverage(), a.getAverage());   // b first = high to low
        } else if (sortKey == 4) {
            result = Double.compare(a.getAverage(), b.getAverage());
        } else if (sortKey == 5) {
            result = Integer.compare(a.getAge(), b.getAge());
        }
        if (result != 0) {
            return result;
        }
        // Same value (or sorting by ID): order by ID. A shorter ID is a smaller number
        // (so 2026-999 comes before 2026-1000), then compare the text.
        String idA = a.getStudentId();
        String idB = b.getStudentId();
        if (idA.length() != idB.length()) {
            return Integer.compare(idA.length(), idB.length());
        }
        return idA.compareTo(idB);
    }

    // Sorts the list in place. (The "(a, b) -> ..." part is a lambda: a short way
    // of saying "to compare two students a and b, do this".)
    private static void sortStudents(ArrayList<Student> list, int sortKey) {
        list.sort((a, b) -> compareStudents(a, b, sortKey));
    }

    // Shows the students, with options underneath to change the sort order or filter by course.
    // Returns true if it showed that screen (it has its own "Back" option, so the main menu
    // should not ask for "Press Enter"), or false if there was nothing to show.
    private static boolean viewAllStudents() {
        if (students.isEmpty()) {
            System.out.println("No student records yet. Choose 1 to add one.");
            return false;
        }

        int sortKey = 1;            // 1 = by ID
        String courseFilter = "";   // "" = all courses
        boolean viewing = true;

        while (viewing) {
            clearScreen();

            // Work on a COPY, so the real list (and the file) keep their original order.
            ArrayList<Student> shown = new ArrayList<>();
            for (Student s : students) {
                if (courseFilter.isEmpty() || s.getCourse().equals(courseFilter)) {
                    shown.add(s);
                }
            }
            sortStudents(shown, sortKey);

            String courseText = courseFilter.isEmpty() ? "all" : courseFilter;
            System.out.println("Course: " + courseText + "   |   Sorted by: " + SORT_LABELS[sortKey - 1]);
            if (shown.isEmpty()) {
                System.out.println();
                System.out.println("No students in " + courseFilter + ".");
            } else {
                printStudentTable(shown);
                System.out.println("Showing " + shown.size() + " of " + students.size() + " student(s)");
            }

            System.out.println();
            System.out.println("Sort:   1=ID  2=Name A-Z  3=Average high-low  4=Average low-high  5=Age");
            System.out.println("Filter: 6=Pick a course  7=All courses        0=Back to main menu");

            try {
                int choice = readIntInRange("Choose (0-7): ", 0, 7);
                if (choice >= 1 && choice <= 5) {
                    sortKey = choice;
                } else if (choice == 6) {
                    try {
                        courseFilter = readCourse();
                    } catch (CancelException e) {
                        // cancelled while picking: keep the current filter
                    }
                } else if (choice == 7) {
                    courseFilter = "";
                } else {
                    viewing = false;   // 0 = back
                }
            } catch (CancelException e) {
                viewing = false;   // typing 'cancel' here also means "back"
            }
        }
        return true;
    }

    // One screen of numbers about the whole class.
    private static void showStatistics() {
        if (students.isEmpty()) {
            System.out.println("No student records yet. Choose 1 to add one.");
            return;
        }

        double totalAverage = 0;
        int totalAge = 0;
        double highest = students.get(0).getAverage();
        double lowest = students.get(0).getAverage();
        for (Student s : students) {
            totalAverage += s.getAverage();
            totalAge += s.getAge();
            if (s.getAverage() > highest) {
                highest = s.getAverage();
            }
            if (s.getAverage() < lowest) {
                lowest = s.getAverage();
            }
        }
        int count = students.size();

        System.out.println("===== CLASS STATISTICS =====");
        System.out.println("Total students:  " + count);
        System.out.printf("Class average:   %.2f%n", totalAverage / count);
        System.out.printf("Average age:     %.1f%n", (double) totalAge / count);

        // More than one student can share the highest (or lowest) average, so list them all.
        System.out.println();
        System.out.printf("Highest average: %.2f%n", highest);
        for (Student s : students) {
            if (Double.compare(s.getAverage(), highest) == 0) {
                System.out.println("  - " + s.getDisplayName() + " (" + s.getStudentId() + ")");
            }
        }
        System.out.printf("Lowest average:  %.2f%n", lowest);
        for (Student s : students) {
            if (Double.compare(s.getAverage(), lowest) == 0) {
                System.out.println("  - " + s.getDisplayName() + " (" + s.getStudentId() + ")");
            }
        }

        System.out.println();
        System.out.println("By course:");
        System.out.printf("  %-7s %8s %9s%n", "Course", "Students", "Average");
        for (String course : COURSES) {
            int inCourse = 0;
            double courseTotal = 0;
            for (Student s : students) {
                if (s.getCourse().equals(course)) {
                    inCourse++;
                    courseTotal += s.getAverage();
                }
            }
            if (inCourse == 0) {
                System.out.printf("  %-7s %8d %9s%n", course, 0, "-");
            } else {
                System.out.printf("  %-7s %8d %9.2f%n", course, inCourse, courseTotal / inCourse);
            }
        }
    }

    // In a CSV file, a value containing a comma or a quote must be wrapped in quotes,
    // and any quote inside it is doubled. Example:  Smith, John "Jr"  ->  "Smith, John ""Jr"""
    private static String csvField(String text) {
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    // Writes every student to students.csv, which Excel can open. Always all students, in ID order.
    private static void exportToCsv() {
        if (students.isEmpty()) {
            System.out.println("No student records to export.");
            return;
        }

        Path csvPath = Paths.get(CSV_FILE);
        if (Files.exists(csvPath) && !readYesNo(CSV_FILE + " already exists. Overwrite it? (y/n): ")) {
            System.out.println("Export cancelled. The existing file was not changed.");
            return;
        }

        List<String> lines = new ArrayList<>();
        // The invisible \uFEFF at the very start tells Excel the file is UTF-8,
        // so names with accents (like Pena with an n-tilde) display correctly.
        lines.add("\uFEFFStudent ID,Full Name,Middle Initial,Course,Age,Average");
        for (Student s : students) {
            String initial = s.hasMiddleInitial() ? String.valueOf(s.getMiddleInitial()) : "";
            lines.add(csvField(s.getStudentId()) + "," + csvField(s.getFullName()) + "," + initial + ","
                    + s.getCourse() + "," + s.getAge() + "," + s.getAverage());
        }

        try {
            Files.write(csvPath, lines, StandardCharsets.UTF_8);
            System.out.println("Exported " + students.size() + " student(s) to:");
            System.out.println(csvPath.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("Could not write " + CSV_FILE + ": " + e.getMessage());
            System.out.println("If the file is open in Excel, close it and try again.");
        }
    }

    private static void searchById() {
        printCancelHint();
        String id = readNonEmptyText("Enter Student ID to search: ");
        Student found = findStudentById(id);

        if (found == null) {
            System.out.println("No student found with ID " + id + ".");
        } else {
            System.out.println("Student found:");
            printStudent(found);
        }
    }

    private static void searchByName() {
        printCancelHint();
        String query = readNonEmptyText("Enter name (or part of a name) to search: ");
        ArrayList<Student> matches = findStudentsByName(query);

        if (matches.isEmpty()) {
            System.out.println("No students found matching \"" + query + "\".");
        } else {
            System.out.println(matches.size() + " match(es) found:");
            printStudentTable(matches);
        }
    }

    private static void updateStudent() {
        printCancelHint();
        Student found = askForExistingStudent("update");
        if (found == null) {
            return;   // message already printed; back to the menu
        }

        // Remember the values from before, so we can show before -> after at the end.
        String oldName = found.getFullName();
        char oldInitial = found.getMiddleInitial();
        String oldCourse = found.getCourse();
        int oldAge = found.getAge();
        double oldAverage = found.getAverage();

        // Let the user fix one field at a time until they choose 0.
        // Each new value goes through the same checks as Add, and is only saved
        // after it passes, so a bad value can never overwrite a good one.
        String status = "";   // a message to show at the top of the next screen
        boolean editing = true;
        while (editing) {
            clearScreen();
            if (!status.isEmpty()) {
                System.out.println(status);
                System.out.println();
                status = "";
            }
            System.out.println("Current record (ID " + found.getStudentId() + " cannot be changed):");
            printStudent(found);
            System.out.println();
            System.out.println("What do you want to update?");
            System.out.println("  1. Full Name");
            System.out.println("  2. Middle Initial");
            System.out.println("  3. Course/Track");
            System.out.println("  4. Age");
            System.out.println("  5. Average");
            System.out.println("  0. Finish updating (or type '" + CANCEL_WORD + "')");

            // Typing 'cancel' here just means "I'm done updating".
            int choice;
            try {
                choice = readIntInRange("Choose (0-5): ", 0, 5);
            } catch (CancelException e) {
                choice = 0;
            }

            // Typing 'cancel' while entering a value drops only that one edit
            // and returns to this field menu. Fields saved earlier stay saved.
            try {
                if (choice == 1) {
                    found.setFullName(readFullName("New Full Name: "));
                    status = "Full name updated.";
                } else if (choice == 2) {
                    found.setMiddleInitial(readMiddleInitial("New Middle Initial (a letter, or [N/A] for none): "));
                    status = "Middle initial updated.";
                } else if (choice == 3) {
                    found.setCourse(readCourse());
                    status = "Course updated.";
                } else if (choice == 4) {
                    found.setAge(readIntInRange("New Age (18-80): ", 18, 80));
                    status = "Age updated.";
                } else if (choice == 5) {
                    found.setAverage(readDoubleInRange("New Average (0-100): ", 0, 100));
                    status = "Average updated.";
                } else {
                    editing = false;
                }
                if (choice != 0) {
                    saveStudents();   // save each field as soon as it has been changed
                }
            } catch (CancelException e) {
                status = "Cancelled. That field was not changed.";
            }
        }

        // Show only the fields that really ended up different from before.
        clearScreen();
        System.out.println("Finished updating " + found.getStudentId() + ". Changes (before -> after):");
        boolean anyChange = false;
        if (!found.getFullName().equals(oldName)) {
            System.out.println("  Full Name:      " + oldName + " -> " + found.getFullName());
            anyChange = true;
        }
        if (found.getMiddleInitial() != oldInitial) {
            System.out.println("  Middle Initial: " + initialText(oldInitial) + " -> " + initialText(found.getMiddleInitial()));
            anyChange = true;
        }
        if (!found.getCourse().equals(oldCourse)) {
            System.out.println("  Course:         " + oldCourse + " -> " + found.getCourse());
            anyChange = true;
        }
        if (found.getAge() != oldAge) {
            System.out.println("  Age:            " + oldAge + " -> " + found.getAge());
            anyChange = true;
        }
        if (Double.compare(found.getAverage(), oldAverage) != 0) {
            System.out.printf("  Average:        %.2f -> %.2f%n", oldAverage, found.getAverage());
            anyChange = true;
        }
        if (!anyChange) {
            System.out.println("  No changes were made.");
        }
    }

    private static void deleteStudent() {
        printCancelHint();
        Student found = askForExistingStudent("delete");
        if (found == null) {
            return;
        }

        System.out.println("You are about to delete:");
        printStudent(found);

        // Keeps asking until y or n. Typing 'cancel' also backs out.
        if (readYesNo("Are you sure? (y/n): ")) {
            students.remove(found);
            saveStudents();
            System.out.println("Record deleted.");
        } else {
            System.out.println("Delete cancelled.");
        }
    }
}