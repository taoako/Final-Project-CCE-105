package models;

public class Student {
    private int id;
    private String name;
    private String email;
    private String password;
    private int courseId;
    private double balance;
    private String courseName;

    public Student(int id, String name, String email, String password, int courseId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.courseId = courseId;
        this.balance = 0.0;
    }

    public Student(String name, String email, String password, int courseId) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.courseId = courseId;
        this.balance = 0.0;
    }

    // Extended constructor for admin operations
    public Student(int id, String name, String email, String password, int courseId, double balance) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.courseId = courseId;
        this.balance = balance;
    }

    // Constructor with course name for display
    public Student(int id, String name, String email, String courseName, double balance) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.courseName = courseName;
        this.balance = balance;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    // Convenience method for course display
    public String getCourse() {
        return courseName != null ? courseName : "Course ID: " + courseId;
    }

    public void setCourse(String course) {
        this.courseName = course;
    }
}
