# Java Student Information System

A console-based Student Information System written in plain Java. It runs in Command Prompt, needs no libraries, no database, and no internet, and can be written and compiled with just Notepad and `javac`.

Records are saved to a local text file, so they are still there the next time you start the program.

## Features

- **Add, view, update, and delete** student records
- **Search** by student ID (exact match) or by name (partial, case-insensitive)
- **Automatic IDs** in the form `2026-001`, `2026-002`, ... that are never reused, even after a delete and a restart
- **Input validation** on every prompt, with clear error messages and no crashes on bad input
- **Cancel anywhere**: type `cancel` at any prompt to back out without saving
- **Sort and filter** the student table by ID, name, average, age, or course
- **Class statistics**: totals, class average, highest and lowest, and a per-course breakdown
- **Export to CSV** for opening in Excel
- **Login screen** with a changeable password (stored as a salted hash)
- **Saved to a file** after every change, with a backup and safe handling of damaged data

## Requirements

- Java Development Kit (JDK) **11 or newer** (tested on JDK 21 and 25)

Check your setup in Command Prompt. Both commands must print a version:

```
java -version
javac -version
```

If `javac` is "not recognized", you have a JRE but not a JDK. Install a JDK (for example [Eclipse Temurin](https://adoptium.net)) and tick **"Add to PATH"** in the installer.

## Quick start

```
git clone https://github.com/jstnpbl/java-student-information-system.git
cd java-student-information-system
javac Student.java StudentInfoSystem.java
java StudentInfoSystem
```

Always run the program from the project folder. The data files are created in whichever folder you run it from.

**Default login:** username `admin`, password `password123` (see [Login and security](#login-and-security)).

On the very first run, the program starts with 8 sample students so there is something to look at. Delete `students.txt` to bring the samples back, or see [Settings you can change](#settings-you-can-change).

## Using the program

```
===== STUDENT INFORMATION SYSTEM =====
1. Add Student
2. View All Students (sort / filter)
3. Search Student by ID
4. Search Student by Name
5. Update Student Record
6. Delete Student Record
7. Class Statistics
8. Export to CSV (for Excel)
9. Change Password
0. Exit
```

Viewing students (option 2) shows an aligned table. Sort and filter options appear underneath it:

```
Course: all   |   Sorted by: ID

----------------------------------------------------------------
STUDENT ID | NAME                       | COURSE | AGE | AVERAGE
----------------------------------------------------------------
2026-001   | Juan Dela Cruz             | ICT    |  18 |   87.50
2026-002   | Maria Santos L.            | STEM   |  19 |   92.25
2026-003   | Pedro Reyes A.             | HUMSS  |  20 |   78.00
2026-004   | Ana Marie Garcia           | ABM    |  21 |   85.75
2026-005   | Jose Mercado P.            | GAS    |  80 |  100.00
2026-006   | Juana Lopez C.             | ICT    |  25 |    0.00
2026-007   | Mark Anthony Villanueva B. | STEM   |  30 |   69.40
2026-008   | Carla Bautista             | HUMSS  |  22 |   95.00
----------------------------------------------------------------
Showing 8 of 8 student(s)

Sort:   1=ID  2=Name A-Z  3=Average high-low  4=Average low-high  5=Age
Filter: 6=Pick a course  7=All courses        0=Back to main menu
```

Class statistics (option 7):

```
===== CLASS STATISTICS =====
Total students:  8
Class average:   75.99
Average age:     29.4

Highest average: 100.00
  - Jose Mercado P. (2026-005)
Lowest average:  0.00
  - Juana Lopez C. (2026-006)

By course:
  Course  Students   Average
  ICT            2     43.75
  HUMSS          2     86.50
  STEM           2     80.83
  ABM            1     85.75
  GAS            1    100.00
```

## Student fields and validation rules

| Field | Type | Rule |
|---|---|---|
| Student ID | `String` | Generated automatically (`2026-001`, ...). Never changes after creation. |
| Full name | `String` | Not blank. The `\|` character is not allowed (it separates fields in the data file). |
| Middle initial | `char` | One letter, or `[N/A]` for none. `N/A` and `n/a` are accepted too. |
| Course/track | `String` | Choose from a menu: ICT, HUMSS, STEM, ABM, GAS. |
| Age | `int` | Whole number from 18 to 80. |
| Average | `double` | Number from 0 to 100. Stored exactly as typed, shown with 2 decimals. |

"No middle initial" is stored internally as `'.'`, because a `char` cannot be empty. It is displayed without any extra punctuation.

## Files the program creates

These are created in the folder you run from, and are excluded from Git by `.gitignore`.

| File | What it is |
|---|---|
| `students.txt` | Your saved records, one student per line, fields separated by `\|`. The first line (`NEXT_ID=...`) remembers the next ID number. |
| `students.txt.bak` | A copy of the previous version, made before each save. |
| `students.txt.corrupt` | Only appears if a damaged `students.txt` was found. A copy of the original, kept before the bad lines are dropped. |
| `login.txt` | Only appears after you change the password. Holds a salt and a SHA-256 hash, never the password itself. |
| `students.csv` | Written by "Export to CSV". Opens in Excel. |

## Login and security

The login is a **simple login screen, not real security**. Be clear about what it does and does not do:

- The default password (`password123`) is written in the source code, and this repository is public.
- `students.txt` is plain text that anyone with access to the folder can read.
- A changed password is stored as a salted hash, which keeps it unreadable in `login.txt`. But anyone who can edit the folder can delete `login.txt` to reset it to the default.

Use it as a practice exercise in loops and conditions, not as protection for real data.

## Settings you can change

Near the top of `StudentInfoSystem.java`:

| Setting | Default | What it does |
|---|---|---|
| `LOAD_SAMPLE_DATA` | `true` | Create `students.txt` with 8 sample students when no file exists yet. |
| `REQUIRE_LOGIN` | `true` | Set to `false` to skip the login while testing. |
| `HIDE_PASSWORD` | `false` | Set to `true` to hide the password while typing (nothing appears on screen, not even asterisks). |
| `ADMIN_USER` / `ADMIN_PASS` | `admin` / `password123` | The login username and the default password. |
| `MAX_NAME_WIDTH` | `40` | The widest the name column in the table can get. |

After changing any setting, recompile with `javac Student.java StudentInfoSystem.java`.

## Project structure

```
Student.java             One student record (data only, no input or output)
StudentInfoSystem.java   The menu, all features, saving and loading
```

## How it works

- **Data structure:** an `ArrayList<Student>` holds the records in memory.
- **Saving:** after every add, update, and delete, the whole list is written to `students.txt`. The write goes to a temporary file first and is then renamed, so a crash in the middle of saving cannot leave a half-written file.
- **Loading:** lines that are not valid student records are skipped with a warning, and a copy of the original file is kept.
- **IDs:** a counter that only ever goes up, saved in the file. A deleted student's ID is never handed out again.
- **Input:** every prompt is a loop that keeps asking until the value is valid. Numbers are read as text and converted, using `try-catch` only where parsing can genuinely fail.
- **Cancel:** typing `cancel` throws a small custom exception that jumps out of whatever the user was doing, back to the menu, with nothing saved.

## Known limitations

- Only one copy of the program should run at a time. If two run together, the last one to save wins.
- Names are sorted as typed (first name first), since a name is stored as one string.
- The screen is cleared with `cls` on Windows and a standard terminal code elsewhere.
- In the CSV export, Excel may display IDs like `2026-001` as dates. If so, import through **Data → From Text/CSV** and set that column to **Text**. Also, a name starting with `=`, `+`, `-`, or `@` would be treated as a formula by Excel.
- Typing accented characters into some Command Prompt windows may need `chcp 65001` first. The data files themselves are UTF-8.

## Ideas for later

- A graphical interface (Swing) on top of the same `Student` class and data file
- Enrollment status, fees, and receipts
