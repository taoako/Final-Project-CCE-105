package models;

import java.sql.Date;

public class Exam {
    private int id;
    private String name;
    private int courseId;
    private Date examDate;
    private String duration;

    // Constructor for creating new exam (without ID)
    public Exam(String name, int courseId, String duration) {
        this.name = name;
        this.courseId = courseId;
        this.duration = duration;
    }

    // Constructor for existing exam (with ID)
    public Exam(int id, String name, int courseId, String duration) {
        this.id = id;
        this.name = name;
        this.courseId = courseId;
        this.duration = duration;
    }

    // Constructor for existing exam without course_id (for backward compatibility)
    public Exam(int id, String name, String duration) {
        this.id = id;
        this.name = name;
        this.duration = duration;
    }

    // Original constructor for backward compatibility
    public Exam(int id, String name, Date examDate, String duration) {
        this.id = id;
        this.name = name;
        this.examDate = examDate;
        this.duration = duration;
    }

    // Constructor with all fields
    public Exam(int id, String name, int courseId, Date examDate, String duration) {
        this.id = id;
        this.name = name;
        this.courseId = courseId;
        this.examDate = examDate;
        this.duration = duration;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getExamName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public Date getExamDate() {
        return examDate;
    }

    public void setExamDate(Date examDate) {
        this.examDate = examDate;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        return name;
    }
}
