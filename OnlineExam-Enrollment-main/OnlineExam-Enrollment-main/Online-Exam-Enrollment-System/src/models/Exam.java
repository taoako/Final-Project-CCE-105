package models;

import java.sql.Date;

public class Exam {
    private int id;
    private String name;
    private Date examDate;
    private String duration;

    public Exam(int id, String name, Date examDate, String duration) {
        this.id = id;
        this.name = name;
        this.examDate = examDate;
        this.duration = duration;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Date getExamDate() {
        return examDate;
    }

    public String getDuration() {
        return duration;
    }
}
