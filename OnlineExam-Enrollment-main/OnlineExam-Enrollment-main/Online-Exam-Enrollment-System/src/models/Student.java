package models;

public class Student {
    private int id;
    private String name;
    private String email;
    private String password;
    private int courseId;

    public Student(int id, String name, String email, String password, int courseId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.courseId = courseId;
    }

    public Student(String name, String email, String password, int courseId) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.courseId = courseId;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public int getCourseId() {
        return courseId;
    }
}
