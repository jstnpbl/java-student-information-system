// Student.java
// Represents ONE student record. Just data, no menu or input code here.
public class Student {

    // The middle initial is stored as a char, and a char can't be "empty".
    // So we use '.' as a special marker meaning "this student has no middle initial".
    public static final char NO_MIDDLE_INITIAL = '.';

    private String studentId;      // permanent identifier, never changed after creation
    private String fullName;
    private char middleInitial;    // a capital letter, or NO_MIDDLE_INITIAL
    private String course;         // ICT, HUMSS, STEM, ABM, or GAS
    private int age;
    private double average;        // stored exactly as entered, never rounded

    public Student(String studentId, String fullName, char middleInitial,
                   String course, int age, double average) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.middleInitial = middleInitial;
        this.course = course;
        this.age = age;
        this.average = average;
    }

    // Getters
    public String getStudentId()   { return studentId; }
    public String getFullName()    { return fullName; }
    public char getMiddleInitial() { return middleInitial; }
    public String getCourse()      { return course; }
    public int getAge()            { return age; }
    public double getAverage()     { return average; }

    // Setters (no setter for ID on purpose: the ID is permanent)
    public void setFullName(String fullName)        { this.fullName = fullName; }
    public void setMiddleInitial(char middleInitial) { this.middleInitial = middleInitial; }
    public void setCourse(String course)            { this.course = course; }
    public void setAge(int age)                     { this.age = age; }
    public void setAverage(double average)          { this.average = average; }

    public boolean hasMiddleInitial() {
        return middleInitial != NO_MIDDLE_INITIAL;
    }

    // The name as shown on screen.
    // With an initial:    "Juan Dela Cruz L."
    // Without an initial: "Juan Dela Cruz"  (no extra dot or space)
    // Want a different layout? This is the only method you need to change.
    public String getDisplayName() {
        if (hasMiddleInitial()) {
            return fullName + " " + middleInitial + ".";
        }
        return fullName;
    }
}