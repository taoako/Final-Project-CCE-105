package dao;

import models.Course;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseDAO {

    public List<Course> getAllCourses() {
        List<Course> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM courses";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(new Course(rs.getInt("id"), rs.getString("name")));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Course getCourseById(int courseId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM courses WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Course(rs.getInt("id"), rs.getString("name"));
            }

        } catch (SQLException e) {
            System.err.println("Error fetching course by ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
