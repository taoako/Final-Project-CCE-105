-- SQL script to set up the admin system tables
-- Run this script in your MySQL database

-- Create admins table if it doesn't exist
CREATE TABLE IF NOT EXISTS admins (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('admin', 'super_admin') DEFAULT 'admin',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create rooms table if it doesn't exist
CREATE TABLE IF NOT EXISTS rooms (
    id INT AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL,
    capacity INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default admin account (if not exists)
INSERT IGNORE INTO admins (username, password, role) VALUES ('admin', 'admin123', 'super_admin');

-- Insert some sample rooms (if not exists)
INSERT IGNORE INTO rooms (room_name, capacity) VALUES 
('Computer Lab 1', 30),
('Computer Lab 2', 25),
('Lecture Hall A', 100),
('Lecture Hall B', 80),
('Conference Room 1', 20),
('Conference Room 2', 15);

-- Update the exams table structure if needed (add course_id if missing)
-- Note: This is a safe operation that only adds the column if it doesn't exist
SET @sql = CONCAT('ALTER TABLE exams ADD COLUMN IF NOT EXISTS course_id INT');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create student_exams table if it doesn't exist (for enrollment tracking)
CREATE TABLE IF NOT EXISTS student_exams (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    exam_id INT NOT NULL,
    status ENUM('Pending', 'Approved', 'Completed', 'Cancelled') DEFAULT 'Pending',
    score INT DEFAULT NULL,
    enrollment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    UNIQUE KEY unique_enrollment (student_id, exam_id)
);

-- Create exam_schedules table if it doesn't exist
CREATE TABLE IF NOT EXISTS exam_schedules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    exam_id INT NOT NULL,
    room_id INT NOT NULL,
    scheduled_date DATE NOT NULL,
    scheduled_time TIME NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 120,
    max_students INT NOT NULL DEFAULT 30,
    status ENUM('Scheduled', 'In Progress', 'Completed', 'Cancelled') DEFAULT 'Scheduled',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_exam_course ON exams(course_id);
CREATE INDEX IF NOT EXISTS idx_student_exams_student ON student_exams(student_id);
CREATE INDEX IF NOT EXISTS idx_student_exams_exam ON student_exams(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_schedules_exam ON exam_schedules(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_schedules_room ON exam_schedules(room_id);
CREATE INDEX IF NOT EXISTS idx_exam_schedules_date ON exam_schedules(scheduled_date);

-- Show confirmation message
SELECT 'Admin system tables created successfully!' as Message;